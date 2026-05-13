package org.pdptw.pricing;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.RouteChecker;
import org.pdptw.io.TinyJsonReader;
import org.pdptw.validation.BruteForcePricingOracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BackwardLabelerTest {
    private static final double TOLERANCE = 1.0e-7;

    private BackwardLabelerTest() {
    }

    public static void main(String[] args) throws Exception {
        assertBackwardAlphaIsZero();
        assertForwardAndBackwardMatchOracleForThreeDualVectors("tiny-a-wide.json");
        assertForwardAndBackwardMatchOracleForThreeDualVectors("tiny-e-random-n5-seeded.json");
        assertStrongDominanceGate();
        assertStrongDominanceRequiresSameSetOutflowState();
        System.out.println("BackwardLabelerTest OK");
    }

    public static void run() throws Exception {
        main(new String[0]);
    }

    private static void assertBackwardAlphaIsZero() {
        assertClose("backward alpha", 0.0, LabelExtender.BACKWARD_ALPHA);
    }

    private static void assertForwardAndBackwardMatchOracleForThreeDualVectors(String fixtureName) throws Exception {
        Instance base = new TinyJsonReader().read(referencePath(fixtureName));
        for (DualVector dualVector : dualVectors(base)) {
            Instance instance = withDuals(base, dualVector);
            ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
            BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
            ForwardLabeler.Result forward = new ForwardLabeler().solve(matrices);
            BackwardLabeler.Result backward = new BackwardLabeler().solve(matrices);

            assertEquals(fixtureName + " " + dualVector.name + " forward complete label count",
                    oracle.enumeratedFeasibleNonEmptyRoutes(), forward.completeLabels().size());
            assertEquals(fixtureName + " " + dualVector.name + " backward complete label count",
                    oracle.enumeratedFeasibleNonEmptyRoutes(), backward.completeLabels().size());
            assertClose(fixtureName + " " + dualVector.name + " forward best rc",
                    oracle.bestReducedCost(), forward.bestReducedCost());
            assertClose(fixtureName + " " + dualVector.name + " backward best rc",
                    oracle.bestReducedCost(), backward.bestReducedCost());
            assertList(fixtureName + " " + dualVector.name + " forward best route",
                    oracle.bestRoute(), forward.bestRoute());
            assertList(fixtureName + " " + dualVector.name + " backward best route",
                    oracle.bestRoute(), backward.bestRoute());
            assertCompleteBackwardLabelsAudited(instance, backward.completeLabels());

            if (!BackwardLabeler.satisfiesBackwardPti(matrices)) {
                throw new AssertionError(fixtureName + " " + dualVector.name + " should satisfy backward PTI");
            }
            BackwardLabeler.Result strong = new BackwardLabeler(true).solve(matrices);
            assertClose(fixtureName + " " + dualVector.name + " strong backward best rc",
                    oracle.bestReducedCost(), strong.bestReducedCost());
            assertRouteFeasible(fixtureName + " " + dualVector.name + " strong backward best route",
                    instance, strong.bestRoute());
            assertCompleteBackwardLabelsAudited(instance, strong.completeLabels());
        }
    }

    private static void assertCompleteBackwardLabelsAudited(Instance instance, List<BackwardLabel> labels) {
        RouteChecker checker = new RouteChecker();
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        for (BackwardLabel label : labels) {
            if (label.openMask() != 0L) {
                throw new AssertionError("backward origin label has open requests: " + label.vertexIds());
            }
            Route route = new Route(label.vertexIds());
            RouteChecker.Result check = checker.check(instance, route);
            if (!check.feasible()) {
                throw new AssertionError("backward label produced infeasible route " + label.vertexIds()
                        + " reason=" + check.reason());
            }
            assertClose("backward alpha=0 label rc",
                    matrices.backwardArcReducedCostSum(label.vertexIds()),
                    label.reducedCost());
            assertClose("backward direct rc",
                    matrices.directReducedCost(label.vertexIds()),
                    label.reducedCost());
        }
    }

    private static void assertRouteFeasible(String label, Instance instance, List<Integer> vertexIds) {
        RouteChecker.Result check = new RouteChecker().check(instance, new Route(vertexIds));
        if (!check.feasible()) {
            throw new AssertionError(label + " infeasible: " + vertexIds + " reason=" + check.reason());
        }
    }

    private static void assertStrongDominanceGate() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        BackwardLabel left = new BackwardLabel(4, 1.0, 20.0, 1,
                1L, 0L, Arrays.asList(4, 1, 3, 5));
        BackwardLabel right = new BackwardLabel(4, 2.0, 10.0, 1,
                3L, 0L, Arrays.asList(4, 2, 1, 3, 5));

        assertThrows("backward dominance without PTI gate",
                () -> Dominance.backwardStrongDominates(left, right, false));
        assertThrows("backward dominance without explicit PTI argument",
                () -> Dominance.backwardStrongDominates(left, right));
        if (!Dominance.backwardStrongDominates(left, right, true)) {
            throw new AssertionError("expected backward strong dominance after PTI gate");
        }

        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        if (!BackwardLabeler.satisfiesBackwardPti(matrices)) {
            throw new AssertionError("Tiny-A backward matrix should pass the PTI gate");
        }
        BackwardLabeler.Result strong = new BackwardLabeler(true).solve(matrices);
        BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
        assertClose("strong-dominance backward best rc", oracle.bestReducedCost(), strong.bestReducedCost());

        Instance ptiViolation = withTravelCostOverride(instance, 0, 2, 1_000.0);
        ReducedCostMatrices violatingMatrices = ReducedCostMatrices.fromInstanceDuals(ptiViolation);
        if (BackwardLabeler.satisfiesBackwardPti(violatingMatrices)) {
            throw new AssertionError("expected modified backward matrix to fail PTI");
        }
        assertThrows("backward labeler strong dominance rejects failed PTI",
                () -> new BackwardLabeler(true).solve(violatingMatrices));
    }

    private static void assertStrongDominanceRequiresSameSetOutflowState() {
        BackwardLabel left = new BackwardLabel(4, 1.0, 20.0, 1,
                1L,
                0L,
                new int[0],
                new int[] {SetOutflowPricingRule.STATE_INSIDE},
                Arrays.asList(4, 1, 3, 5));
        BackwardLabel sameStateRight = new BackwardLabel(4, 2.0, 10.0, 1,
                3L,
                0L,
                new int[0],
                new int[] {SetOutflowPricingRule.STATE_INSIDE},
                Arrays.asList(4, 2, 1, 3, 5));
        BackwardLabel differentStateRight = new BackwardLabel(4, 2.0, 10.0, 1,
                3L,
                0L,
                new int[0],
                new int[] {SetOutflowPricingRule.STATE_OUTSIDE},
                Arrays.asList(4, 2, 1, 3, 5));

        if (!Dominance.backwardStrongDominates(left, sameStateRight, true)) {
            throw new AssertionError("same set-outflow state should allow backward dominance");
        }
        if (Dominance.backwardStrongDominates(left, differentStateRight, true)) {
            throw new AssertionError("different set-outflow states must block backward dominance");
        }
    }

    private static List<DualVector> dualVectors(Instance base) {
        return Arrays.asList(
                new DualVector("fixture-duals", oneIndexedDuals(base), base.fleetDual()),
                new DualVector("zero-duals", filledDuals(base.nRequests(), 0.0), 0.0),
                new DualVector("pattern-duals", patternDuals(base.nRequests()), 1.25));
    }

    private static double[] oneIndexedDuals(Instance instance) {
        double[] duals = new double[instance.nRequests() + 1];
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            duals[requestId] = instance.requestDual(requestId);
        }
        return duals;
    }

    private static double[] filledDuals(int nRequests, double value) {
        double[] duals = new double[nRequests + 1];
        for (int requestId = 1; requestId <= nRequests; requestId++) {
            duals[requestId] = value;
        }
        return duals;
    }

    private static double[] patternDuals(int nRequests) {
        double[] duals = new double[nRequests + 1];
        for (int requestId = 1; requestId <= nRequests; requestId++) {
            duals[requestId] = 0.75 + requestId * 1.5;
        }
        return duals;
    }

    private static Instance withDuals(Instance source, DualVector dualVector) {
        Map<Integer, Double> requestDuals = new LinkedHashMap<>();
        for (int requestId = 1; requestId <= source.nRequests(); requestId++) {
            requestDuals.put(Integer.valueOf(requestId), Double.valueOf(dualVector.requestDuals[requestId]));
        }
        return new Instance(
                source.name() + "-" + dualVector.name,
                source.nRequests(),
                source.vehicleCapacity(),
                source.maxVehicles(),
                source.vertices(),
                matrix(source, true),
                matrix(source, false),
                requestDuals,
                dualVector.fleetDual);
    }

    private static Instance withTravelCostOverride(Instance source, int from, int to, double cost) {
        double[][] costs = matrix(source, true);
        costs[from][to] = cost;
        return new Instance(
                source.name() + "-pti-violation",
                source.nRequests(),
                source.vehicleCapacity(),
                source.maxVehicles(),
                source.vertices(),
                costs,
                matrix(source, false),
                source.requestDuals(),
                source.fleetDual());
    }

    private static double[][] matrix(Instance instance, boolean costs) {
        int dimension = 0;
        for (int vertexId = 0; vertexId <= instance.endDepotId(); vertexId++) {
            if (instance.hasVertex(vertexId)) {
                dimension = Math.max(dimension, vertexId + 1);
            }
        }
        double[][] matrix = new double[dimension][dimension];
        for (int from = 0; from < dimension; from++) {
            for (int to = 0; to < dimension; to++) {
                matrix[from][to] = costs ? instance.travelCost(from, to) : instance.travelTime(from, to);
            }
        }
        return matrix;
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

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertList(String label, List<Integer> expected, List<Integer> actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertThrows(String label, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException expected) {
            return;
        } catch (Exception exception) {
            throw new AssertionError(label + " threw unexpected checked exception", exception);
        }
        throw new AssertionError(label + " should have thrown");
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class DualVector {
        private final String name;
        private final double[] requestDuals;
        private final double fleetDual;

        private DualVector(String name, double[] requestDuals, double fleetDual) {
            this.name = name;
            this.requestDuals = requestDuals;
            this.fleetDual = fleetDual;
        }
    }
}
