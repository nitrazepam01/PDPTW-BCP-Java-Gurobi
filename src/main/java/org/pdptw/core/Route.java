package org.pdptw.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Route {
    private final List<Integer> vertexIds;

    public Route(List<Integer> vertexIds) {
        if (vertexIds == null || vertexIds.isEmpty()) {
            throw new IllegalArgumentException("Route must contain at least one vertex");
        }
        this.vertexIds = Collections.unmodifiableList(new ArrayList<>(vertexIds));
    }

    public static Route of(int... ids) {
        List<Integer> route = new ArrayList<>(ids.length);
        for (int id : ids) {
            route.add(id);
        }
        return new Route(route);
    }

    public List<Integer> vertexIds() {
        return vertexIds;
    }

    public double cost(Instance instance) {
        double cost = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            cost += instance.travelCost(vertexIds.get(i), vertexIds.get(i + 1));
        }
        return cost;
    }

    public Set<Integer> servedRequests(Instance instance) {
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
        return Collections.unmodifiableSet(pickups);
    }

    @Override
    public String toString() {
        return vertexIds.toString();
    }
}
