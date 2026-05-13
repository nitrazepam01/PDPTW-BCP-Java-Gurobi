package org.pdptw.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Instance {
    private final String name;
    private final int nRequests;
    private final int vehicleCapacity;
    private final int maxVehicles;
    private final int startDepotId;
    private final int endDepotId;
    private final Map<Integer, Vertex> vertices;
    private final Map<Integer, Request> requests;
    private final double[][] travelCost;
    private final double[][] travelTime;
    private final Map<Integer, Double> requestDuals;
    private final double fleetDual;

    public Instance(
            String name,
            int nRequests,
            int vehicleCapacity,
            int maxVehicles,
            List<Vertex> vertices,
            double[][] travelCost,
            double[][] travelTime,
            Map<Integer, Double> requestDuals,
            double fleetDual) {
        this.name = name;
        this.nRequests = nRequests;
        this.vehicleCapacity = vehicleCapacity;
        this.maxVehicles = maxVehicles;
        this.travelCost = copyMatrix(travelCost);
        this.travelTime = copyMatrix(travelTime);
        this.requestDuals = Collections.unmodifiableMap(new LinkedHashMap<>(requestDuals));
        this.fleetDual = fleetDual;

        Map<Integer, Vertex> vertexMap = new LinkedHashMap<>();
        int start = -1;
        int end = -1;
        for (Vertex vertex : vertices) {
            vertexMap.put(vertex.id(), vertex);
            if (vertex.type() == Vertex.Type.DEPOT_START) {
                start = vertex.id();
            } else if (vertex.type() == Vertex.Type.DEPOT_END) {
                end = vertex.id();
            }
        }
        this.vertices = Collections.unmodifiableMap(vertexMap);
        this.startDepotId = start;
        this.endDepotId = end;
        this.requests = Collections.unmodifiableMap(buildRequests(vertexMap, nRequests));
        validate();
    }

    private static Map<Integer, Request> buildRequests(Map<Integer, Vertex> vertices, int nRequests) {
        Map<Integer, Integer> pickups = new HashMap<>();
        Map<Integer, Integer> deliveries = new HashMap<>();
        Map<Integer, Integer> demands = new HashMap<>();
        for (Vertex vertex : vertices.values()) {
            if (vertex.isPickup()) {
                pickups.put(vertex.requestId(), vertex.id());
                demands.put(vertex.requestId(), vertex.demand());
            } else if (vertex.isDelivery()) {
                deliveries.put(vertex.requestId(), vertex.id());
            }
        }
        Map<Integer, Request> requests = new LinkedHashMap<>();
        for (int request = 1; request <= nRequests; request++) {
            Integer pickup = pickups.get(request);
            Integer delivery = deliveries.get(request);
            if (pickup == null || delivery == null) {
                throw new IllegalArgumentException("Missing pickup or delivery for request " + request);
            }
            requests.put(request, new Request(request, pickup, delivery, demands.getOrDefault(request, 0)));
        }
        return requests;
    }

    private static double[][] copyMatrix(double[][] matrix) {
        double[][] copy = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = matrix[i].clone();
        }
        return copy;
    }

    private void validate() {
        if (startDepotId < 0 || endDepotId < 0) {
            throw new IllegalArgumentException("Instance must have one start and one end depot");
        }
        int size = vertices.size();
        if (travelCost.length != size || travelTime.length != size) {
            throw new IllegalArgumentException("Travel matrices must match vertex count");
        }
        for (int i = 0; i < size; i++) {
            if (travelCost[i].length != size || travelTime[i].length != size) {
                throw new IllegalArgumentException("Travel matrices must be square");
            }
        }
    }

    public String name() {
        return name;
    }

    public int nRequests() {
        return nRequests;
    }

    public int vehicleCapacity() {
        return vehicleCapacity;
    }

    public int maxVehicles() {
        return maxVehicles;
    }

    public int startDepotId() {
        return startDepotId;
    }

    public int endDepotId() {
        return endDepotId;
    }

    public Vertex vertex(int id) {
        Vertex vertex = vertices.get(id);
        if (vertex == null) {
            throw new IllegalArgumentException("Unknown vertex id: " + id);
        }
        return vertex;
    }

    public boolean hasVertex(int id) {
        return vertices.containsKey(id);
    }

    public List<Vertex> vertices() {
        return List.copyOf(vertices.values());
    }

    public Request request(int id) {
        Request request = requests.get(id);
        if (request == null) {
            throw new IllegalArgumentException("Unknown request id: " + id);
        }
        return request;
    }

    public List<Integer> requestIds() {
        return new ArrayList<>(requests.keySet());
    }

    public double travelCost(int from, int to) {
        return travelCost[from][to];
    }

    public double travelTime(int from, int to) {
        return travelTime[from][to];
    }

    public Map<Integer, Double> requestDuals() {
        return requestDuals;
    }

    public double requestDual(int requestId) {
        return requestDuals.getOrDefault(requestId, 0.0);
    }

    public double fleetDual() {
        return fleetDual;
    }
}
