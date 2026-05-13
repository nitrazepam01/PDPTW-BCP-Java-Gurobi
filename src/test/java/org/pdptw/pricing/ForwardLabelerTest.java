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

public final class ForwardLabelerTest {
    private static final double TOLERANCE = 1.0e-7;

    private ForwardLabelerTest() {
    }

    public static void main(String[] args) throws Exception {
        assertForwardAlphaIsOne();
        assertMatchesOracleForThreeDualVectors("tiny-a-wide.json");
        assertMatchesOracleForThreeDualVectors("tiny-e-random-n5-seeded.json");
        assertStrongDominanceGate();
        assertStrongDominanceRequiresSameSetOutflowState();
        System.out.println("ForwardLabelerTest OK");
    }

    public static void run() throws Exception {
        main(new String[0]);
    }

    private static void assertForwardAlphaIsOne() {
        assertClose("forward alpha", 1.0, LabelExtender.FORWARD_ALPHA);
    }

    private static void assertMatchesOracleForThreeDualVectors(String fixtureName) throws Exception {
        Instance base = new TinyJsonReader().read(referencePath(fixtureName));
        for (DualVector dualVector : dualVectors(base)) {
            Instance instance = withDuals(base, dualVector);
            ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
            BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
            ForwardLabeler.Result forward = new ForwardLabeler().solve(matrices);

            assertEquals(fixtureName + " " + dualVector.name + " complete label count",
                    oracle.enumeratedFeasibleNonEmptyRoutes(), forward.completeLabels().size());
            assertClose(fixtureName + " " + dualVector.name + " forward best rc",
                    oracle.bestReducedCost(), forward.bestReducedCost());
            assertList(fixtureName + " " + dualVector.name + " forward best route",
                    oracle.bestRoute(), forward.bestRoute());
            assertCompleteLabelsAudited(instance, forward.completeLabels());

            if (!ForwardLabeler.satisfiesForwardDti(matrices)) {
                throw new AssertionError(fixtureName + " " + dualVector.name + " should satisfy forward DTI");
            }
            ForwardLabeler.Result strong = new ForwardLabeler(true).solve(matrices);
            assertClose(fixtureName + " " + dualVector.name + " strong forward best rc",
                    oracle.bestReducedCost(), strong.bestReducedCost());
            assertList(fixtureName + " " + dualVector.name + " strong forward best route",
                    oracle.bestRoute(), strong.bestRoute());
            assertCompleteLabelsAudited(instance, strong.completeLabels());
        }
    }

    private static void assertCompleteLabelsAudited(Instance instance, List<ForwardLabel> labels) {
        RouteChecker checker = new RouteChecker();
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        for (ForwardLabel label : labels) {
            if (label.openMask() != 0L) {
                throw new AssertionError("forward sink label has open requests: " + label.vertexIds());
            }
            Route route = new Route(label.vertexIds());
            RouteChecker.Result check = checker.check(instance, route);
            if (!check.feasible()) {
                throw new AssertionError("forward label produced infeasible route " + label.vertexIds()
                        + " reason=" + check.reason());
            }
            assertClose("forward alpha=1 label rc",
                    matrices.forwardArcReducedCostSum(label.vertexIds()),
                    label.reducedCost());
            assertClose("forward direct rc",
                    matrices.directReducedCost(label.vertexIds()),
                    label.reducedCost());
        }
    }

    private static void assertStrongDominanceGate() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ForwardLabel left = new ForwardLabel(2, 1.0, 5.0, 1,
                1L, 0L, Arrays.asList(0, 1, 3, 2));
        ForwardLabel right = new ForwardLabel(2, 2.0, 6.0, 1,
                3L, 0L, Arrays.asList(0, 1, 3, 4, 2));

        assertThrows("forward dominance without DTI gate",
                () -> Dominance.forwardStrongDominates(left, right, false));
        assertThrows("forward dominance without explicit DTI argument",
                () -> Dominance.forwardStrongDominates(left, right));
        if (!Dominance.forwardStrongDominates(left, right, true)) {
            throw new AssertionError("expected forward strong dominance after DTI gate");
        }

        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        if (!ForwardLabeler.satisfiesForwardDti(matrices)) {
            throw new AssertionError("Tiny-A forward matrix should pass the DTI gate");
        }
        ForwardLabeler.Result strong = new ForwardLabeler(true).solve(matrices);
        BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
        assertClose("strong-dominance forward best rc", oracle.bestReducedCost(), strong.bestReducedCost());

        Instance dtiViolation = withTravelCostOverride(instance, 0, 1, 1_000.0);
        ReducedCostMatrices violatingMatrices = ReducedCostMatrices.fromInstanceDuals(dtiViolation);
        if (ForwardLabeler.satisfiesForwardDti(violatingMatrices)) {
            throw new AssertionError("expected modified forward matrix to fail DTI");
        }
        assertThrows("forward labeler strong dominance rejects failed DTI",
                () -> new ForwardLabeler(true).solve(violatingMatrices));
    }

    private static void assertStrongDominanceRequiresSameSetOutflowState() {
        ForwardLabel left = new ForwardLabel(2, 1.0, 5.0, 1,
                1L,
                0L,
                new int[0],
                new int[] {SetOutflowPricingRule.STATE_INSIDE},
                Arrays.asList(0, 1, 3, 2));
        ForwardLabel sameStateRight = new ForwardLabel(2, 2.0, 6.0, 1,
                3L,
                0L,
                new int[0],
                new int[] {SetOutflowPricingRule.STATE_INSIDE},
                Arrays.asList(0, 1, 3, 4, 2));
        ForwardLabel differentStateRight = new ForwardLabel(2, 2.0, 6.0, 1,
                3L,
                0L,
                new int[0],
                new int[] {SetOutflowPricingRule.STATE_OUTSIDE},
                Arrays.asList(0, 1, 3, 4, 2));

        if (!Dominance.forwardStrongDominates(left, sameStateRight, true)) {
            throw new AssertionError("same set-outflow state should allow forward dominance");
        }
        if (Dominance.forwardStrongDominates(left, differentStateRight, true)) {
            throw new AssertionError("different set-outflow states must block forward dominance");
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
                source.name() + "-dti-violation",
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
