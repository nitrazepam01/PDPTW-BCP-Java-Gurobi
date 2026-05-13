package org.pdptw.pricing;

import org.pdptw.cuts.SubsetRowCut;
import org.pdptw.core.Instance;
import org.pdptw.io.TinyJsonReader;
import org.pdptw.validation.BruteForcePricingOracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DynamicHalfwayControllerTest {
    private static final double TOLERANCE = 1.0e-7;

    private DynamicHalfwayControllerTest() {
    }

    public static void main(String[] args) throws Exception {
        assertDirectionSelectionUsesUnprocessedCounts();
        assertBoundUpdatesAndCounters();
        assertPruneUnprocessedCounts();
        assertDynamicSolverDoesNotReplayStaticSolver();
        assertDynamicMatchesStaticAndOracle("tiny-a-wide.json");
        assertDynamicMatchesStaticAndOracle("tiny-b-capacity-precedence.json");
        assertDynamicMatchesStaticAndOracle("tiny-e-random-n5-seeded.json");
        assertDynamicCountsCanDifferFromStatic();
        assertDynamicDominanceSkipsAtLeastOneLabel();
        assertDynamicDominanceDisabledForUnsafeCutContexts();
        assertPricingModeAliases();
        System.out.println("DynamicHalfwayControllerTest OK");
    }

    public static void run() throws Exception {
        main(new String[0]);
    }

    private static void assertDirectionSelectionUsesUnprocessedCounts() {
        assertDirection("both queues empty", DynamicHalfwayController.Direction.DONE, 0, 0);
        assertDirection("forward empty", DynamicHalfwayController.Direction.BACKWARD, 0, 4);
        assertDirection("backward empty", DynamicHalfwayController.Direction.FORWARD, 4, 0);
        assertDirection("tie chooses forward", DynamicHalfwayController.Direction.FORWARD, 3, 3);
        assertDirection("fewer forward", DynamicHalfwayController.Direction.FORWARD, 2, 5);
        assertDirection("fewer backward", DynamicHalfwayController.Direction.BACKWARD, 5, 2);
        assertThrows("negative forward count",
                () -> DynamicHalfwayController.chooseDirection(-1, 0));
        assertThrows("negative backward count",
                () -> DynamicHalfwayController.chooseDirection(0, -1));
    }

    private static void assertBoundUpdatesAndCounters() {
        DynamicHalfwayController controller = new DynamicHalfwayController(0.0, 100.0, 2, 3);
        DynamicHalfwayController.Decision first = controller.process(
                DynamicHalfwayController.Direction.FORWARD,
                25.0,
                1,
                0);
        assertClose("forward bound update HB", 25.0, first.after().backwardLowerBound());
        assertClose("forward bound update HF", 100.0, first.after().forwardUpperBound());
        assertEquals("processed forward", 1, first.after().processedForwardLabels());
        assertEquals("generated forward", 3, first.after().generatedForwardLabels());
        assertEquals("unprocessed forward", 2, first.after().unprocessedForwardLabels());
        assertEquals("unprocessed backward", 3, first.after().unprocessedBackwardLabels());

        DynamicHalfwayController.Decision second = controller.process(
                DynamicHalfwayController.Direction.BACKWARD,
                60.0);
        assertClose("backward bound update HB", 25.0, second.after().backwardLowerBound());
        assertClose("backward bound update HF", 60.0, second.after().forwardUpperBound());
        assertEquals("processed backward", 1, second.after().processedBackwardLabels());
        if (second.after().backwardLowerBound() > second.after().forwardUpperBound() + TOLERANCE) {
            throw new AssertionError("dynamic bounds must maintain HB <= HF");
        }
    }

    private static void assertDynamicSolverDoesNotReplayStaticSolver() throws Exception {
        Path source = sourcePath("BidirectionalDynamicPricingSolver.java");
        String text = Files.readString(source);
        assertDoesNotContain(source, text, "new BidirectionalPricingSolver");
        assertDoesNotContain(source, text, "new ForwardLabeler");
        assertDoesNotContain(source, text, "new BackwardLabeler");
        assertDoesNotContain(source, text, ".forwardResult()");
        assertDoesNotContain(source, text, ".backwardResult()");
    }

    private static void assertDynamicMatchesStaticAndOracle(String fixtureName) throws Exception {
        Instance base = new TinyJsonReader().read(referencePath(fixtureName));
        for (DualVector dualVector : dualVectors(base)) {
            Instance instance = withDuals(base, dualVector);
            ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
            BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
            BidirectionalPricingSolver.Result staticResult = new BidirectionalPricingSolver().solve(matrices);
            BidirectionalDynamicPricingSolver.Result dynamicResult =
                    new BidirectionalDynamicPricingSolver().solve(matrices);

            assertClose(fixtureName + " " + dualVector.name + " static vs oracle",
                    oracle.bestReducedCost(), staticResult.bestReducedCost());
            assertClose(fixtureName + " " + dualVector.name + " dynamic vs static",
                    staticResult.bestReducedCost(), dynamicResult.bestReducedCost());
            assertClose(fixtureName + " " + dualVector.name + " dynamic vs oracle",
                    oracle.bestReducedCost(), dynamicResult.bestReducedCost());
            if (dynamicResult.directionDecisions().isEmpty()) {
                throw new AssertionError(fixtureName + " " + dualVector.name + " should record dynamic decisions");
            }
            assertDirectionTraceUsesUnprocessedCounts(dynamicResult.directionDecisions());
            DynamicHalfwayController.Snapshot finalSnapshot = dynamicResult.finalSnapshot();
            assertEquals("final unprocessed forward", 0, finalSnapshot.unprocessedForwardLabels());
            assertEquals("final unprocessed backward", 0, finalSnapshot.unprocessedBackwardLabels());
            assertEquals("dynamic generated forward labels",
                    dynamicResult.forwardLabelCount(),
                    finalSnapshot.generatedForwardLabels());
            assertEquals("dynamic generated backward labels",
                    dynamicResult.backwardLabelCount(),
                    finalSnapshot.generatedBackwardLabels());
            if (dynamicResult.dominatedLabels() < 0) {
                throw new AssertionError("dynamic dominated label count must be non-negative");
            }
            if (dynamicResult.dominanceCleanupPrunedLabels() < 0
                    || dynamicResult.dominanceCleanupPrunedLabels() > dynamicResult.dominatedLabels()) {
                throw new AssertionError("dynamic cleanup-pruned labels must be a nonnegative dominated subset");
            }
            assertEquals("dynamic cleanup-pruned label stores",
                    dynamicResult.dominanceCleanupPrunedLabels(),
                    dynamicResult.forwardPrunedLabels().size() + dynamicResult.backwardPrunedLabels().size());
            if (finalSnapshot.backwardLowerBound() > finalSnapshot.forwardUpperBound() + TOLERANCE) {
                throw new AssertionError("dynamic final bounds must maintain HB <= HF");
            }
            assertEquals("dynamic dominance cleanup hook triggers",
                    countBoundChanges(dynamicResult.directionDecisions()),
                    dynamicResult.dominanceCleanupTriggers());
        }
    }

    private static void assertDynamicCountsCanDifferFromStatic() throws Exception {
        boolean sawDifferentCounts = false;
        for (String fixtureName : List.of(
                "tiny-a-wide.json",
                "tiny-b-capacity-precedence.json",
                "tiny-e-random-n5-seeded.json")) {
            Instance base = new TinyJsonReader().read(referencePath(fixtureName));
            for (DualVector dualVector : dualVectors(base)) {
                Instance instance = withDuals(base, dualVector);
                ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
                BidirectionalPricingSolver.Result staticResult = new BidirectionalPricingSolver().solve(matrices);
                BidirectionalDynamicPricingSolver.Result dynamicResult =
                        new BidirectionalDynamicPricingSolver().solve(matrices);
                assertClose(fixtureName + " " + dualVector.name + " dynamic count audit best rc",
                        staticResult.bestReducedCost(), dynamicResult.bestReducedCost());

                int staticForwardLabels = staticResult.forwardResult().partialLabels().size()
                        + staticResult.forwardResult().completeLabels().size();
                int staticBackwardLabels = staticResult.backwardResult().partialLabels().size()
                        + staticResult.backwardResult().completeLabels().size();
                if (staticForwardLabels != dynamicResult.forwardLabelCount()
                        || staticBackwardLabels != dynamicResult.backwardLabelCount()) {
                    sawDifferentCounts = true;
                }
            }
        }
        if (!sawDifferentCounts) {
            throw new AssertionError("dynamic labeling should expose at least one static-vs-dynamic label count change");
        }
    }

    private static void assertDynamicDominanceSkipsAtLeastOneLabel() throws Exception {
        boolean sawDominatedLabel = false;
        boolean sawCleanupPrunedLabel = false;
        for (String fixtureName : List.of(
                "tiny-a-wide.json",
                "tiny-b-capacity-precedence.json",
                "tiny-e-random-n5-seeded.json")) {
            Instance base = new TinyJsonReader().read(referencePath(fixtureName));
            for (DualVector dualVector : dualVectors(base)) {
                Instance instance = withDuals(base, dualVector);
                ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
                BidirectionalDynamicPricingSolver.Result dynamicResult =
                        new BidirectionalDynamicPricingSolver().solve(matrices);
                PricingResult pricingResult = new BidirectionalDynamicPricingSolver().price(matrices);
                assertEquals(fixtureName + " " + dualVector.name + " dynamic dominated stats",
                        dynamicResult.dominatedLabels(),
                        pricingResult.stats().dominatedLabels());
                assertEquals(fixtureName + " " + dualVector.name + " dynamic cleanup-pruned stats",
                        dynamicResult.dominanceCleanupPrunedLabels(),
                        pricingResult.stats().dominanceCleanupPrunedLabels());
                if (dynamicResult.dominatedLabels() > 0) {
                    sawDominatedLabel = true;
                }
                if (dynamicResult.dominanceCleanupPrunedLabels() > 0) {
                    sawCleanupPrunedLabel = true;
                }
            }
        }
        if (!sawDominatedLabel) {
            throw new AssertionError("dynamic insertion dominance should skip at least one tiny label");
        }
        if (!sawCleanupPrunedLabel) {
            throw new AssertionError("dynamic bounds-change cleanup should prune at least one queued tiny label");
        }
    }

    private static void assertDynamicDominanceDisabledForUnsafeCutContexts() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-e-random-n5-seeded.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);

        PricingContext withSetOutflow = PricingContext.withSetOutflowPricing(
                PricingContext.noCuts(matrices),
                List.of(SetOutflowPricingRule.of("so-dynamic-probe", List.of(1, 2, 3), -2.0)));
        if (withSetOutflow.satisfiesForwardDti() || withSetOutflow.satisfiesBackwardPti()) {
            throw new AssertionError("set-outflow pricing context must not certify DTI/PTI");
        }
        BidirectionalDynamicPricingSolver.Result setOutflowDynamic =
                new BidirectionalDynamicPricingSolver().solve(withSetOutflow);
        assertEquals("set-outflow dynamic dominated labels", 0, setOutflowDynamic.dominatedLabels());
        assertEquals("set-outflow dynamic cleanup-pruned labels",
                0, setOutflowDynamic.dominanceCleanupPrunedLabels());

        PricingContext withSubsetRow = PricingContext.withSubsetRowCuts(
                matrices,
                List.of(SubsetRowCut.ofL2Triple("sr-dynamic-probe", 1, 2, 3, -4.0)));
        BidirectionalPricingSolver.Result subsetRowStatic =
                new BidirectionalPricingSolver().solve(withSubsetRow);
        BidirectionalDynamicPricingSolver.Result subsetRowDynamic =
                new BidirectionalDynamicPricingSolver().solve(withSubsetRow);
        assertClose("SR dynamic cleanup disabled best rc",
                subsetRowStatic.bestReducedCost(), subsetRowDynamic.bestReducedCost());
        assertEquals("SR dynamic dominated labels", 0, subsetRowDynamic.dominatedLabels());
        assertEquals("SR dynamic cleanup-pruned labels", 0, subsetRowDynamic.dominanceCleanupPrunedLabels());
    }

    private static void assertPricingModeAliases() throws Exception {
        if (PricingMode.fromString("bidir-dynamic") != PricingMode.BIDIR_DYNAMIC) {
            throw new AssertionError("bidir-dynamic alias should map to BIDIR_DYNAMIC");
        }
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        PricingResult staticPricing = PricingMode.BIDIR_STATIC.createSolver(TOLERANCE).price(matrices);
        BidirectionalDynamicPricingSolver.Result dynamicDirect =
                new BidirectionalDynamicPricingSolver(TOLERANCE).solve(matrices);
        PricingResult dynamicPricing = PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE).price(matrices);
        assertClose("PricingMode dynamic best rc",
                staticPricing.bestReducedCost(), dynamicPricing.bestReducedCost());
        if (!dynamicPricing.exact()) {
            throw new AssertionError("BIDIR_DYNAMIC pricing mode must be exact");
        }
        if (dynamicPricing.stats().generatedForwardLabels() <= 0
                || dynamicPricing.stats().generatedBackwardLabels() <= 0
                || dynamicPricing.stats().merges() <= 0) {
            throw new AssertionError("BIDIR_DYNAMIC pricing mode should expose label and merge stats");
        }
        PricingResult.Stats stats = dynamicPricing.stats();
        DynamicHalfwayController.Snapshot snapshot = dynamicDirect.finalSnapshot();
        assertEquals("dynamic stats processed forward",
                snapshot.processedForwardLabels(), stats.processedForwardLabels());
        assertEquals("dynamic stats processed backward",
                snapshot.processedBackwardLabels(), stats.processedBackwardLabels());
        assertEquals("dynamic stats unprocessed forward",
                snapshot.unprocessedForwardLabels(), stats.unprocessedForwardLabels());
        assertEquals("dynamic stats unprocessed backward",
                snapshot.unprocessedBackwardLabels(), stats.unprocessedBackwardLabels());
        assertClose("dynamic stats final HB",
                snapshot.backwardLowerBound(), stats.finalBackwardLowerBound());
        assertClose("dynamic stats final HF",
                snapshot.forwardUpperBound(), stats.finalForwardUpperBound());
        assertEquals("dynamic stats dominance cleanup hook",
                dynamicDirect.dominanceCleanupTriggers(), stats.dominanceCleanupTriggers());
        assertEquals("dynamic stats cleanup-pruned labels",
                dynamicDirect.dominanceCleanupPrunedLabels(), stats.dominanceCleanupPrunedLabels());
        assertEquals("dynamic stats dominated labels",
                dynamicDirect.dominatedLabels(), stats.dominatedLabels());
        if (stats.dominanceCleanupPrunedLabels() < 0
                || stats.dominanceCleanupPrunedLabels() > stats.dominatedLabels()) {
            throw new AssertionError("BIDIR_DYNAMIC stats cleanup-pruned labels must be a dominated subset");
        }
        if (stats.finalBackwardLowerBound() > stats.finalForwardUpperBound() + TOLERANCE) {
            throw new AssertionError("BIDIR_DYNAMIC stats must expose valid HB <= HF");
        }
    }

    private static void assertDirectionTraceUsesUnprocessedCounts(
            List<DynamicHalfwayController.Decision> decisions) {
        for (DynamicHalfwayController.Decision decision : decisions) {
            DynamicHalfwayController.Snapshot before = decision.before();
            DynamicHalfwayController.Direction expected = DynamicHalfwayController.chooseDirection(
                    before.unprocessedForwardLabels(),
                    before.unprocessedBackwardLabels());
            if (decision.direction() != expected) {
                throw new AssertionError("decision should follow unprocessed-label rule expected="
                        + expected + " actual=" + decision.direction());
            }
        }
    }

    private static void assertPruneUnprocessedCounts() {
        DynamicHalfwayController controller = new DynamicHalfwayController(0.0, 100.0, 3, 2);
        DynamicHalfwayController.Snapshot afterForward =
                controller.pruneUnprocessed(DynamicHalfwayController.Direction.FORWARD, 2);
        assertEquals("prune forward unprocessed", 1, afterForward.unprocessedForwardLabels());
        assertEquals("prune forward leaves backward", 2, afterForward.unprocessedBackwardLabels());
        assertEquals("prune forward keeps generated count", 3, afterForward.generatedForwardLabels());
        if (controller.nextDirection() != DynamicHalfwayController.Direction.FORWARD) {
            throw new AssertionError("direction after forward pruning should use updated queue counts");
        }

        DynamicHalfwayController.Snapshot afterBackward =
                controller.pruneUnprocessed(DynamicHalfwayController.Direction.BACKWARD, 1);
        assertEquals("prune backward leaves forward", 1, afterBackward.unprocessedForwardLabels());
        assertEquals("prune backward unprocessed", 1, afterBackward.unprocessedBackwardLabels());
        assertEquals("prune backward keeps generated count", 2, afterBackward.generatedBackwardLabels());
        if (controller.nextDirection() != DynamicHalfwayController.Direction.FORWARD) {
            throw new AssertionError("direction after backward pruning should keep tie-forward rule");
        }

        assertThrows("prune negative count",
                () -> controller.pruneUnprocessed(DynamicHalfwayController.Direction.FORWARD, -1));
        assertThrows("prune too many forward",
                () -> controller.pruneUnprocessed(DynamicHalfwayController.Direction.FORWARD, 2));
        assertThrows("prune with DONE",
                () -> controller.pruneUnprocessed(DynamicHalfwayController.Direction.DONE, 0));
    }

    private static int countBoundChanges(List<DynamicHalfwayController.Decision> decisions) {
        int count = 0;
        for (DynamicHalfwayController.Decision decision : decisions) {
            if (Math.abs(decision.before().backwardLowerBound()
                    - decision.after().backwardLowerBound()) > TOLERANCE
                    || Math.abs(decision.before().forwardUpperBound()
                    - decision.after().forwardUpperBound()) > TOLERANCE) {
                count++;
            }
        }
        return count;
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
        Map<Integer, Double> requestDuals = new LinkedHashMap<Integer, Double>();
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

    private static Path sourcePath(String name) {
        Path[] candidates = new Path[] {
                Path.of("src", "main", "java", "org", "pdptw", "pricing", name),
                Path.of("pdptw-bcp-java-gurobi", "src", "main", "java", "org", "pdptw", "pricing", name),
                Path.of("G:\\bid\\pdptw-bcp-java-gurobi\\src\\main\\java\\org\\pdptw\\pricing", name)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate source file: " + name);
    }

    private static void assertDoesNotContain(Path source, String text, String forbidden) {
        if (text.contains(forbidden)) {
            throw new AssertionError(source + " must not use static-then-replay dependency: " + forbidden);
        }
    }

    private static void assertDirection(
            String label,
            DynamicHalfwayController.Direction expected,
            int unprocessedForward,
            int unprocessedBackward) {
        DynamicHalfwayController.Direction actual = DynamicHalfwayController.chooseDirection(
                unprocessedForward,
                unprocessedBackward);
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
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
