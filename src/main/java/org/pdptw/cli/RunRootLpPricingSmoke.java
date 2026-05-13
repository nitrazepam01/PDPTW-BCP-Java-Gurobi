package org.pdptw.cli;

import com.gurobi.gurobi.GRB;
import org.pdptw.core.Instance;
import org.pdptw.core.Request;
import org.pdptw.core.Route;
import org.pdptw.core.RouteChecker;
import org.pdptw.io.BenchmarkInstanceReader;
import org.pdptw.master.DualSolution;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunRootLpPricingSmoke {
    static final double DEFAULT_TOLERANCE = RootColumnGenerationRunner.DEFAULT_TOLERANCE;
    static final int DEFAULT_MAX_ROUTE_REQUESTS = 2;
    static final int DEFAULT_THREE_REQUEST_MAX_CANDIDATES = 5_000;
    static final int NO_CANDIDATE_LIMIT = Integer.MAX_VALUE;

    private RunRootLpPricingSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        CliSupport.requireKnownOptions(
                options,
                "root-lp-pricing-smoke",
                "instance",
                "max-route-requests",
                "max-candidate-routes",
                "max-three-request-routes",
                "tolerance");
        CliSupport.requirePositionalCount(
                options,
                "root-lp-pricing-smoke",
                CliSupport.instanceOnlyPositionalLimit(options),
                "[instance]");
        if (!options.containsKey("instance") && !options.containsKey("arg0")) {
            throw new IllegalArgumentException("root-lp-pricing-smoke requires --instance or a positional instance path");
        }

        int maxRouteRequests = parseMaxRouteRequests(options);
        int maxThreeRequestRoutes = parseMaxThreeRequestRoutes(options, maxRouteRequests);
        double tolerance = parseTolerance(options);
        Path path = CliSupport.instancePath(options, null);
        Instance instance = new BenchmarkInstanceReader().read(path);

        long start = System.nanoTime();
        ResultRow row = run(instance, path, maxRouteRequests, maxThreeRequestRoutes, tolerance, start);
        System.out.println(ResultRow.HEADER);
        System.out.println(row.toCsv());
    }

    static ResultRow run(Instance instance, Path path, int maxRouteRequests, double tolerance, long startNanos)
            throws Exception {
        return run(instance, path, maxRouteRequests, defaultMaxThreeRequestRoutes(maxRouteRequests), tolerance, startNanos);
    }

    static ResultRow run(
            Instance instance,
            Path path,
            int maxRouteRequests,
            int maxThreeRequestRoutes,
            double tolerance,
            long startNanos)
            throws Exception {
        if (maxRouteRequests < 1 || maxRouteRequests > 3) {
            throw new IllegalArgumentException("max-route-requests must be 1, 2, or 3: " + maxRouteRequests);
        }
        if (maxThreeRequestRoutes < 1) {
            throw new IllegalArgumentException("max-three-request-routes must be positive: " + maxThreeRequestRoutes);
        }
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }

        List<RouteColumn> seedColumns = restrictedSeedColumns(instance);
        List<RouteColumn> candidateColumns = candidateColumns(instance, maxRouteRequests, maxThreeRequestRoutes);

        try (GurobiRmp rmp = new GurobiRmp(instance)) {
            int seedAdded = 0;
            for (RouteColumn column : seedColumns) {
                if (rmp.addColumn(column)) {
                    seedAdded++;
                }
            }

            GurobiRmp.SolveResult solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                return new ResultRow(
                        path.getFileName().toString(),
                        BenchmarkInstanceReader.paperGroup(path),
                        BenchmarkInstanceReader.formatName(path),
                        instance.nRequests(),
                        instance.vertices().size(),
                        instance.vehicleCapacity(),
                        instance.maxVehicles(),
                        seedAdded,
                        solveStatusName(solve.status()),
                        "restricted_lp_not_optimal",
                        solve.objectiveValue(),
                        false,
                        candidateColumns.size(),
                        0,
                        Double.NaN,
                        "NA",
                        CliSupport.elapsedMs(startNanos));
            }

            DualSolution duals = rmp.dualSolution();
            Scan scan = scanCandidates(duals, candidateColumns, tolerance);
            boolean positiveArtificial = rmp.hasPositiveArtificial();
            String status = scan.negativeCandidates > 0
                    ? "restricted_lp_candidate_negative"
                    : "restricted_lp_no_candidate_negative";
            return new ResultRow(
                    path.getFileName().toString(),
                    BenchmarkInstanceReader.paperGroup(path),
                    BenchmarkInstanceReader.formatName(path),
                    instance.nRequests(),
                    instance.vertices().size(),
                    instance.vehicleCapacity(),
                    instance.maxVehicles(),
                    seedAdded,
                    solveStatusName(solve.status()),
                    status,
                    solve.objectiveValue(),
                    positiveArtificial,
                    candidateColumns.size(),
                    scan.negativeCandidates,
                    scan.bestReducedCost,
                    scan.bestRoute,
                    CliSupport.elapsedMs(startNanos));
        }
    }

    private static List<RouteColumn> singleRequestColumns(Instance instance, String namePrefix) {
        RouteChecker checker = new RouteChecker();
        ArrayList<RouteColumn> columns = new ArrayList<RouteColumn>();
        for (int requestId : instance.requestIds()) {
            Request request = instance.request(requestId);
            Route route = Route.of(
                    instance.startDepotId(),
                    request.pickupVertexId(),
                    request.deliveryVertexId(),
                    instance.endDepotId());
            if (checker.isFeasible(instance, route)) {
                columns.add(RouteColumn.fromRoute(namePrefix + "_" + requestId, route, instance));
            }
        }
        return columns;
    }

    static List<RouteColumn> restrictedSeedColumns(Instance instance) {
        LinkedHashMap<String, RouteColumn> bySignature = new LinkedHashMap<String, RouteColumn>();
        for (RouteColumn column : singleRequestColumns(instance, "seed_single")) {
            bySignature.put(column.signature(), column);
        }
        for (RouteColumn column : greedyInsertionSeedColumns(instance)) {
            bySignature.putIfAbsent(column.signature(), column);
        }
        return List.copyOf(bySignature.values());
    }

    private static List<RouteColumn> greedyInsertionSeedColumns(Instance instance) {
        RouteChecker checker = new RouteChecker();
        ArrayList<Route> routes = new ArrayList<Route>();
        for (int requestId : instance.requestIds()) {
            Request request = instance.request(requestId);
            Insertion best = bestInsertion(instance, checker, routes, request);
            if (best == null) {
                Route route = directRoute(instance, request);
                if (routes.size() < instance.maxVehicles() && checker.isFeasible(instance, route)) {
                    routes.add(route);
                }
                continue;
            }
            routes.set(best.routeIndex, best.route);
        }

        ArrayList<RouteColumn> columns = new ArrayList<RouteColumn>();
        for (int index = 0; index < routes.size(); index++) {
            Route route = routes.get(index);
            if (!route.servedRequests(instance).isEmpty()) {
                columns.add(RouteColumn.fromRoute("seed_greedy_" + index, route, instance));
            }
        }
        return columns;
    }

    private static Insertion bestInsertion(
            Instance instance,
            RouteChecker checker,
            List<Route> routes,
            Request request) {
        Insertion best = null;
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            Route base = routes.get(routeIndex);
            double baseCost = base.cost(instance);
            List<Integer> vertexIds = base.vertexIds();
            for (int pickupIndex = 1; pickupIndex < vertexIds.size(); pickupIndex++) {
                ArrayList<Integer> withPickup = new ArrayList<Integer>(vertexIds);
                withPickup.add(pickupIndex, Integer.valueOf(request.pickupVertexId()));
                for (int deliveryIndex = pickupIndex + 1; deliveryIndex < withPickup.size(); deliveryIndex++) {
                    ArrayList<Integer> candidateIds = new ArrayList<Integer>(withPickup);
                    candidateIds.add(deliveryIndex, Integer.valueOf(request.deliveryVertexId()));
                    Route candidate = new Route(candidateIds);
                    if (checker.isFeasible(instance, candidate)) {
                        double increase = candidate.cost(instance) - baseCost;
                        if (best == null || increase < best.costIncrease) {
                            best = new Insertion(routeIndex, candidate, increase);
                        }
                    }
                }
            }
        }
        return best;
    }

    private static Route directRoute(Instance instance, Request request) {
        return Route.of(
                instance.startDepotId(),
                request.pickupVertexId(),
                request.deliveryVertexId(),
                instance.endDepotId());
    }

    static List<RouteColumn> candidateColumns(Instance instance, int maxRouteRequests) {
        return candidateColumns(instance, maxRouteRequests, defaultMaxThreeRequestRoutes(maxRouteRequests));
    }

    static List<RouteColumn> candidateColumns(Instance instance, int maxRouteRequests, int maxThreeRequestRoutes) {
        if (maxRouteRequests < 1 || maxRouteRequests > 3) {
            throw new IllegalArgumentException("max-route-requests must be 1, 2, or 3: " + maxRouteRequests);
        }
        if (maxThreeRequestRoutes < 1) {
            throw new IllegalArgumentException("max-three-request-routes must be positive: "
                    + maxThreeRequestRoutes);
        }
        LinkedHashMap<String, RouteColumn> bySignature = new LinkedHashMap<String, RouteColumn>();
        for (RouteColumn column : singleRequestColumns(instance, "candidate_single")) {
            bySignature.put(column.signature(), column);
        }
        if (maxRouteRequests >= 2) {
            addTwoRequestCandidates(instance, bySignature);
        }
        if (maxRouteRequests >= 3) {
            addThreeRequestCandidates(instance, bySignature, maxThreeRequestRoutes);
        }
        return List.copyOf(bySignature.values());
    }

    private static void addTwoRequestCandidates(
            Instance instance,
            LinkedHashMap<String, RouteColumn> bySignature) {
        RouteChecker checker = new RouteChecker();
        List<Integer> requestIds = instance.requestIds();
        for (int left = 0; left < requestIds.size(); left++) {
            Request first = instance.request(requestIds.get(left).intValue());
            for (int right = left + 1; right < requestIds.size(); right++) {
                Request second = instance.request(requestIds.get(right).intValue());
                List<Route> routes = twoRequestRoutes(instance, first, second);
                for (int candidate = 0; candidate < routes.size(); candidate++) {
                    Route route = routes.get(candidate);
                    if (checker.isFeasible(instance, route)) {
                        RouteColumn column = RouteColumn.fromRoute(
                                "candidate_pair_" + first.id() + "_" + second.id() + "_" + candidate,
                                route,
                                instance);
                        bySignature.putIfAbsent(column.signature(), column);
                    }
                }
            }
        }
    }

    private static void addThreeRequestCandidates(
            Instance instance,
            LinkedHashMap<String, RouteColumn> bySignature,
            int maxThreeRequestRoutes) {
        RouteChecker checker = new RouteChecker();
        List<Integer> requestIds = instance.requestIds();
        int[] addedThreeRequestRoutes = new int[] {0};
        for (int firstIndex = 0; firstIndex < requestIds.size(); firstIndex++) {
            Request first = instance.request(requestIds.get(firstIndex).intValue());
            for (int secondIndex = firstIndex + 1; secondIndex < requestIds.size(); secondIndex++) {
                Request second = instance.request(requestIds.get(secondIndex).intValue());
                for (int thirdIndex = secondIndex + 1; thirdIndex < requestIds.size(); thirdIndex++) {
                    if (addedThreeRequestRoutes[0] >= maxThreeRequestRoutes) {
                        return;
                    }
                    Request third = instance.request(requestIds.get(thirdIndex).intValue());
                    addThreeRequestPermutations(
                            instance,
                            checker,
                            bySignature,
                            maxThreeRequestRoutes,
                            new Request[] {first, second, third},
                            addedThreeRequestRoutes);
                }
            }
        }
    }

    private static void addThreeRequestPermutations(
            Instance instance,
            RouteChecker checker,
            LinkedHashMap<String, RouteColumn> bySignature,
            int maxThreeRequestRoutes,
            Request[] requests,
            int[] addedThreeRequestRoutes) {
        ArrayList<Integer> route = new ArrayList<Integer>();
        route.add(Integer.valueOf(instance.startDepotId()));
        enumerateThreeRequestPermutations(
                instance,
                checker,
                bySignature,
                maxThreeRequestRoutes,
                requests,
                new boolean[requests.length],
                new boolean[requests.length],
                route,
                addedThreeRequestRoutes);
    }

    private static void enumerateThreeRequestPermutations(
            Instance instance,
            RouteChecker checker,
            LinkedHashMap<String, RouteColumn> bySignature,
            int maxThreeRequestRoutes,
            Request[] requests,
            boolean[] picked,
            boolean[] delivered,
            ArrayList<Integer> route,
            int[] addedThreeRequestRoutes) {
        if (addedThreeRequestRoutes[0] >= maxThreeRequestRoutes) {
            return;
        }
        if (allTrue(delivered)) {
            route.add(Integer.valueOf(instance.endDepotId()));
            Route candidateRoute = new Route(route);
            if (checker.isFeasible(instance, candidateRoute)) {
                RouteColumn column = RouteColumn.fromRoute(
                        "candidate_triple_" + requests[0].id() + "_" + requests[1].id()
                                + "_" + requests[2].id() + "_" + bySignature.size(),
                        candidateRoute,
                        instance);
                if (!bySignature.containsKey(column.signature())) {
                    bySignature.put(column.signature(), column);
                    addedThreeRequestRoutes[0]++;
                }
            }
            route.remove(route.size() - 1);
            return;
        }
        for (int index = 0; index < requests.length; index++) {
            if (!picked[index]) {
                picked[index] = true;
                route.add(Integer.valueOf(requests[index].pickupVertexId()));
                enumerateThreeRequestPermutations(
                        instance,
                        checker,
                        bySignature,
                        maxThreeRequestRoutes,
                        requests,
                        picked,
                        delivered,
                        route,
                        addedThreeRequestRoutes);
                route.remove(route.size() - 1);
                picked[index] = false;
            } else if (!delivered[index]) {
                delivered[index] = true;
                route.add(Integer.valueOf(requests[index].deliveryVertexId()));
                enumerateThreeRequestPermutations(
                        instance,
                        checker,
                        bySignature,
                        maxThreeRequestRoutes,
                        requests,
                        picked,
                        delivered,
                        route,
                        addedThreeRequestRoutes);
                route.remove(route.size() - 1);
                delivered[index] = false;
            }
        }
    }

    private static boolean allTrue(boolean[] values) {
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }

    private static List<Route> twoRequestRoutes(Instance instance, Request first, Request second) {
        int s = instance.startDepotId();
        int e = instance.endDepotId();
        int p1 = first.pickupVertexId();
        int d1 = first.deliveryVertexId();
        int p2 = second.pickupVertexId();
        int d2 = second.deliveryVertexId();
        return List.of(
                Route.of(s, p1, d1, p2, d2, e),
                Route.of(s, p2, d2, p1, d1, e),
                Route.of(s, p1, p2, d1, d2, e),
                Route.of(s, p1, p2, d2, d1, e),
                Route.of(s, p2, p1, d1, d2, e),
                Route.of(s, p2, p1, d2, d1, e));
    }

    private static Scan scanCandidates(
            DualSolution duals,
            List<RouteColumn> candidateColumns,
            double tolerance) {
        int negativeCandidates = 0;
        double bestReducedCost = Double.NaN;
        String bestRoute = "NA";
        for (RouteColumn column : candidateColumns) {
            double reducedCost = duals.directReducedCost(column);
            if (Double.isNaN(bestReducedCost) || reducedCost < bestReducedCost) {
                bestReducedCost = reducedCost;
                bestRoute = routeText(column.vertexIds());
            }
            if (reducedCost < -tolerance) {
                negativeCandidates++;
            }
        }
        return new Scan(negativeCandidates, bestReducedCost, bestRoute);
    }

    static String routeText(List<Integer> vertexIds) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < vertexIds.size(); index++) {
            if (index > 0) {
                builder.append("->");
            }
            builder.append(vertexIds.get(index));
        }
        return builder.toString();
    }

    private static int parseMaxRouteRequests(Map<String, String> options) {
        String value = CliSupport.option(options, "max-route-requests", Integer.toString(DEFAULT_MAX_ROUTE_REQUESTS));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("max-route-requests must be an integer: " + value, exception);
        }
    }

    private static int parseMaxThreeRequestRoutes(Map<String, String> options, int maxRouteRequests) {
        String value = options.get("max_three_request_routes");
        String optionName = "max-three-request-routes";
        if (value == null) {
            value = options.get("max_candidate_routes");
            optionName = "max-candidate-routes";
        }
        if (value == null) {
            return defaultMaxThreeRequestRoutes(maxRouteRequests);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(optionName + " must be an integer: " + value, exception);
        }
    }

    static int defaultMaxThreeRequestRoutes(int maxRouteRequests) {
        return maxRouteRequests >= 3 ? DEFAULT_THREE_REQUEST_MAX_CANDIDATES : NO_CANDIDATE_LIMIT;
    }

    private static double parseTolerance(Map<String, String> options) {
        String value = CliSupport.option(options, "tolerance", Double.toString(DEFAULT_TOLERANCE));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("tolerance must be a number: " + value, exception);
        }
    }

    static String solveStatusName(int status) {
        if (status == GRB.OPTIMAL) {
            return "optimal";
        }
        if (status == GRB.INFEASIBLE) {
            return "infeasible";
        }
        if (status == GRB.UNBOUNDED) {
            return "unbounded";
        }
        if (status == GRB.INF_OR_UNBD) {
            return "inf_or_unbd";
        }
        return "status_" + status;
    }

    record ResultRow(
            String instance,
            String paperGroup,
            String format,
            int requests,
            int vertices,
            int capacity,
            int maxVehicles,
            int seedColumns,
            String lpSolveStatus,
            String status,
            double lpObjective,
            boolean positiveArtificial,
            int candidateRoutes,
            int negativeCandidates,
            double bestCandidateRc,
            String bestCandidateRoute,
            long totalTimeMs) {
        static final String HEADER = "mode,instance,paperGroup,format,requests,vertices,capacity,maxVehicles,"
                + "seedColumns,lpSolveStatus,status,lpObjective,positiveArtificial,candidateRoutes,"
                + "negativeCandidates,bestCandidateRc,bestCandidateRoute,totalTimeMs";

        String toCsv() {
            return String.join(",",
                    "root-lp-pricing-smoke",
                    BenchmarkCsv.csv(instance),
                    BenchmarkCsv.csv(paperGroup),
                    BenchmarkCsv.csv(format),
                    Integer.toString(requests),
                    Integer.toString(vertices),
                    Integer.toString(capacity),
                    Integer.toString(maxVehicles),
                    Integer.toString(seedColumns),
                    BenchmarkCsv.csv(lpSolveStatus),
                    BenchmarkCsv.csv(status),
                    BenchmarkCsv.number(lpObjective),
                    Boolean.toString(positiveArtificial),
                    Integer.toString(candidateRoutes),
                    Integer.toString(negativeCandidates),
                    BenchmarkCsv.number(bestCandidateRc),
                    BenchmarkCsv.csv(bestCandidateRoute),
                    Long.toString(totalTimeMs));
        }
    }

    private record Scan(int negativeCandidates, double bestReducedCost, String bestRoute) {
    }

    private record Insertion(int routeIndex, Route route, double costIncrease) {
    }
}
