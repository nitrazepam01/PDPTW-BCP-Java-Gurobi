package org.pdptw.validation;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.RouteChecker;
import org.pdptw.io.TinyJsonReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class BruteForcePricingOraclePlainTest {
    private BruteForcePricingOraclePlainTest() {
    }

    public static void main(String[] args) throws Exception {
        Instance tinyA = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        BruteForcePricingOracle.Result tinyAResult = new BruteForcePricingOracle().solve(tinyA);
        assertList("Tiny-A best route", ints(0, 1, 2, 3, 4, 5), tinyAResult.bestRoute());
        assertClose("Tiny-A best reduced cost", -2.0, tinyAResult.bestReducedCost());

        Instance instance = new TinyJsonReader().read(referencePath("tiny-e-random-n5-seeded.json"));
        BruteForcePricingOracle oracle = new BruteForcePricingOracle();
        RouteChecker checker = new RouteChecker();

        BruteForcePricingOracle.Result result = oracle.solve(instance);
        assertEquals("enumerated feasible non-empty routes", 3287, result.enumeratedFeasibleNonEmptyRoutes());
        assertList("best route", ints(0, 1, 6, 11), result.bestRoute());
        assertList("best served requests", ints(1), result.bestServedRequests());
        assertClose("best cost", 12.0, result.bestCost());
        assertClose("best reduced cost", 1.0, result.bestReducedCost());
        assertRouteCheckerAcceptsAll(instance, checker, result.routes());

        assertRoute(oracle.evaluate(instance, ints(0, 1, 6, 11)), true, 12.0, 1.0, ints(1), "ok");
        assertRoute(oracle.evaluate(instance, ints(0, 1, 6, 3, 8, 11)), true, 24.0, 4.0, ints(1, 3), "ok");
        assertRoute(oracle.evaluate(instance, ints(0, 1, 6, 3, 8, 2, 7, 11)), true, 32.0, 5.0,
                ints(1, 2, 3), "ok");

        assertInfeasible(oracle.evaluate(instance, ints(0, 1, 2, 3, 6, 7, 8, 11)), "capacity");
        assertInfeasible(oracle.evaluate(instance, ints(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)), "capacity");
        assertRouteCheckerReason(instance, checker, ints(0, 1, 2, 3, 6, 7, 8, 11), RouteChecker.CAPACITY);

        System.out.println("BruteForcePricingOraclePlainTest OK");
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

    private static void assertRouteCheckerAcceptsAll(
            Instance instance,
            RouteChecker checker,
            List<BruteForcePricingOracle.RouteEvaluation> routes) {
        for (BruteForcePricingOracle.RouteEvaluation route : routes) {
            RouteChecker.Result result = checker.check(instance, new Route(route.vertexIds()));
            if (!result.feasible()) {
                throw new AssertionError("RouteChecker rejected oracle route " + route.vertexIds()
                        + " reason=" + result.reason());
            }
        }
    }

    private static void assertRouteCheckerReason(
            Instance instance,
            RouteChecker checker,
            List<Integer> vertexIds,
            String reason) {
        RouteChecker.Result result = checker.check(instance, new Route(vertexIds));
        if (result.feasible()) {
            throw new AssertionError("RouteChecker expected infeasible route, got feasible " + vertexIds);
        }
        if (!reason.equals(result.reason())) {
            throw new AssertionError("RouteChecker reason expected " + reason + " but got " + result.reason());
        }
    }

    private static void assertRoute(
            BruteForcePricingOracle.RouteEvaluation evaluation,
            boolean feasible,
            double cost,
            double reducedCost,
            List<Integer> servedRequests,
            String reason) {
        if (evaluation.feasible() != feasible) {
            throw new AssertionError("feasible expected " + feasible + " but got " + evaluation.feasible()
                    + " reason=" + evaluation.reason());
        }
        if (!reason.equals(evaluation.reason())) {
            throw new AssertionError("reason expected " + reason + " but got " + evaluation.reason());
        }
        assertClose("cost", cost, evaluation.cost());
        assertClose("reduced cost", reducedCost, evaluation.reducedCost());
        assertList("served requests", servedRequests, evaluation.servedRequests());
    }

    private static void assertInfeasible(BruteForcePricingOracle.RouteEvaluation evaluation, String reason) {
        if (evaluation.feasible()) {
            throw new AssertionError("expected infeasible route, got feasible " + evaluation.vertexIds());
        }
        if (!reason.equals(evaluation.reason())) {
            throw new AssertionError("reason expected " + reason + " but got " + evaluation.reason());
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > 1.0e-7) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertList(String label, List<Integer> expected, List<Integer> actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static List<Integer> ints(Integer... values) {
        return Arrays.asList(values);
    }
}
