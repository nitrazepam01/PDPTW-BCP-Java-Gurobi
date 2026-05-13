package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.Vertex;
import org.pdptw.cli.RootCgResult;
import org.pdptw.cli.RootColumnGenerationRunner;
import org.pdptw.cli.TraceCsv;
import org.pdptw.master.DualSolution;
import org.pdptw.master.GurobiRmp;
import org.pdptw.pricing.BackwardLabel;
import org.pdptw.pricing.BackwardLabeler;
import org.pdptw.pricing.BidirectionalMerger;
import org.pdptw.pricing.BidirectionalDynamicPricingSolver;
import org.pdptw.pricing.BidirectionalStaticPricingSolver;
import org.pdptw.pricing.ForwardLabel;
import org.pdptw.pricing.ForwardLabeler;
import org.pdptw.pricing.PricingContext;
import org.pdptw.pricing.PricingResult;
import org.pdptw.pricing.PricingSolver;
import org.pdptw.pricing.ReducedCostMatrices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SubsetRowPricingIntegrationTest {
    private static final double TOLERANCE = 1.0e-7;

    private SubsetRowPricingIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("SubsetRowPricingIntegrationTest OK");
    }

    public static void run() throws Exception {
        assertSubsetRowCutRowDualFeedsPricingContext();
        assertRootCgPassesSubsetRowPricingContext();
        assertForwardBackwardLabelsCarrySubsetRowPricing();
        assertSubsetRowMergeMatchesDirectReducedCost();
        assertMixedRobustAndSubsetRowMergeMatchesDirectReducedCost();
        assertDynamicMatchesStaticWithActiveCutContexts();
        assertNoActiveSubsetRowCutsLeavePricingUnchanged();
    }

    private static void assertRootCgPassesSubsetRowPricingContext() throws Exception {
        Instance instance = tinyThreeRequestInstance();
        SubsetRowCutRow row = SubsetRowCutRow.of(baseCut(0.0));
        SubsetRowContextProbe probe = new SubsetRowContextProbe();

        RootCgResult result = new RootColumnGenerationRunner(1000.0, TOLERANCE, 1)
                .run(instance, "sr-context-probe", probe, List.of(row));

        assertTrue(probe.called(), "root CG invokes SR pricing probe");
        assertTrue(probe.sawSubsetRowContext(),
                "root CG passes SR cut duals through PricingContext");
        assertEquals(1, result.finalCutsActive(), "root CG reports one active SR cut");
        assertEquals(1, result.iterations().get(0).cutsActive(),
                "root CG iteration reports one active SR cut");
        assertTraceCutsActive(TraceCsv.rootCg(instance.name(), result), result.iterationCount(), 1);
    }

    private static void assertSubsetRowCutRowDualFeedsPricingContext() throws Exception {
        Instance instance = tinyThreeRequestInstance();
        SubsetRowCutRow row = SubsetRowCutRow.of(baseCut(0.0));
        Route route = Route.of(0, 1, 2, 4, 5, 7);

        assertClose(1.0,
                row.coefficient(instance, org.pdptw.master.RouteColumn.fromRoute("r", route, instance)),
                TOLERANCE,
                "SR row coefficient uses floor(served in U / 2)");

        try (GurobiRmp rmp = new GurobiRmp(instance, 1000.0)) {
            assertTrue(rmp.addRoute(route), "real route is added before SR cut row");
            rmp.addCutRow(row);
            GurobiRmp.SolveResult solve = rmp.solveLp();
            assertTrue(solve.isOptimal(), "SR cut-row LP solves");

            List<SubsetRowCut> pricingCuts = rmp.subsetRowCutsFromDuals();
            assertEquals(1, pricingCuts.size(), "one SR pricing cut is produced");
            assertTrue(pricingCuts.get(0).sigma() <= TOLERANCE,
                    "LESS_EQUAL SR row exposes non-positive raw Pi as sigma");

            DualSolution duals = rmp.dualSolution();
            assertEquals(1, duals.subsetRowCuts().size(),
                    "LP dual solution carries SR pricing cuts");
            PricingContext context = PricingContext.withCuts(
                    ReducedCostMatrices.fromDualSolution(instance, duals),
                    rmp.robustCutsFromDuals(),
                    pricingCuts);
            assertTrue(context.hasSubsetRowCuts(), "PricingContext sees active SR cuts");
        }
    }

    private static void assertForwardBackwardLabelsCarrySubsetRowPricing() {
        Instance instance = tinyThreeRequestInstance();
        PricingContext context = activeSrContext(instance, -4.0);
        List<Integer> route = List.of(0, 1, 2, 3, 4, 5, 6, 7);

        ForwardLabel forward = findForwardLabel(context, route);
        BackwardLabel backward = findBackwardLabel(context, route);
        double direct = context.directReducedCost(route);

        assertClose(direct, forward.reducedCost(), TOLERANCE,
                "forward label RC equals direct SR-adjusted RC");
        assertClose(direct, backward.reducedCost(), TOLERANCE,
                "backward label RC equals direct SR-adjusted RC");
        assertEquals(3, forward.subsetRowRelevantVisitCount(0),
                "forward label counted relevant pickups");
        assertEquals(3, backward.subsetRowRelevantVisitCount(0),
                "backward label counted relevant deliveries");
    }

    private static void assertSubsetRowMergeMatchesDirectReducedCost() {
        Instance instance = tinyThreeRequestInstance();
        PricingContext context = activeSrContext(instance, -4.0);
        LabelIndex labels = buildLabelIndex(context);
        BidirectionalMerger merger = new BidirectionalMerger();
        List<List<Integer>> routes = List.of(
                List.of(0, 1, 4, 7),
                List.of(0, 1, 2, 4, 5, 7),
                List.of(0, 1, 2, 3, 4, 5, 6, 7));

        int checkedSplits = 0;
        for (List<Integer> route : routes) {
            for (int split = 0; split < route.size(); split++) {
                final int splitIndex = split;
                List<Integer> prefix = new ArrayList<Integer>(route.subList(0, split + 1));
                List<Integer> suffix = new ArrayList<Integer>(route.subList(split, route.size()));
                ForwardLabel forward = labels.forwardByRoute.get(prefix);
                BackwardLabel backward = labels.backwardByRoute.get(suffix);
                if (forward == null || backward == null) {
                    throw new AssertionError("missing SR labels route=" + route
                            + " split=" + split + " prefix=" + prefix + " suffix=" + suffix);
                }
                BidirectionalMerger.MergeResult merge = merger.merge(context, forward, backward)
                        .orElseThrow(() -> new AssertionError("SR split should be merge-compatible route="
                                + route + " split=" + splitIndex));
                assertClose(
                        context.directReducedCost(route),
                        merge.mergedReducedCost(),
                        TOLERANCE,
                        "SR merged RC equals direct route RC");
                checkedSplits++;
            }
        }
        assertTrue(checkedSplits > 0, "SR merge audit checked splits");
    }

    private static void assertMixedRobustAndSubsetRowMergeMatchesDirectReducedCost() {
        Instance instance = tinyThreeRequestInstance();
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        RobustCut robustCut = new SingleArcRobustCut("mixed-robust-arc", -2.0, 0, 1, 1.0);
        SubsetRowCut subsetRowCut = baseCut(-4.0);
        PricingContext context = PricingContext.withCuts(
                matrices,
                List.of(robustCut),
                List.of(subsetRowCut));
        List<Integer> route = List.of(0, 1, 2, 3, 4, 5, 6, 7);

        double expected = matrices.directReducedCost(route)
                + DtiPtiRepair.robustArcPriceSum(instance, List.of(robustCut), route)
                + SRPricingAdjuster.routePricingAdjustment(subsetRowCut, instance, route);
        assertClose(expected, context.directReducedCost(route), TOLERANCE,
                "mixed robust+SR direct RC composes both cut contributions");
        assertTrue(context.hasRobustCuts(), "mixed context keeps robust cuts active");
        assertTrue(context.hasSubsetRowCuts(), "mixed context keeps SR cuts active");
        assertTrue(context.satisfiesForwardDti(), "mixed robust+SR context repairs forward DTI");
        assertTrue(context.satisfiesBackwardPti(), "mixed robust+SR context repairs backward PTI");

        ForwardLabel forward = findForwardLabel(context, route);
        BackwardLabel backward = findBackwardLabel(context, route);
        assertClose(context.directReducedCost(route), forward.reducedCost(), TOLERANCE,
                "mixed robust+SR forward label equals direct RC");
        assertClose(context.directReducedCost(route), backward.reducedCost(), TOLERANCE,
                "mixed robust+SR backward label equals direct RC");

        LabelIndex labels = buildLabelIndex(context);
        BidirectionalMerger merger = new BidirectionalMerger();
        int checkedSplits = 0;
        for (int split = 0; split < route.size(); split++) {
            final int splitIndex = split;
            List<Integer> prefix = new ArrayList<Integer>(route.subList(0, split + 1));
            List<Integer> suffix = new ArrayList<Integer>(route.subList(split, route.size()));
            ForwardLabel splitForward = labels.forwardByRoute.get(prefix);
            BackwardLabel splitBackward = labels.backwardByRoute.get(suffix);
            if (splitForward == null || splitBackward == null) {
                throw new AssertionError("missing mixed robust+SR labels split=" + split
                        + " prefix=" + prefix + " suffix=" + suffix);
            }
            BidirectionalMerger.MergeResult merge = merger.merge(context, splitForward, splitBackward)
                    .orElseThrow(() -> new AssertionError("mixed robust+SR split should merge split=" + splitIndex));
            assertClose(
                    context.directReducedCost(route),
                    merge.mergedReducedCost(),
                    TOLERANCE,
                    "mixed robust+SR merged RC equals direct route RC");
            checkedSplits++;
        }
        assertTrue(checkedSplits > 0, "mixed robust+SR merge audit checked splits");
    }

    private static void assertDynamicMatchesStaticWithActiveCutContexts() {
        Instance instance = tinyThreeRequestInstance();
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        RobustCut robustCut = new SingleArcRobustCut("dynamic-robust-arc", -2.0, 0, 1, 1.0);
        SubsetRowCut subsetRowCut = baseCut(-4.0);

        assertDynamicMatchesStatic(
                "robust-only",
                PricingContext.withRobustCuts(matrices, List.of(robustCut)));
        assertDynamicMatchesStatic(
                "subset-row-only",
                PricingContext.withSubsetRowCuts(matrices, List.of(subsetRowCut)));
        assertDynamicMatchesStatic(
                "mixed-robust-subset-row",
                PricingContext.withCuts(matrices, List.of(robustCut), List.of(subsetRowCut)));
    }

    private static void assertDynamicMatchesStatic(String label, PricingContext context) {
        PricingResult staticPricing = new BidirectionalStaticPricingSolver(TOLERANCE).price(context);
        BidirectionalDynamicPricingSolver dynamicSolver = new BidirectionalDynamicPricingSolver(TOLERANCE);
        BidirectionalDynamicPricingSolver.Result dynamicResult = dynamicSolver.solve(context);
        PricingResult dynamicPricing = dynamicSolver.price(context);

        assertTrue(staticPricing.exact(), label + " static pricing remains exact");
        assertTrue(dynamicPricing.exact(), label + " dynamic pricing remains exact");
        assertClose(staticPricing.bestReducedCost(), dynamicPricing.bestReducedCost(), TOLERANCE,
                label + " dynamic best RC equals static best RC");
        assertClose(staticPricing.bestReducedCost(), dynamicResult.bestReducedCost(), TOLERANCE,
                label + " direct dynamic solve agrees with pricing adapter");
        assertTrue(!dynamicResult.directionDecisions().isEmpty(),
                label + " dynamic solver records direction decisions");
        assertTrue(dynamicResult.dominanceCleanupPrunedLabels() <= dynamicResult.dominatedLabels(),
                label + " cleanup-pruned labels are part of dominated label accounting");
        if (context.hasRobustCuts() && !context.hasSubsetRowCuts()) {
            assertTrue(context.satisfiesForwardDti(), label + " robust repair certifies forward DTI");
            assertTrue(context.satisfiesBackwardPti(), label + " robust repair certifies backward PTI");
        }
        if (context.hasSubsetRowCuts()) {
            assertEquals(0, dynamicResult.dominanceCleanupPrunedLabels(),
                    label + " SR context disables dynamic cleanup pruning");
        }
        if (!dynamicResult.bestRoute().isEmpty()) {
            assertClose(
                    context.directReducedCost(dynamicResult.bestRoute()),
                    dynamicResult.bestReducedCost(),
                    TOLERANCE,
                    label + " dynamic best route direct RC audit");
        }
    }

    private static void assertNoActiveSubsetRowCutsLeavePricingUnchanged() {
        Instance instance = tinyThreeRequestInstance();
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        PricingContext noCuts = PricingContext.noCuts(matrices);
        PricingContext emptySr = PricingContext.withSubsetRowCuts(matrices, List.of());
        List<Integer> route = List.of(0, 1, 2, 4, 5, 7);

        assertClose(
                noCuts.directReducedCost(route),
                emptySr.directReducedCost(route),
                TOLERANCE,
                "empty SR context leaves direct RC unchanged");
        assertClose(
                findForwardLabel(noCuts, route).reducedCost(),
                findForwardLabel(emptySr, route).reducedCost(),
                TOLERANCE,
                "empty SR context leaves forward pricing unchanged");
        assertClose(
                findBackwardLabel(noCuts, route).reducedCost(),
                findBackwardLabel(emptySr, route).reducedCost(),
                TOLERANCE,
                "empty SR context leaves backward pricing unchanged");
    }

    private static PricingContext activeSrContext(Instance instance, double sigma) {
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        return PricingContext.withSubsetRowCuts(matrices, List.of(baseCut(sigma)));
    }

    private static SubsetRowCut baseCut(double sigma) {
        return SubsetRowCut.ofL2Triple("sr-U123-l2", 1, 2, 3, sigma);
    }

    private static ForwardLabel findForwardLabel(PricingContext context, List<Integer> route) {
        for (ForwardLabel label : new ForwardLabeler().solve(context).completeLabels()) {
            if (label.vertexIds().equals(route)) {
                return label;
            }
        }
        throw new AssertionError("missing forward label route=" + route);
    }

    private static BackwardLabel findBackwardLabel(PricingContext context, List<Integer> route) {
        for (BackwardLabel label : new BackwardLabeler().solve(context).completeLabels()) {
            if (label.vertexIds().equals(route)) {
                return label;
            }
        }
        throw new AssertionError("missing backward label route=" + route);
    }

    private static LabelIndex buildLabelIndex(PricingContext context) {
        ForwardLabeler.Result forward = new ForwardLabeler().solve(context);
        BackwardLabeler.Result backward = new BackwardLabeler().solve(context);
        Map<List<Integer>, ForwardLabel> forwardByRoute = new LinkedHashMap<List<Integer>, ForwardLabel>();
        Map<List<Integer>, BackwardLabel> backwardByRoute = new LinkedHashMap<List<Integer>, BackwardLabel>();
        for (ForwardLabel label : forward.partialLabels()) {
            forwardByRoute.put(label.vertexIds(), label);
        }
        for (ForwardLabel label : forward.completeLabels()) {
            forwardByRoute.put(label.vertexIds(), label);
        }
        for (BackwardLabel label : backward.partialLabels()) {
            backwardByRoute.put(label.vertexIds(), label);
        }
        for (BackwardLabel label : backward.completeLabels()) {
            backwardByRoute.put(label.vertexIds(), label);
        }
        return new LabelIndex(forwardByRoute, backwardByRoute);
    }

    private static Instance tinyThreeRequestInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = new double[8][8];
        for (int from = 0; from < matrix.length; from++) {
            for (int to = 0; to < matrix[from].length; to++) {
                matrix[from][to] = Math.abs(from - to);
            }
        }
        return new Instance(
                "sr-pricing-integration-test",
                3,
                3,
                2,
                vertices,
                matrix,
                matrix,
                Collections.emptyMap(),
                0.0);
    }

    private static void assertClose(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertTraceCutsActive(String trace, int expectedIterations, int expectedCutsActive) {
        String[] rows = trace.split("\\R", -1);
        if (rows.length != expectedIterations + 1) {
            throw new AssertionError("root CG trace row count expected="
                    + (expectedIterations + 1) + " actual=" + rows.length);
        }
        if (!TraceCsv.ROOT_CG_HEADER.equals(rows[0])) {
            throw new AssertionError("root CG trace header mismatch");
        }
        for (int index = 1; index < rows.length; index++) {
            String[] fields = rows[index].split(",", -1);
            if (fields.length != TraceCsv.ROOT_CG_HEADER.split(",", -1).length) {
                throw new AssertionError("root CG trace field count mismatch row=" + rows[index]);
            }
            int cutsActive = Integer.parseInt(fields[8]);
            if (cutsActive != expectedCutsActive) {
                throw new AssertionError("root CG trace cutsActive expected="
                        + expectedCutsActive + " actual=" + cutsActive + " row=" + rows[index]);
            }
        }
    }

    private record LabelIndex(
            Map<List<Integer>, ForwardLabel> forwardByRoute,
            Map<List<Integer>, BackwardLabel> backwardByRoute) {
    }

    private static final class SubsetRowContextProbe implements PricingSolver {
        private boolean called;
        private boolean sawSubsetRowContext;

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            throw new AssertionError("root CG should call price(PricingContext) when SR cut rows are active");
        }

        @Override
        public PricingResult price(PricingContext context) {
            called = true;
            sawSubsetRowContext = context.hasSubsetRowCuts();
            return PricingResult.noNegativeColumn(0.0);
        }

        boolean called() {
            return called;
        }

        boolean sawSubsetRowContext() {
            return sawSubsetRowContext;
        }
    }

    private static final class SingleArcRobustCut implements RobustCut {
        private final String name;
        private final double dualValue;
        private final int from;
        private final int to;
        private final double coefficient;

        private SingleArcRobustCut(String name, double dualValue, int from, int to, double coefficient) {
            this.name = name;
            this.dualValue = dualValue;
            this.from = from;
            this.to = to;
            this.coefficient = coefficient;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public double dualValue() {
            return dualValue;
        }

        @Override
        public double arcCoefficient(Instance instance, int from, int to) {
            return this.from == from && this.to == to ? coefficient : 0.0;
        }
    }
}
