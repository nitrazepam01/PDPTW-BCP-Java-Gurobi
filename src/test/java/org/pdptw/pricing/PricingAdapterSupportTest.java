package org.pdptw.pricing;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.cuts.RobustCut;
import org.pdptw.io.TinyJsonReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PricingAdapterSupportTest {
    private static final double TOLERANCE = 1.0e-7;

    private PricingAdapterSupportTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("PricingAdapterSupportTest OK");
    }

    public static void run() throws Exception {
        assertDirectRouteReducedCostOverridesStaleNonNegativeBest();
        assertStaleNegativeBestWithoutDirectNegativeRouteFails();
        assertCallerNegativeBestCannotBeMoreNegativeThanReturnedRoutes();
        assertMatchingNegativeCallerBestIsAccepted();
        assertCutAwareDirectReducedCostControlsColumns();
    }

    private static void assertDirectRouteReducedCostOverridesStaleNonNegativeBest() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        Route route = Route.of(0, 1, 2, 3, 4, 5);

        PricingResult result = PricingAdapterSupport.exactFromRoutes(
                "adapter_audit",
                matrices,
                List.of(route.vertexIds()),
                0.0,
                TOLERANCE,
                PricingResult.Stats.empty());

        if (!result.exact()) {
            throw new AssertionError("adapter result must remain exact");
        }
        if (!PricingResult.OPTIMAL.equals(result.status())) {
            throw new AssertionError("stale non-negative best must not mask an exact negative route");
        }
        assertClose("recomputed best reduced cost", -2.0, result.bestReducedCost());
        if (!result.hasNegativeColumn(TOLERANCE)) {
            throw new AssertionError("adapter should expose the direct negative reduced-cost column");
        }
        assertEquals("negative column count", 1, result.columns().size());
        if (!route.vertexIds().equals(result.columns().get(0).vertexIds())) {
            throw new AssertionError("negative column route mismatch expected="
                    + route.vertexIds() + " actual=" + result.columns().get(0).vertexIds());
        }
    }

    private static void assertStaleNegativeBestWithoutDirectNegativeRouteFails() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        Route route = Route.of(0, 1, 3, 5);

        assertBestReducedCostMismatch(() -> PricingAdapterSupport.exactFromRoutes(
                        "adapter_audit",
                        matrices,
                        List.of(route.vertexIds()),
                        -99.0,
                        TOLERANCE,
                        PricingResult.Stats.empty()),
                "stale negative best must not be hidden by non-negative direct routes");
    }

    private static void assertCallerNegativeBestCannotBeMoreNegativeThanReturnedRoutes() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        Route route = Route.of(0, 1, 2, 3, 4, 5);

        assertBestReducedCostMismatch(() -> PricingAdapterSupport.exactFromRoutes(
                        "adapter_audit",
                        matrices,
                        List.of(route.vertexIds()),
                        -3.0,
                        TOLERANCE,
                        PricingResult.Stats.empty()),
                "caller negative best must not be more negative than returned direct routes");
    }

    private static void assertMatchingNegativeCallerBestIsAccepted() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        Route route = Route.of(0, 1, 2, 3, 4, 5);

        PricingResult result = PricingAdapterSupport.exactFromRoutes(
                "adapter_audit",
                matrices,
                List.of(route.vertexIds()),
                -2.0,
                TOLERANCE,
                PricingResult.Stats.empty());

        if (!PricingResult.OPTIMAL.equals(result.status())) {
            throw new AssertionError("matching negative caller best should be accepted");
        }
        assertClose("matching negative caller best", -2.0, result.bestReducedCost());
        assertEquals("matching negative caller column count", 1, result.columns().size());
    }

    private static void assertCutAwareDirectReducedCostControlsColumns() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        PricingContext context = PricingContext.withRobustCuts(
                matrices,
                List.of(new SingleArcRobustCut("adapter_cut_probe", 3.0, 0, 1, -1.0)));
        Route route = Route.of(0, 1, 3, 5);

        PricingResult result = PricingAdapterSupport.exactFromRoutes(
                "adapter_cut_audit",
                context,
                List.of(route.vertexIds()),
                0.0,
                TOLERANCE,
                PricingResult.Stats.empty());

        if (!PricingResult.OPTIMAL.equals(result.status())) {
            throw new AssertionError("cut-aware direct RC should create a negative column");
        }
        assertClose("cut-aware adapter best reduced cost", -2.0, result.bestReducedCost());
        assertEquals("cut-aware negative column count", 1, result.columns().size());
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

    private static void assertBestReducedCostMismatch(ThrowingRunnable runnable, String label) throws Exception {
        try {
            runnable.run();
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("best reduced-cost mismatch")) {
                throw new AssertionError(label + " threw the wrong error", expected);
            }
            return;
        }
        throw new AssertionError(label);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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
