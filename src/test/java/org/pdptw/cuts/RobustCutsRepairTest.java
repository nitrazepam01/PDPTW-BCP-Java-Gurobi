package org.pdptw.cuts;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.io.TinyJsonReader;
import org.pdptw.pricing.BidirectionalMergeTest;
import org.pdptw.pricing.BidirectionalMerger;
import org.pdptw.pricing.BackwardLabel;
import org.pdptw.pricing.ForwardLabel;
import org.pdptw.pricing.ReducedCostMatrices;
import org.pdptw.validation.BruteForcePricingOracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RobustCutsRepairTest {
    private static final double TOLERANCE = 1.0e-7;

    private RobustCutsRepairTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("RobustCutsRepairTest OK");
    }

    public static void run() throws Exception {
        BidirectionalMergeTest.run();
        assertNoCutRepairPreservesDirectRouteCosts("tiny-a-wide.json");
        assertNoCutRepairPreservesDirectRouteCosts("tiny-e-random-n5-seeded.json");
        assertRoundedCapacityRequestSetContract();
        assertCraftedRobustPerturbationIsRepaired();
    }

    private static void assertRoundedCapacityRequestSetContract() throws Exception {
        Instance tinyA = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        Instance tinyB = new TinyJsonReader().read(referencePath("tiny-b-capacity-precedence.json"));

        RobustCutRow tinyARow = RoundedCapacityCut.requestSetRow(tinyA, List.of(1, 2));
        assertClose("Tiny-A rounded-capacity RHS", 1.0, tinyARow.rhs());
        assertClose("Tiny-A rounded-capacity full-route coefficient",
                1.0,
                tinyARow.routeCoefficient(tinyA, Route.of(0, 1, 2, 3, 4, 5)));

        RobustCutRow tinyBRow = RoundedCapacityCut.requestSetRow(tinyB, "tiny-b-rcap-R1-2", List.of(2, 1));
        assertClose("Tiny-B rounded-capacity RHS", 2.0, tinyBRow.rhs());
        assertClose("Tiny-B capacity-violating pickup block exits once",
                1.0,
                tinyBRow.routeCoefficient(tinyB, Route.of(0, 1, 2, 3, 4, 5)));
        assertClose("Tiny-B feasible sequential pickup blocks exit twice",
                2.0,
                tinyBRow.routeCoefficient(tinyB, Route.of(0, 1, 3, 2, 4, 5)));

        RoundedCapacityCut pricingCut =
                RoundedCapacityCut.forRequestSet(tinyB, "tiny-b-rcap-pricing", 2.5, List.of(1, 2));
        assertClose("Tiny-B RCC pricing arc from pickup set to delivery",
                2.5,
                pricingCut.arcPrice(tinyB, 1, 3));
        assertClose("Tiny-B RCC pricing arc within pickup set has zero price",
                0.0,
                pricingCut.arcPrice(tinyB, 1, 2));

        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(tinyB);
        List<RobustCut> cuts = List.of(pricingCut);
        List<Integer> feasibleRoute = ints(0, 1, 3, 2, 4, 5);
        double expected = matrices.directReducedCost(feasibleRoute) + 2.5 * 2.0;
        assertClose("Tiny-B RCC direct route RC includes pickup-exit price",
                expected,
                DtiPtiRepair.robustDirectReducedCost(matrices, cuts, feasibleRoute));

        DtiPtiRepair.RepairResult forwardRepair = DtiPtiRepair.repairForwardDti(
                tinyB,
                DtiPtiRepair.forwardMatrixWithRobustCuts(matrices, cuts));
        DtiPtiRepair.RepairResult backwardRepair = DtiPtiRepair.repairBackwardPti(
                tinyB,
                DtiPtiRepair.backwardMatrixWithRobustCuts(matrices, cuts));
        if (!DtiPtiRepair.satisfiesForwardDti(tinyB, forwardRepair.matrix())) {
            throw new AssertionError("Tiny-B RCC forward repair must certify DTI");
        }
        if (!DtiPtiRepair.satisfiesBackwardPti(tinyB, backwardRepair.matrix())) {
            throw new AssertionError("Tiny-B RCC backward repair must certify PTI");
        }
        assertClose("Tiny-B RCC forward repair preserves feasible route RC",
                expected,
                forwardRepair.arcReducedCostSum(feasibleRoute));
        assertClose("Tiny-B RCC backward repair preserves feasible route RC",
                expected,
                backwardRepair.arcReducedCostSum(feasibleRoute));
    }

    private static void assertNoCutRepairPreservesDirectRouteCosts(String fixtureName) throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath(fixtureName));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);

        DtiPtiRepair.RepairResult forwardRepair =
                DtiPtiRepair.repairForwardDti(instance, matrices.forwardArcReducedCosts());
        DtiPtiRepair.RepairResult backwardRepair =
                DtiPtiRepair.repairBackwardPti(instance, matrices.backwardArcReducedCosts());

        if (!DtiPtiRepair.satisfiesForwardDti(instance, forwardRepair.matrix())) {
            throw new AssertionError(fixtureName + " forward no-cut repair did not satisfy DTI");
        }
        if (!DtiPtiRepair.satisfiesBackwardPti(instance, backwardRepair.matrix())) {
            throw new AssertionError(fixtureName + " backward no-cut repair did not satisfy PTI");
        }
        assertAllRouteSumsPreserved(fixtureName, instance, matrices.forwardArcReducedCosts(), forwardRepair);
        assertAllRouteSumsPreserved(fixtureName, instance, matrices.backwardArcReducedCosts(), backwardRepair);
        assertAllRouteSumsEqualDirect(fixtureName, instance, matrices, forwardRepair);
        assertAllRouteSumsEqualDirect(fixtureName, instance, matrices, backwardRepair);
    }

    private static void assertCraftedRobustPerturbationIsRepaired() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        List<RobustCut> cuts = List.of(craftedRobustCut());

        double[][] forwardWithCuts = DtiPtiRepair.forwardMatrixWithRobustCuts(matrices, cuts);
        double[][] backwardWithCuts = DtiPtiRepair.backwardMatrixWithRobustCuts(matrices, cuts);
        if (DtiPtiRepair.satisfiesForwardDti(instance, forwardWithCuts)) {
            throw new AssertionError("crafted robust arc dual should break forward DTI before repair");
        }
        if (DtiPtiRepair.satisfiesBackwardPti(instance, backwardWithCuts)) {
            throw new AssertionError("crafted robust arc dual should break backward PTI before repair");
        }

        DtiPtiRepair.RepairResult forwardRepair = DtiPtiRepair.repairForwardDti(instance, forwardWithCuts);
        DtiPtiRepair.RepairResult backwardRepair = DtiPtiRepair.repairBackwardPti(instance, backwardWithCuts);
        if (!DtiPtiRepair.satisfiesForwardDti(instance, forwardRepair.matrix())) {
            throw new AssertionError("forward robust repair did not satisfy DTI");
        }
        if (!DtiPtiRepair.satisfiesBackwardPti(instance, backwardRepair.matrix())) {
            throw new AssertionError("backward robust repair did not satisfy PTI");
        }
        if (positiveThetaSum(forwardRepair) <= 0.0 || positiveThetaSum(backwardRepair) <= 0.0) {
            throw new AssertionError("crafted robust repair should compute positive theta values");
        }

        assertAllRouteSumsPreserved("tiny-a-wide robust forward", instance, forwardWithCuts, forwardRepair);
        assertAllRouteSumsPreserved("tiny-a-wide robust backward", instance, backwardWithCuts, backwardRepair);
        assertAllRobustRouteSumsEqualDirect("tiny-a-wide robust forward direct",
                matrices, cuts, forwardRepair);
        assertAllRobustRouteSumsEqualDirect("tiny-a-wide robust backward direct",
                matrices, cuts, backwardRepair);
        assertTinyAMergeCorrectionWithTheta(matrices, forwardWithCuts, forwardRepair, backwardRepair);
    }

    private static RobustCut craftedRobustCut() {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 2), 20.0);
        coefficients.put(new RobustCut.Arc(3, 4), 20.0);
        return RoundedCapacityCut.ofArcCoefficients("crafted-robust-arc-prices", 1.0, coefficients);
    }

    private static void assertAllRouteSumsPreserved(
            String label,
            Instance instance,
            double[][] unrepaired,
            DtiPtiRepair.RepairResult repair) {
        BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
        int checked = 0;
        for (BruteForcePricingOracle.RouteEvaluation route : oracle.routes()) {
            double before = DtiPtiRepair.arcReducedCostSum(unrepaired, route.vertexIds());
            double after = repair.arcReducedCostSum(route.vertexIds());
            assertClose(label + " preserve route " + route.vertexIds(), before, after);
            checked++;
        }
        if (checked == 0) {
            throw new AssertionError(label + " did not check any complete feasible route");
        }
    }

    private static void assertAllRouteSumsEqualDirect(
            String fixtureName,
            Instance instance,
            ReducedCostMatrices matrices,
            DtiPtiRepair.RepairResult repair) {
        BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
        for (BruteForcePricingOracle.RouteEvaluation route : oracle.routes()) {
            assertClose(fixtureName + " repaired route equals direct " + route.vertexIds(),
                    matrices.directReducedCost(route.vertexIds()),
                    repair.arcReducedCostSum(route.vertexIds()));
        }
    }

    private static void assertAllRobustRouteSumsEqualDirect(
            String label,
            ReducedCostMatrices matrices,
            List<? extends RobustCut> cuts,
            DtiPtiRepair.RepairResult repair) {
        BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(matrices.instance());
        for (BruteForcePricingOracle.RouteEvaluation route : oracle.routes()) {
            assertClose(label + " repaired route equals robust direct " + route.vertexIds(),
                    DtiPtiRepair.robustDirectReducedCost(matrices, cuts, route.vertexIds()),
                    repair.arcReducedCostSum(route.vertexIds()));
        }
    }

    private static void assertTinyAMergeCorrectionWithTheta(
            ReducedCostMatrices matrices,
            double[][] forwardWithCuts,
            DtiPtiRepair.RepairResult forwardRepair,
            DtiPtiRepair.RepairResult backwardRepair) {
        List<Integer> fullRoute = ints(0, 1, 2, 3, 4, 5);
        List<Integer> forwardPrefix = ints(0, 1, 2);
        List<Integer> backwardSuffix = ints(2, 3, 4, 5);
        long forwardOpen = BitSetOps.add(BitSetOps.add(0L, 1), 2);
        long backwardOpen = BitSetOps.add(0L, 1);

        double directRobustRouteCost = DtiPtiRepair.arcReducedCostSum(forwardWithCuts, fullRoute);
        double forwardReducedCost = forwardRepair.arcReducedCostSum(forwardPrefix);
        double backwardReducedCost = backwardRepair.arcReducedCostSum(backwardSuffix);
        double merged = DtiPtiRepair.robustMergedReducedCost(
                forwardReducedCost,
                backwardReducedCost,
                matrices,
                forwardOpen,
                backwardOpen,
                forwardRepair,
                backwardRepair);

        assertClose("Tiny-A robust theta merge correction", directRobustRouteCost, merged);
        if (Math.abs((forwardReducedCost + backwardReducedCost) - merged) < TOLERANCE) {
            throw new AssertionError("robust merge test should require theta/open-request correction");
        }

        ForwardLabel forwardLabel = new ForwardLabel(
                2,
                forwardReducedCost,
                2.0,
                2,
                0L,
                forwardOpen,
                forwardPrefix);
        BackwardLabel backwardLabel = new BackwardLabel(
                2,
                backwardReducedCost,
                90.0,
                1,
                BitSetOps.add(0L, 2),
                backwardOpen,
                backwardSuffix);
        BidirectionalMerger.MergeResult hookedMerge = new BidirectionalMerger()
                .merge(
                        matrices,
                        forwardLabel,
                        backwardLabel,
                        (activeMatrices, forward, backward) -> DtiPtiRepair.robustMergeCorrection(
                                activeMatrices,
                                forward.openMask(),
                                backward.openMask(),
                                forwardRepair,
                                backwardRepair),
                        route -> DtiPtiRepair.arcReducedCostSum(forwardWithCuts, route))
                .orElseThrow(() -> new AssertionError("robust-repair merge hook should accept Tiny-A split"));
        assertClose("Tiny-A robust merge hook audits direct RC",
                directRobustRouteCost,
                hookedMerge.mergedReducedCost());
    }

    private static double positiveThetaSum(DtiPtiRepair.RepairResult repair) {
        double sum = 0.0;
        for (double value : repair.thetaOneIndexed()) {
            if (value > 0.0) {
                sum += value;
            }
        }
        return sum;
    }

    private static Path referencePath(String name) {
        Path[] candidates = new Path[] {
                Path.of("..", "references", "tiny", name),
                Path.of("references", "tiny", name),
                Path.of("G:\\bid\\references\\tiny", name)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate tiny reference file: " + name);
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static List<Integer> ints(Integer... values) {
        return Arrays.asList(values);
    }
}
