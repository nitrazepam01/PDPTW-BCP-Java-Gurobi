package org.pdptw.validation;

import org.pdptw.core.Instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BruteForcePricingOracle {
    private static final double EPS = 1.0e-9;

    public Result solve(Instance instance) {
        return solve((Object) instance);
    }

    public Result solve(Object instance) {
        List<RouteEvaluation> routes = enumerate(instance);
        RouteEvaluation best = null;
        for (RouteEvaluation route : routes) {
            if (best == null || isBetter(route, best)) {
                best = route;
            }
        }
        return new Result(routes.size(), best, routes);
    }

    public static Result price(Object instance) {
        return new BruteForcePricingOracle().solve(instance);
    }

    public List<RouteEvaluation> enumerate(Object instance) {
        ProblemData data = new ProblemData(instance);
        if (data.nRequests > 6) {
            throw new IllegalArgumentException("BruteForcePricingOracle is intended only for n <= 6, got "
                    + data.nRequests);
        }

        ArrayList<RouteEvaluation> routes = new ArrayList<RouteEvaluation>();
        ArrayList<Integer> prefix = new ArrayList<Integer>();
        prefix.add(Integer.valueOf(data.startDepotId));
        boolean[] picked = new boolean[data.nRequests + 1];
        boolean[] delivered = new boolean[data.nRequests + 1];

        double startTime = Math.max(0.0, data.ready[data.startDepotId]);
        if (startTime <= data.due[data.startDepotId] + EPS) {
            dfs(data, prefix, startTime + data.service[data.startDepotId], 0.0, picked, delivered, 0, 0, routes);
        }
        return Collections.unmodifiableList(routes);
    }

    public List<RouteEvaluation> enumerate(Instance instance) {
        return enumerate((Object) instance);
    }

    public RouteEvaluation evaluate(Instance instance, List<Integer> vertexIds) {
        return evaluate((Object) instance, vertexIds);
    }

    public RouteEvaluation evaluate(Object instance, List<Integer> vertexIds) {
        return evaluate(new ProblemData(instance), vertexIds);
    }

    public RouteEvaluation evaluate(Instance instance, int... vertexIds) {
        return evaluate((Object) instance, vertexIds);
    }

    public RouteEvaluation evaluate(Object instance, int... vertexIds) {
        ArrayList<Integer> route = new ArrayList<Integer>();
        for (int vertexId : vertexIds) {
            route.add(Integer.valueOf(vertexId));
        }
        return evaluate(instance, route);
    }

    private void dfs(
            ProblemData data,
            ArrayList<Integer> prefix,
            double currentTime,
            double currentLoad,
            boolean[] picked,
            boolean[] delivered,
            int pickedCount,
            int deliveredCount,
            ArrayList<RouteEvaluation> routes) {

        int last = prefix.get(prefix.size() - 1).intValue();
        if (deliveredCount > 0 && pickedCount == deliveredCount) {
            Step endStep = data.extend(last, data.endDepotId, currentTime, currentLoad);
            if (endStep != null) {
                ArrayList<Integer> complete = new ArrayList<Integer>(prefix);
                complete.add(Integer.valueOf(data.endDepotId));
                RouteEvaluation evaluation = evaluate(data, complete);
                if (evaluation.feasible()) {
                    routes.add(evaluation);
                }
            }
        }

        for (int request = 1; request <= data.nRequests; request++) {
            if (!picked[request]) {
                int pickup = request;
                Step step = data.extend(last, pickup, currentTime, currentLoad);
                if (step != null) {
                    picked[request] = true;
                    prefix.add(Integer.valueOf(pickup));
                    dfs(data, prefix, step.time, step.load, picked, delivered,
                            pickedCount + 1, deliveredCount, routes);
                    prefix.remove(prefix.size() - 1);
                    picked[request] = false;
                }
            }

            if (picked[request] && !delivered[request]) {
                int delivery = data.nRequests + request;
                Step step = data.extend(last, delivery, currentTime, currentLoad);
                if (step != null) {
                    delivered[request] = true;
                    prefix.add(Integer.valueOf(delivery));
                    dfs(data, prefix, step.time, step.load, picked, delivered,
                            pickedCount, deliveredCount + 1, routes);
                    prefix.remove(prefix.size() - 1);
                    delivered[request] = false;
                }
            }
        }
    }

    private static RouteEvaluation evaluate(ProblemData data, List<Integer> vertexIds) {
        if (vertexIds == null || vertexIds.size() < 2) {
            return RouteEvaluation.infeasible(vertexIds, "depot");
        }
        if (vertexIds.get(0).intValue() != data.startDepotId
                || vertexIds.get(vertexIds.size() - 1).intValue() != data.endDepotId) {
            return RouteEvaluation.infeasible(vertexIds, "depot");
        }

        boolean[] visitedCustomer = new boolean[data.maxVertexId + 1];
        boolean[] picked = new boolean[data.nRequests + 1];
        boolean[] delivered = new boolean[data.nRequests + 1];
        double time = Math.max(0.0, data.ready[data.startDepotId]);
        if (time > data.due[data.startDepotId] + EPS) {
            return RouteEvaluation.infeasible(vertexIds, "time-window");
        }
        time += data.service[data.startDepotId];
        double load = 0.0;
        double cost = 0.0;

        for (int position = 1; position < vertexIds.size(); position++) {
            int previous = vertexIds.get(position - 1).intValue();
            int current = vertexIds.get(position).intValue();
            if (current < 0 || current > data.maxVertexId) {
                return RouteEvaluation.infeasible(vertexIds, "unknown-vertex");
            }
            if (position < vertexIds.size() - 1 && data.isDepot(current)) {
                return RouteEvaluation.infeasible(vertexIds, "depot");
            }

            cost += data.travelCost[previous][current];
            double arrival = time + data.travelTime[previous][current];
            double serviceStart = Math.max(arrival, data.ready[current]);
            if (serviceStart > data.due[current] + EPS) {
                return RouteEvaluation.infeasible(vertexIds, "time-window");
            }
            double nextLoad = load + data.demand[current];

            if (current != data.endDepotId) {
                if (visitedCustomer[current]) {
                    return RouteEvaluation.infeasible(vertexIds, "elementary");
                }
                visitedCustomer[current] = true;

                if (data.isPickup(current)) {
                    picked[current] = true;
                } else if (data.isDelivery(current)) {
                    int request = data.requestOf(current);
                    if (!picked[request]) {
                        return RouteEvaluation.infeasible(vertexIds, "precedence");
                    }
                    delivered[request] = true;
                } else {
                    return RouteEvaluation.infeasible(vertexIds, "unknown-vertex");
                }
            }

            if (nextLoad < -EPS || nextLoad > data.vehicleCapacity + EPS) {
                return RouteEvaluation.infeasible(vertexIds, "capacity");
            }
            time = serviceStart + data.service[current];
            load = nextLoad;
        }

        ArrayList<Integer> served = new ArrayList<Integer>();
        for (int request = 1; request <= data.nRequests; request++) {
            if (picked[request] && !delivered[request]) {
                return RouteEvaluation.infeasible(vertexIds, "pairing");
            }
            if (delivered[request]) {
                served.add(Integer.valueOf(request));
            }
        }
        if (served.isEmpty()) {
            return RouteEvaluation.infeasible(vertexIds, "empty");
        }
        double reducedCost = cost - data.fleetDual;
        for (Integer request : served) {
            reducedCost -= data.requestDual[request.intValue()];
        }
        return RouteEvaluation.feasible(vertexIds, served, cost, reducedCost, time);
    }

    private static boolean isBetter(RouteEvaluation candidate, RouteEvaluation incumbent) {
        if (candidate.reducedCost() < incumbent.reducedCost() - EPS) {
            return true;
        }
        if (Math.abs(candidate.reducedCost() - incumbent.reducedCost()) > EPS) {
            return false;
        }
        if (candidate.cost() < incumbent.cost() - EPS) {
            return true;
        }
        if (Math.abs(candidate.cost() - incumbent.cost()) > EPS) {
            return false;
        }
        if (candidate.vertexIds().size() != incumbent.vertexIds().size()) {
            return candidate.vertexIds().size() < incumbent.vertexIds().size();
        }
        return lexicographicLess(candidate.vertexIds(), incumbent.vertexIds());
    }

    private static boolean lexicographicLess(List<Integer> left, List<Integer> right) {
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            int leftValue = left.get(i).intValue();
            int rightValue = right.get(i).intValue();
            if (leftValue != rightValue) {
                return leftValue < rightValue;
            }
        }
        return left.size() < right.size();
    }

    public static final class Result {
        private final int enumeratedFeasibleNonEmptyRoutes;
        private final RouteEvaluation best;
        private final List<RouteEvaluation> routes;

        private Result(int enumeratedFeasibleNonEmptyRoutes, RouteEvaluation best, List<RouteEvaluation> routes) {
            this.enumeratedFeasibleNonEmptyRoutes = enumeratedFeasibleNonEmptyRoutes;
            this.best = best;
            this.routes = Collections.unmodifiableList(new ArrayList<RouteEvaluation>(routes));
        }

        public int enumeratedFeasibleNonEmptyRoutes() {
            return enumeratedFeasibleNonEmptyRoutes;
        }

        public int count() {
            return enumeratedFeasibleNonEmptyRoutes;
        }

        public RouteEvaluation best() {
            return best;
        }

        public List<Integer> bestRoute() {
            return best == null ? Collections.<Integer>emptyList() : best.vertexIds();
        }

        public double bestCost() {
            return best == null ? Double.NaN : best.cost();
        }

        public double bestReducedCost() {
            return best == null ? Double.NaN : best.reducedCost();
        }

        public List<Integer> bestServedRequests() {
            return best == null ? Collections.<Integer>emptyList() : best.servedRequests();
        }

        public List<RouteEvaluation> routes() {
            return routes;
        }
    }

    public static final class RouteEvaluation {
        private final List<Integer> vertexIds;
        private final List<Integer> servedRequests;
        private final boolean feasible;
        private final String reason;
        private final double cost;
        private final double reducedCost;
        private final double completionTime;

        private RouteEvaluation(
                List<Integer> vertexIds,
                List<Integer> servedRequests,
                boolean feasible,
                String reason,
                double cost,
                double reducedCost,
                double completionTime) {
            this.vertexIds = copyList(vertexIds);
            this.servedRequests = copyList(servedRequests);
            this.feasible = feasible;
            this.reason = reason;
            this.cost = cost;
            this.reducedCost = reducedCost;
            this.completionTime = completionTime;
        }

        private static RouteEvaluation feasible(
                List<Integer> vertexIds,
                List<Integer> servedRequests,
                double cost,
                double reducedCost,
                double completionTime) {
            return new RouteEvaluation(vertexIds, servedRequests, true, "ok", cost, reducedCost, completionTime);
        }

        private static RouteEvaluation infeasible(List<Integer> vertexIds, String reason) {
            return new RouteEvaluation(vertexIds, Collections.<Integer>emptyList(), false, reason,
                    Double.NaN, Double.NaN, Double.NaN);
        }

        public List<Integer> vertexIds() {
            return vertexIds;
        }

        public List<Integer> route() {
            return vertexIds;
        }

        public List<Integer> servedRequests() {
            return servedRequests;
        }

        public boolean feasible() {
            return feasible;
        }

        public String reason() {
            return reason;
        }

        public double cost() {
            return cost;
        }

        public double reducedCost() {
            return reducedCost;
        }

        public double completionTime() {
            return completionTime;
        }

        private static List<Integer> copyList(List<Integer> source) {
            if (source == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<Integer>(source));
        }
    }

    private static final class ProblemData {
        private final int nRequests;
        private final int startDepotId;
        private final int endDepotId;
        private final int maxVertexId;
        private final double vehicleCapacity;
        private final double fleetDual;
        private final double[] requestDual;
        private final double[][] travelCost;
        private final double[][] travelTime;
        private final double[] ready;
        private final double[] due;
        private final double[] service;
        private final double[] demand;

        private ProblemData(Object instance) {
            this.nRequests = ValidationReflection.nRequests(instance);
            this.startDepotId = ValidationReflection.startDepotId(instance);
            this.endDepotId = ValidationReflection.endDepotId(instance);
            this.maxVertexId = Math.max(Math.max(endDepotId, 2 * nRequests + 1), startDepotId);
            this.vehicleCapacity = ValidationReflection.vehicleCapacity(instance);
            ValidationReflection.maxVehicles(instance);
            this.fleetDual = ValidationReflection.fleetDual(instance);
            this.requestDual = new double[nRequests + 1];
            for (int request = 1; request <= nRequests; request++) {
                requestDual[request] = ValidationReflection.requestDual(instance, request);
            }
            this.travelCost = new double[maxVertexId + 1][maxVertexId + 1];
            this.travelTime = new double[maxVertexId + 1][maxVertexId + 1];
            this.ready = new double[maxVertexId + 1];
            this.due = new double[maxVertexId + 1];
            this.service = new double[maxVertexId + 1];
            this.demand = new double[maxVertexId + 1];
            for (int id = 0; id <= maxVertexId; id++) {
                ready[id] = ValidationReflection.vertexReady(instance, id);
                due[id] = ValidationReflection.vertexDue(instance, id);
                service[id] = ValidationReflection.vertexService(instance, id);
                demand[id] = ValidationReflection.vertexDemand(instance, id, nRequests);
                for (int other = 0; other <= maxVertexId; other++) {
                    travelCost[id][other] = ValidationReflection.travelCost(instance, id, other);
                    travelTime[id][other] = ValidationReflection.travelTime(instance, id, other);
                }
            }
        }

        private Step extend(int from, int to, double currentTime, double currentLoad) {
            double arrival = currentTime + travelTime[from][to];
            double startService = Math.max(arrival, ready[to]);
            if (startService > due[to] + EPS) {
                return null;
            }
            double nextLoad = currentLoad + demand[to];
            if (nextLoad < -EPS || nextLoad > vehicleCapacity + EPS) {
                return null;
            }
            return new Step(startService + service[to], nextLoad);
        }

        private boolean isDepot(int vertexId) {
            return vertexId == startDepotId || vertexId == endDepotId;
        }

        private boolean isPickup(int vertexId) {
            return ValidationReflection.isPickup(vertexId, nRequests);
        }

        private boolean isDelivery(int vertexId) {
            return ValidationReflection.isDelivery(vertexId, nRequests);
        }

        private int requestOf(int vertexId) {
            return ValidationReflection.requestOf(vertexId, nRequests);
        }
    }

    private static final class Step {
        private final double time;
        private final double load;

        private Step(double time, double load) {
            this.time = time;
            this.load = load;
        }
    }
}
