package org.pdptw.core;

public final class RouteChecker {
    public static final String OK = "ok";
    public static final String DEPOT_SHAPE = "depot_shape";
    public static final String UNKNOWN_VERTEX = "unknown_vertex";
    public static final String REPEATED_CUSTOMER = "repeated_customer";
    public static final String DELIVERY_BEFORE_PICKUP = "delivery_before_pickup";
    public static final String CAPACITY = "capacity";
    public static final String TIME_WINDOW = "time_window";
    public static final String OPEN_REQUESTS_AT_SINK = "open_requests_at_sink";

    public Result check(Instance instance, Route route) {
        if (route.vertexIds().size() < 2) {
            return Result.infeasible(DEPOT_SHAPE, 0.0);
        }
        int first = route.vertexIds().get(0);
        int last = route.vertexIds().get(route.vertexIds().size() - 1);
        if (first != instance.startDepotId() || last != instance.endDepotId()) {
            return Result.infeasible(DEPOT_SHAPE, route.cost(instance));
        }
        for (int vertexId : route.vertexIds()) {
            if (!instance.hasVertex(vertexId)) {
                return Result.infeasible(UNKNOWN_VERTEX, 0.0);
            }
        }

        int maxVertexId = maxVertexId(instance);
        boolean maskBacked = instance.nRequests() <= 63 && maxVertexId <= 63;
        long visitedCustomerMask = 0L;
        long pickedMask = 0L;
        long deliveredMask = 0L;
        boolean[] visitedCustomers = maskBacked ? null : new boolean[maxVertexId + 1];
        boolean[] picked = maskBacked ? null : new boolean[instance.nRequests() + 1];
        boolean[] delivered = maskBacked ? null : new boolean[instance.nRequests() + 1];
        int load = 0;
        double time = Math.max(instance.vertex(first).readyTime(), 0.0);
        double cost = 0.0;

        for (int idx = 1; idx < route.vertexIds().size(); idx++) {
            int prevId = route.vertexIds().get(idx - 1);
            int currentId = route.vertexIds().get(idx);
            Vertex prev = instance.vertex(prevId);
            Vertex current = instance.vertex(currentId);
            cost += instance.travelCost(prevId, currentId);

            double arrival = time + prev.serviceTime() + instance.travelTime(prevId, currentId);
            time = Math.max(arrival, current.readyTime());
            if (time > current.dueTime() + 1e-9) {
                return Result.infeasible(TIME_WINDOW, cost);
            }

            if (current.isCustomer()) {
                if (maskBacked) {
                    long customerBit = bit(current.id());
                    if ((visitedCustomerMask & customerBit) != 0L) {
                        return Result.infeasible(REPEATED_CUSTOMER, cost);
                    }
                    visitedCustomerMask |= customerBit;
                } else {
                    if (visitedCustomers[current.id()]) {
                        return Result.infeasible(REPEATED_CUSTOMER, cost);
                    }
                    visitedCustomers[current.id()] = true;
                }
            }

            if (idx < route.vertexIds().size() - 1
                    && (current.id() == instance.startDepotId() || current.id() == instance.endDepotId())) {
                return Result.infeasible(DEPOT_SHAPE, cost);
            }

            if (current.isPickup()) {
                if (maskBacked) {
                    long requestBit = bit(current.requestId());
                    if ((pickedMask & requestBit) != 0L || (deliveredMask & requestBit) != 0L) {
                        return Result.infeasible(REPEATED_CUSTOMER, cost);
                    }
                    pickedMask |= requestBit;
                } else {
                    int requestId = current.requestId();
                    if (picked[requestId] || delivered[requestId]) {
                        return Result.infeasible(REPEATED_CUSTOMER, cost);
                    }
                    picked[requestId] = true;
                }
                load += current.demand();
            } else if (current.isDelivery()) {
                if (maskBacked) {
                    long requestBit = bit(current.requestId());
                    if ((pickedMask & requestBit) == 0L) {
                        return Result.infeasible(DELIVERY_BEFORE_PICKUP, cost);
                    }
                    if ((deliveredMask & requestBit) != 0L) {
                        return Result.infeasible(REPEATED_CUSTOMER, cost);
                    }
                    deliveredMask |= requestBit;
                } else {
                    int requestId = current.requestId();
                    if (!picked[requestId]) {
                        return Result.infeasible(DELIVERY_BEFORE_PICKUP, cost);
                    }
                    if (delivered[requestId]) {
                        return Result.infeasible(REPEATED_CUSTOMER, cost);
                    }
                    delivered[requestId] = true;
                }
                load += current.demand();
            }

            if (load < 0 || load > instance.vehicleCapacity()) {
                return Result.infeasible(CAPACITY, cost);
            }

        }

        if (maskBacked ? pickedMask != deliveredMask : hasOpenRequests(picked, delivered)) {
            return Result.infeasible(OPEN_REQUESTS_AT_SINK, cost);
        }
        return Result.feasible(cost);
    }

    public boolean isFeasible(Instance instance, Route route) {
        return check(instance, route).feasible();
    }

    public record Result(boolean feasible, String reason, double cost) {
        public static Result feasible(double cost) {
            return new Result(true, OK, cost);
        }

        public static Result infeasible(String reason, double cost) {
            return new Result(false, reason, cost);
        }
    }

    private static int maxVertexId(Instance instance) {
        int max = 0;
        for (Vertex vertex : instance.vertices()) {
            if (vertex.id() > max) {
                max = vertex.id();
            }
        }
        return max;
    }

    private static boolean hasOpenRequests(boolean[] picked, boolean[] delivered) {
        for (int requestId = 1; requestId < picked.length; requestId++) {
            if (picked[requestId] != delivered[requestId]) {
                return true;
            }
        }
        return false;
    }

    private static long bit(int id) {
        return 1L << id;
    }
}
