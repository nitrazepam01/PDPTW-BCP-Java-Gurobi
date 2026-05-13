package org.pdptw.master;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.cli.RootColumnGenerationRunner;
import org.pdptw.cuts.DtiPtiRepair;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.cuts.RobustCut;
import org.pdptw.cuts.RobustCutRow;
import org.pdptw.pricing.PricingContext;
import org.pdptw.pricing.PricingResult;
import org.pdptw.pricing.PricingSolver;
import org.pdptw.pricing.ReducedCostMatrices;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GurobiRmpCutRowsTest {
    private static final double TOLERANCE = 1.0e-7;

    private GurobiRmpCutRowsTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("GurobiRmpCutRowsTest OK");
    }

    public static void run() throws Exception {
        assertRobustCutRowDualFeedsPricingRepair();
        assertCutRowsApplyToFutureColumns();
        assertRootCgPassesRobustPricingContext();
    }

    private static void assertRobustCutRowDualFeedsPricingRepair() throws Exception {
        Instance instance = MasterTestSupport.tinyTwoRequestInstance(10.0, 1);
        Route route = MasterTestSupport.routeServingBothRequests();
        RobustCutRow row = robustRow("robust_route_cap", 1.0);

        try (GurobiRmp rmp = new GurobiRmp(instance, 1000.0)) {
            MasterTestSupport.assertTrue(rmp.addRoute(route), "real route is added before cut row");
            rmp.addCutRow(row);
            MasterTestSupport.assertEquals(1, rmp.cutRows().size(), "one cut row is active");

            GurobiRmp.SolveResult solve = rmp.solveLp();
            MasterTestSupport.assertTrue(solve.isOptimal(), "cut-row LP solves");
            MasterTestSupport.assertEquals(1005.0, solve.objectiveValue(), TOLERANCE,
                    "binding robust cut row forces half artificial usage");

            List<GurobiRmp.CutDual> cutDuals = rmp.cutDuals();
            MasterTestSupport.assertEquals(1, cutDuals.size(), "one cut dual is available");
            GurobiRmp.CutDual cutDual = cutDuals.get(0);
            MasterTestSupport.assertTrue(cutDual.rawPi() < -TOLERANCE,
                    "LESS_EQUAL robust cut raw Pi should be negative on the toy LP");
            MasterTestSupport.assertEquals(-cutDual.rawPi(), cutDual.pricingDual(), TOLERANCE,
                    "pricing dual uses centralized -rawPi convention");

            List<RobustCut> robustCuts = rmp.robustCutsFromDuals();
            MasterTestSupport.assertEquals(1, robustCuts.size(), "one pricing robust cut is produced");
            MasterTestSupport.assertEquals(cutDual.pricingDual(),
                    robustCuts.get(0).arcPrice(instance, 1, 2),
                    TOLERANCE,
                    "pricing robust cut receives converted dual on arc 1->2");

            DualSolution duals = rmp.dualSolution();
            MasterTestSupport.assertEquals(1, duals.robustCuts().size(),
                    "LP dual solution carries robust pricing cuts");
            MasterTestSupport.assertEquals(0.0,
                    duals.directReducedCostWithRobustCuts(route, instance),
                    TOLERANCE,
                    "active route has zero reduced cost after robust cut dual contribution");

            ReducedCostMatrices matrices = ReducedCostMatrices.fromDualSolution(instance, duals);
            double[][] forwardWithCuts = DtiPtiRepair.forwardMatrixWithRobustCuts(matrices, robustCuts);
            MasterTestSupport.assertEquals(
                    matrices.forwardArcReducedCost(1, 2) + cutDual.pricingDual(),
                    forwardWithCuts[1][2],
                    TOLERANCE,
                    "forward pricing matrix receives robust arc contribution");

            DtiPtiRepair.RepairResult forwardRepair =
                    DtiPtiRepair.repairForwardDti(instance, forwardWithCuts);
            DtiPtiRepair.RepairResult backwardRepair =
                    DtiPtiRepair.repairBackwardPti(
                            instance,
                            DtiPtiRepair.backwardMatrixWithRobustCuts(matrices, robustCuts));
            MasterTestSupport.assertTrue(DtiPtiRepair.satisfiesForwardDti(instance, forwardRepair.matrix()),
                    "forward robust matrix is DTI after repair");
            MasterTestSupport.assertTrue(DtiPtiRepair.satisfiesBackwardPti(instance, backwardRepair.matrix()),
                    "backward robust matrix is PTI after repair");

            List<Integer> routeIds = route.vertexIds();
            MasterTestSupport.assertEquals(
                    DtiPtiRepair.arcReducedCostSum(forwardWithCuts, routeIds),
                    forwardRepair.arcReducedCostSum(routeIds),
                    TOLERANCE,
                    "forward repair preserves complete route reduced cost");
        }
    }

    private static void assertCutRowsApplyToFutureColumns() throws Exception {
        Instance instance = MasterTestSupport.tinyTwoRequestInstance(10.0, 1);
        Route route = MasterTestSupport.routeServingBothRequests();
        RobustCutRow row = robustRow("future_robust_route_cap", 1.0);

        try (GurobiRmp rmp = new GurobiRmp(instance, 1000.0)) {
            rmp.addCutRow(row);
            MasterTestSupport.assertTrue(rmp.addRoute(route), "real route is added after cut row");
            GurobiRmp.SolveResult solve = rmp.solveLp();
            MasterTestSupport.assertTrue(solve.isOptimal(), "future-column cut-row LP solves");
            MasterTestSupport.assertEquals(1005.0, solve.objectiveValue(), TOLERANCE,
                    "future route column receives the active cut row coefficient");
        }
    }

    private static void assertRootCgPassesRobustPricingContext() throws Exception {
        Instance instance = MasterTestSupport.tinyTwoRequestInstance(10.0, 1);
        RobustCutRow row = robustRow("root_cg_robust_context", 1.0);
        RobustContextProbe probe = new RobustContextProbe();

        new RootColumnGenerationRunner(1000.0, TOLERANCE, 1)
                .run(instance, "robust-context-probe", probe, List.of(row));

        MasterTestSupport.assertTrue(probe.called(), "root CG invokes pricing probe");
        MasterTestSupport.assertTrue(probe.sawRobustContext(),
                "root CG passes robust cut duals through PricingContext");
        MasterTestSupport.assertTrue(probe.forwardDtiRepaired(),
                "robust PricingContext exposes repaired DTI matrix before pricing");
        MasterTestSupport.assertTrue(probe.backwardPtiRepaired(),
                "robust PricingContext exposes repaired PTI matrix before pricing");
    }

    private static RobustCutRow robustRow(String name, double rhs) {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 2), 1.0);
        coefficients.put(new RobustCut.Arc(3, 4), 1.0);
        return RobustCutRow.ofArcCoefficients(
                name,
                MasterCutRow.Sense.LESS_EQUAL,
                rhs,
                coefficients);
    }

    private static final class RobustContextProbe implements PricingSolver {
        private boolean called;
        private boolean sawRobustContext;
        private boolean forwardDtiRepaired;
        private boolean backwardPtiRepaired;

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            throw new AssertionError("root CG should call price(PricingContext) when cut rows are active");
        }

        @Override
        public PricingResult price(PricingContext context) {
            called = true;
            sawRobustContext = context.hasRobustCuts();
            forwardDtiRepaired = context.satisfiesForwardDti();
            backwardPtiRepaired = context.satisfiesBackwardPti();
            return PricingResult.noNegativeColumn(0.0);
        }

        boolean called() {
            return called;
        }

        boolean sawRobustContext() {
            return sawRobustContext;
        }

        boolean forwardDtiRepaired() {
            return forwardDtiRepaired;
        }

        boolean backwardPtiRepaired() {
            return backwardPtiRepaired;
        }
    }
}
