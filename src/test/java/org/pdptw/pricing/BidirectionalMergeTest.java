package org.pdptw.pricing;

import org.pdptw.core.Instance;
import org.pdptw.io.TinyJsonReader;
import org.pdptw.validation.BruteForcePricingOracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BidirectionalMergeTest {
    private static final double TOLERANCE = 1.0e-7;

    private BidirectionalMergeTest() {
    }

    public static void main(String[] args) throws Exception {
        assertTinyADocumentedSplit();
        assertMergeAuditToleranceIsExplicit();
        assertBrokenMergeCorrectionFailsAudit();
        assertEveryOracleRouteSplit("tiny-a-wide.json");
        assertEveryOracleRouteSplit("tiny-e-random-n5-seeded.json");
        assertStaticBidirectionalBestForDualVectors("tiny-a-wide.json");
        assertStaticBidirectionalBestForDualVectors("tiny-e-random-n5-seeded.json");
        assertBidirectionalPricingStats("tiny-a-wide.json");
        System.out.println("BidirectionalMergeTest OK");
    }

    public static void run() throws Exception {
        main(new String[0]);
    }

    private static void assertTinyADocumentedSplit() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        LabelIndex labels = buildLabelIndex(matrices);
        ForwardLabel forward = labels.forwardByRoute.get(ints(0, 1, 2));
        BackwardLabel backward = labels.backwardByRoute.get(ints(2, 3, 4, 5));
        if (forward == null || backward == null) {
            throw new AssertionError("missing Tiny-A documented split labels");
        }

        BidirectionalMerger.MergeResult merge = new BidirectionalMerger()
                .merge(matrices, forward, backward)
                .orElseThrow(() -> new AssertionError("Tiny-A documented split should merge"));
        assertList("Tiny-A documented merged route", ints(0, 1, 2, 3, 4, 5), merge.route());
        assertClose("Tiny-A documented merged rc", -2.0, merge.mergedReducedCost());
        assertClose("Tiny-A documented direct rc", -2.0, merge.directReducedCost());
        if (Math.abs(merge.rawReducedCost() - merge.mergedReducedCost()) < TOLERANCE) {
            throw new AssertionError("Tiny-A documented split should require the no-cut merge correction");
        }
    }

    private static void assertMergeAuditToleranceIsExplicit() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        LabelIndex labels = buildLabelIndex(matrices);
        ForwardLabel forward = labels.forwardByRoute.get(ints(0, 1, 2));
        BackwardLabel backward = labels.backwardByRoute.get(ints(2, 3, 4, 5));
        if (forward == null || backward == null) {
            throw new AssertionError("missing Tiny-A labels for merge audit tolerance test");
        }

        double offset = 1.0e-4;
        ForwardLabel shiftedForward = new ForwardLabel(
                forward.lastVertexId(),
                forward.reducedCost() + offset,
                forward.time(),
                forward.load(),
                forward.completedMask(),
                forward.openMask(),
                forward.vertexIds());

        BidirectionalMerger.MergeResult accepted = new BidirectionalMerger(offset * 2.0)
                .merge(matrices, shiftedForward, backward)
                .orElseThrow(() -> new AssertionError("loose merge audit tolerance should accept the split"));
        assertClose("loose merge audit preserves shifted error",
                offset,
                accepted.mergedReducedCost() - accepted.directReducedCost());

        assertThrows("strict merge audit rejects shifted reduced cost",
                () -> new BidirectionalMerger(offset / 2.0).merge(matrices, shiftedForward, backward));
        assertThrows("negative merge audit tolerance",
                () -> new BidirectionalMerger(-1.0));
    }

    private static void assertBrokenMergeCorrectionFailsAudit() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        LabelIndex labels = buildLabelIndex(matrices);
        ForwardLabel forward = labels.forwardByRoute.get(ints(0, 1, 2));
        BackwardLabel backward = labels.backwardByRoute.get(ints(2, 3, 4, 5));
        if (forward == null || backward == null) {
            throw new AssertionError("missing Tiny-A labels for broken merge correction audit test");
        }

        assertThrows("broken merge correction rejected",
                () -> new BidirectionalMerger().merge(
                        matrices,
                        forward,
                        backward,
                        (activeMatrices, activeForward, activeBackward) -> 0.0,
                        matrices::directReducedCost));
    }

    private static void assertEveryOracleRouteSplit(String fixtureName) throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath(fixtureName));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        LabelIndex labels = buildLabelIndex(matrices);
        BidirectionalMerger merger = new BidirectionalMerger();
        BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);

        int checkedSplits = 0;
        for (BruteForcePricingOracle.RouteEvaluation route : oracle.routes()) {
            List<Integer> vertexIds = route.vertexIds();
            for (int split = 0; split < vertexIds.size(); split++) {
                List<Integer> prefix = new ArrayList<Integer>(vertexIds.subList(0, split + 1));
                List<Integer> suffix = new ArrayList<Integer>(vertexIds.subList(split, vertexIds.size()));
                ForwardLabel forward = labels.forwardByRoute.get(prefix);
                BackwardLabel backward = labels.backwardByRoute.get(suffix);
                if (forward == null || backward == null) {
                    throw new AssertionError(fixtureName + " missing labels for split route="
                            + vertexIds + " split=" + split + " prefix=" + prefix + " suffix=" + suffix);
                }

                String mergeError = fixtureName + " split should be merge-compatible route="
                        + vertexIds + " split=" + split;
                BidirectionalMerger.MergeResult merge = merger.merge(matrices, forward, backward)
                        .orElseThrow(() -> new AssertionError(mergeError));
                assertList(fixtureName + " reconstructed route", vertexIds, merge.route());
                assertClose(fixtureName + " merged rc equals direct route rc",
                        matrices.directReducedCost(vertexIds), merge.mergedReducedCost());
                assertClose(fixtureName + " merge direct rc equals matrix direct rc",
                        matrices.directReducedCost(vertexIds), merge.directReducedCost());
                checkedSplits++;
            }
        }
        if (checkedSplits == 0) {
            throw new AssertionError(fixtureName + " did not check any route split");
        }
    }

    private static void assertStaticBidirectionalBestForDualVectors(String fixtureName) throws Exception {
        Instance base = new TinyJsonReader().read(referencePath(fixtureName));
        for (DualVector dualVector : dualVectors(base)) {
            Instance instance = withDuals(base, dualVector);
            ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
            BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
            ForwardLabeler.Result forward = new ForwardLabeler().solve(matrices);
            BackwardLabeler.Result backward = new BackwardLabeler().solve(matrices);
            BidirectionalPricingSolver.Result bidirectional = new BidirectionalPricingSolver().solve(matrices);

            assertClose(fixtureName + " " + dualVector.name + " forward best rc",
                    oracle.bestReducedCost(), forward.bestReducedCost());
            assertClose(fixtureName + " " + dualVector.name + " backward best rc",
                    oracle.bestReducedCost(), backward.bestReducedCost());
            assertClose(fixtureName + " " + dualVector.name + " bidirectional best rc",
                    oracle.bestReducedCost(), bidirectional.bestReducedCost());
            if (bidirectional.bestMerge() == null) {
                throw new AssertionError(fixtureName + " " + dualVector.name + " missing bidirectional best merge");
            }
            assertClose(fixtureName + " " + dualVector.name + " bidirectional direct audit",
                    bidirectional.bestMerge().directReducedCost(), bidirectional.bestReducedCost());
        }
    }

    private static void assertBidirectionalPricingStats(String fixtureName) throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath(fixtureName));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        BidirectionalPricingSolver.Result direct = new BidirectionalPricingSolver().solve(matrices);
        PricingResult pricing = new BidirectionalStaticPricingSolver(TOLERANCE).price(matrices);
        PricingResult.Stats stats = pricing.stats();

        assertEquals("static stats generated forward",
                direct.forwardResult().partialLabels().size() + direct.forwardResult().completeLabels().size(),
                stats.generatedForwardLabels());
        assertEquals("static stats generated backward",
                direct.backwardResult().partialLabels().size() + direct.backwardResult().completeLabels().size(),
                stats.generatedBackwardLabels());
        assertEquals("static stats complete forward",
                direct.forwardResult().completeLabels().size(),
                stats.completeForwardLabels());
        assertEquals("static stats complete backward",
                direct.backwardResult().completeLabels().size(),
                stats.completeBackwardLabels());
        assertEquals("static stats complete total",
                stats.completeForwardLabels() + stats.completeBackwardLabels(),
                stats.completeLabels());
        assertEquals("static stats merges", direct.merges().size(), stats.merges());
        assertEquals("static stats dominated labels", 0, stats.dominatedLabels());
    }

    private static LabelIndex buildLabelIndex(ReducedCostMatrices matrices) {
        ForwardLabeler.Result forward = new ForwardLabeler().solve(matrices);
        BackwardLabeler.Result backward = new BackwardLabeler().solve(matrices);
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

    private static List<Integer> ints(Integer... values) {
        return Arrays.asList(values);
    }

    private record LabelIndex(
            Map<List<Integer>, ForwardLabel> forwardByRoute,
            Map<List<Integer>, BackwardLabel> backwardByRoute) {
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
