package org.pdptw.master;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.Vertex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MasterTestSupport {
    private MasterTestSupport() {
    }

    static void assertEquals(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    static void expectThrows(Class<? extends Throwable> expected, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(message + " wrong exception: " + actual, actual);
        }
        throw new AssertionError(message + " expected exception " + expected.getName());
    }

    static Instance tinyTwoRequestInstance(double routeCost, int maxVehicles) {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.DELIVERY, 1, 3.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(4, Vertex.Type.DELIVERY, 2, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DEPOT_END, 0, 5.0, 0.0, 0.0, 100.0, 0.0, 0)
        );
        double[][] travelCost = denseMatrix(vertices.size(), 100.0);
        double[][] travelTime = denseMatrix(vertices.size(), 1.0);
        double arcCost = routeCost / 5.0;
        setRouteArcCosts(travelCost, arcCost);
        Map<Integer, Double> requestDuals = new LinkedHashMap<>();
        return new Instance(
                "master-test",
                2,
                2,
                maxVehicles,
                vertices,
                travelCost,
                travelTime,
                requestDuals,
                0.0
        );
    }

    static Route routeServingBothRequests() {
        return Route.of(0, 1, 2, 3, 4, 5);
    }

    private static double[][] denseMatrix(int size, double defaultValue) {
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i][j] = i == j ? 0.0 : defaultValue;
            }
        }
        return matrix;
    }

    private static void setRouteArcCosts(double[][] travelCost, double arcCost) {
        int[] route = {0, 1, 2, 3, 4, 5};
        for (int i = 0; i + 1 < route.length; i++) {
            travelCost[route[i]][route[i + 1]] = arcCost;
        }
    }
}
