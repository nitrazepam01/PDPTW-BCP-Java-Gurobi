package org.pdptw.pricing;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.Vertex;
import org.pdptw.master.DualSolution;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Alpha-split reduced-cost matrices for forward and backward PDPTW pricing.
 */
public final class ReducedCostMatrices {
    public static final double FORWARD_ALPHA = 1.0;
    public static final double BACKWARD_ALPHA = 0.0;

    private final Instance instance;
    private final DualSolution duals;
    private final int dimension;

    private ReducedCostMatrices(Instance instance, DualSolution duals) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.duals = Objects.requireNonNull(duals, "duals");
        if (duals.nRequests() != instance.nRequests()) {
            throw new IllegalArgumentException("Dual request count " + duals.nRequests()
                    + " does not match instance request count " + instance.nRequests());
        }
        this.dimension = computeDimension(instance);
    }

    public static ReducedCostMatrices fromInstanceDuals(Instance instance) {
        Objects.requireNonNull(instance, "instance");
        double[] requestDuals = new double[instance.nRequests() + 1];
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            requestDuals[requestId] = instance.requestDual(requestId);
        }
        return new ReducedCostMatrices(
                instance,
                DualSolution.ofOneIndexed(requestDuals, instance.fleetDual()));
    }

    public static ReducedCostMatrices fromDualSolution(Instance instance, DualSolution duals) {
        return new ReducedCostMatrices(instance, duals);
    }

    public Instance instance() {
        return instance;
    }

    public DualSolution duals() {
        return duals;
    }

    public int dimension() {
        return dimension;
    }

    public double requestDual(int requestId) {
        return duals.requestDual(requestId);
    }

    public double fleetDual() {
        return duals.fleetDual();
    }

    public double routeCost(Route route) {
        Objects.requireNonNull(route, "route");
        return route.cost(instance);
    }

    public double routeCost(List<Integer> vertexIds) {
        Objects.requireNonNull(vertexIds, "vertexIds");
        double cost = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            cost += instance.travelCost(vertexIds.get(i), vertexIds.get(i + 1));
        }
        return cost;
    }

    public Set<Integer> servedRequests(Route route) {
        Objects.requireNonNull(route, "route");
        return route.servedRequests(instance);
    }

    public Set<Integer> servedRequests(List<Integer> vertexIds) {
        Objects.requireNonNull(vertexIds, "vertexIds");
        Set<Integer> pickups = new LinkedHashSet<>();
        Set<Integer> deliveries = new LinkedHashSet<>();
        for (int vertexId : vertexIds) {
            Vertex vertex = instance.vertex(vertexId);
            if (vertex.isPickup()) {
                pickups.add(vertex.requestId());
            } else if (vertex.isDelivery()) {
                deliveries.add(vertex.requestId());
            }
        }
        pickups.retainAll(deliveries);
        return Set.copyOf(pickups);
    }

    public double directReducedCost(Route route) {
        Objects.requireNonNull(route, "route");
        return directReducedCost(route.cost(instance), route.servedRequests(instance));
    }

    public double directReducedCost(List<Integer> vertexIds) {
        Objects.requireNonNull(vertexIds, "vertexIds");
        return directReducedCost(routeCost(vertexIds), servedRequests(vertexIds));
    }

    public double directReducedCost(double routeCost, Set<Integer> servedRequests) {
        return duals.directReducedCost(routeCost, servedRequests);
    }

    public double splitVertexDual(int vertexId, double alpha) {
        requireFinite(alpha, "alpha");
        Vertex vertex = instance.vertex(vertexId);
        if (vertexId == instance.startDepotId() || vertexId == instance.endDepotId()) {
            return duals.fleetDual();
        }
        if (vertex.isPickup()) {
            return alpha * duals.requestDual(vertex.requestId());
        }
        if (vertex.isDelivery()) {
            return (1.0 - alpha) * duals.requestDual(vertex.requestId());
        }
        return 0.0;
    }

    public double arcReducedCost(int from, int to, double alpha) {
        requireFinite(alpha, "alpha");
        return instance.travelCost(from, to)
                - 0.5 * splitVertexDual(from, alpha)
                - 0.5 * splitVertexDual(to, alpha);
    }

    public double forwardArcReducedCost(int from, int to) {
        return arcReducedCost(from, to, FORWARD_ALPHA);
    }

    public double backwardArcReducedCost(int from, int to) {
        return arcReducedCost(from, to, BACKWARD_ALPHA);
    }

    public double arcReducedCostSum(Route route, double alpha) {
        Objects.requireNonNull(route, "route");
        return arcReducedCostSum(route.vertexIds(), alpha);
    }

    public double arcReducedCostSum(List<Integer> vertexIds, double alpha) {
        Objects.requireNonNull(vertexIds, "vertexIds");
        requireFinite(alpha, "alpha");
        double reducedCost = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            reducedCost += arcReducedCost(vertexIds.get(i), vertexIds.get(i + 1), alpha);
        }
        return reducedCost;
    }

    public double forwardArcReducedCostSum(Route route) {
        return arcReducedCostSum(route, FORWARD_ALPHA);
    }

    public double forwardArcReducedCostSum(List<Integer> vertexIds) {
        return arcReducedCostSum(vertexIds, FORWARD_ALPHA);
    }

    public double backwardArcReducedCostSum(Route route) {
        return arcReducedCostSum(route, BACKWARD_ALPHA);
    }

    public double backwardArcReducedCostSum(List<Integer> vertexIds) {
        return arcReducedCostSum(vertexIds, BACKWARD_ALPHA);
    }

    public double[][] forwardArcReducedCosts() {
        return arcReducedCosts(FORWARD_ALPHA);
    }

    public double[][] backwardArcReducedCosts() {
        return arcReducedCosts(BACKWARD_ALPHA);
    }

    public double[][] arcReducedCosts(double alpha) {
        requireFinite(alpha, "alpha");
        double[][] matrix = new double[dimension][dimension];
        for (int from = 0; from < dimension; from++) {
            for (int to = 0; to < dimension; to++) {
                matrix[from][to] = instance.hasVertex(from) && instance.hasVertex(to)
                        ? arcReducedCost(from, to, alpha)
                        : Double.NaN;
            }
        }
        return matrix;
    }

    private static int computeDimension(Instance instance) {
        int maxVertexId = 0;
        for (Vertex vertex : instance.vertices()) {
            maxVertexId = Math.max(maxVertexId, vertex.id());
        }
        return maxVertexId + 1;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
