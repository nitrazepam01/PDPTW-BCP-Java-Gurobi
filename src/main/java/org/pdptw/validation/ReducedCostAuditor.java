package org.pdptw.validation;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;

import java.util.List;
import java.util.Set;

public final class ReducedCostAuditor {
    public static final double DEFAULT_TOLERANCE = 1.0e-7;

    private ReducedCostAuditor() {
    }

    public static double directReducedCost(Instance instance, Route route) {
        return directReducedCost((Object) instance, (Object) route);
    }

    public static double directReducedCost(Object instance, Object route) {
        double routeCost = ValidationReflection.routeCost(instance, route);
        double fleetDual = ValidationReflection.fleetDual(instance);
        double requestDualSum = 0.0;
        Set<Integer> servedRequests = ValidationReflection.servedRequests(instance, route);
        for (Integer request : servedRequests) {
            requestDualSum += ValidationReflection.requestDual(instance, request.intValue());
        }
        return routeCost - fleetDual - requestDualSum;
    }

    public static double directReducedCost(Object instance, List<Integer> vertexIds) {
        double routeCost = ValidationReflection.routeCostFromIds(instance, vertexIds);
        double fleetDual = ValidationReflection.fleetDual(instance);
        double requestDualSum = 0.0;
        Set<Integer> servedRequests = ValidationReflection.servedRequestsFromIds(instance, vertexIds);
        for (Integer request : servedRequests) {
            requestDualSum += ValidationReflection.requestDual(instance, request.intValue());
        }
        return routeCost - fleetDual - requestDualSum;
    }

    public static double alphaArcReducedCost(Object instance, Object route, double alpha) {
        return alphaArcReducedCost(instance, ValidationReflection.routeVertexIds(route), alpha);
    }

    public static double alphaArcReducedCost(Instance instance, Route route, double alpha) {
        return alphaArcReducedCost((Object) instance, (Object) route, alpha);
    }

    public static double alphaArcReducedCost(Object instance, List<Integer> vertexIds, double alpha) {
        double sum = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            sum += arcReducedCost(instance, vertexIds.get(i).intValue(), vertexIds.get(i + 1).intValue(), alpha);
        }
        return sum;
    }

    public static double arcReducedCost(Object instance, int from, int to, double alpha) {
        return ValidationReflection.travelCost(instance, from, to)
                - 0.5 * splitVertexDual(instance, from, alpha)
                - 0.5 * splitVertexDual(instance, to, alpha);
    }

    public static double splitVertexDual(Object instance, int vertexId, double alpha) {
        int n = ValidationReflection.nRequests(instance);
        if (vertexId == ValidationReflection.startDepotId(instance) || vertexId == ValidationReflection.endDepotId(instance)) {
            return ValidationReflection.fleetDual(instance);
        }
        if (ValidationReflection.isPickup(vertexId, n)) {
            return alpha * ValidationReflection.requestDual(instance, vertexId);
        }
        if (ValidationReflection.isDelivery(vertexId, n)) {
            int request = vertexId - n;
            return (1.0 - alpha) * ValidationReflection.requestDual(instance, request);
        }
        return 0.0;
    }

    public static void assertDirectEqualsAlpha(Object instance, Object route, double alpha) {
        assertDirectEqualsAlpha(instance, route, alpha, DEFAULT_TOLERANCE);
    }

    public static void assertDirectEqualsAlpha(Instance instance, Route route, double alpha) {
        assertDirectEqualsAlpha((Object) instance, (Object) route, alpha, DEFAULT_TOLERANCE);
    }

    public static void assertDirectEqualsAlpha(Object instance, Object route, double alpha, double tolerance) {
        double direct = directReducedCost(instance, route);
        double arcSum = alphaArcReducedCost(instance, route, alpha);
        if (Math.abs(direct - arcSum) > tolerance) {
            throw new AssertionError("direct reduced cost " + direct
                    + " differs from alpha=" + alpha + " arc sum " + arcSum);
        }
    }
}
