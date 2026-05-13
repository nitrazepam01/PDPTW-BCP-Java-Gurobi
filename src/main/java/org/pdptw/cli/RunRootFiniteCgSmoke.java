package org.pdptw.cli;

import org.pdptw.core.Instance;
import org.pdptw.io.BenchmarkInstanceReader;
import org.pdptw.master.DualSolution;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RunRootFiniteCgSmoke {
    static final double DEFAULT_TOLERANCE = RootColumnGenerationRunner.DEFAULT_TOLERANCE;
    static final int DEFAULT_MAX_ROUTE_REQUESTS = RunRootLpPricingSmoke.DEFAULT_MAX_ROUTE_REQUESTS;
    static final int DEFAULT_MAX_ITERATIONS = 50;
    static final int DEFAULT_MAX_COLUMNS_PER_ITERATION = 100;

    private RunRootFiniteCgSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        CliSupport.requireKnownOptions(
                options,
                "root-finite-cg-smoke",
                "instance",
                "max-route-requests",
                "max-candidate-routes",
                "max-three-request-routes",
                "max-iterations",
                "max-columns-per-iteration",
                "tolerance");
        CliSupport.requirePositionalCount(
                options,
                "root-finite-cg-smoke",
                CliSupport.instanceOnlyPositionalLimit(options),
                "[instance]");
        if (!options.containsKey("instance") && !options.containsKey("arg0")) {
            throw new IllegalArgumentException("root-finite-cg-smoke requires --instance or a positional instance path");
        }

        int maxRouteRequests = parseIntOption(options, "max-route-requests", DEFAULT_MAX_ROUTE_REQUESTS);
        int maxThreeRequestRoutes = parseMaxThreeRequestRoutes(options, maxRouteRequests);
        int maxIterations = parseIntOption(options, "max-iterations", DEFAULT_MAX_ITERATIONS);
        int maxColumnsPerIteration = parseIntOption(
                options,
                "max-columns-per-iteration",
                DEFAULT_MAX_COLUMNS_PER_ITERATION);
        double tolerance = parseTolerance(options);
        Path path = CliSupport.instancePath(options, null);
        Instance instance = new BenchmarkInstanceReader().read(path);

        long start = System.nanoTime();
        ResultRow row = run(
                instance,
                path,
                maxRouteRequests,
                maxThreeRequestRoutes,
                maxIterations,
                maxColumnsPerIteration,
                tolerance,
                start);
        System.out.println(ResultRow.HEADER);
        System.out.println(row.toCsv());
    }

    static ResultRow run(
            Instance instance,
            Path path,
            int maxRouteRequests,
            int maxThreeRequestRoutes,
            int maxIterations,
            int maxColumnsPerIteration,
            double tolerance,
            long startNanos) throws Exception {
        validateOptions(maxRouteRequests, maxThreeRequestRoutes, maxIterations, maxColumnsPerIteration, tolerance);

        List<RouteColumn> seedColumns = RunRootLpPricingSmoke.restrictedSeedColumns(instance);
        List<RouteColumn> candidateColumns = RunRootLpPricingSmoke.candidateColumns(
                instance,
                maxRouteRequests,
                maxThreeRequestRoutes);

        try (GurobiRmp rmp = new GurobiRmp(instance)) {
            int seedAdded = addSeedColumns(rmp, seedColumns);
            int totalAdded = 0;
            int iterations = 0;
            GurobiRmp.SolveResult solve = null;
            Scan scan = null;
            String status = "restricted_finite_cg_not_started";

            for (int iteration = 0; iteration < maxIterations; iteration++) {
                solve = rmp.solveLp();
                if (!solve.isOptimal()) {
                    status = "restricted_finite_cg_lp_not_optimal";
                    break;
                }
                scan = scanCandidates(rmp, rmp.dualSolution(), candidateColumns, tolerance);
                iterations = iteration + 1;
                if (scan.negativeCandidates == 0) {
                    status = "restricted_finite_cg_pool_no_negative";
                    break;
                }
                int added = addMostNegativeColumns(rmp, scan.negativeColumns, maxColumnsPerIteration);
                totalAdded += added;
                if (added == 0) {
                    status = "restricted_finite_cg_no_new_columns";
                    break;
                }
                if (iteration == maxIterations - 1) {
                    status = "restricted_finite_cg_iteration_limit";
                }
            }

            if (solve == null) {
                solve = rmp.solveLp();
            }
            if ("restricted_finite_cg_iteration_limit".equals(status)) {
                solve = rmp.solveLp();
                if (solve.isOptimal()) {
                    scan = scanCandidates(rmp, rmp.dualSolution(), candidateColumns, tolerance);
                    if (scan.negativeCandidates == 0) {
                        status = "restricted_finite_cg_pool_no_negative";
                    }
                } else {
                    status = "restricted_finite_cg_final_lp_not_optimal";
                    scan = Scan.empty();
                }
            }
            if (scan == null && solve.isOptimal()) {
                scan = scanCandidates(rmp, rmp.dualSolution(), candidateColumns, tolerance);
            }
            if (scan == null) {
                scan = Scan.empty();
            }

            return new ResultRow(
                    path.getFileName().toString(),
                    BenchmarkInstanceReader.paperGroup(path),
                    BenchmarkInstanceReader.formatName(path),
                    instance.nRequests(),
                    instance.vertices().size(),
                    instance.vehicleCapacity(),
                    instance.maxVehicles(),
                    seedAdded,
                    rmp.realColumns().size(),
                    RunRootLpPricingSmoke.solveStatusName(solve.status()),
                    status,
                    solve.objectiveValue(),
                    solve.isOptimal() && rmp.hasPositiveArtificial(),
                    candidateColumns.size(),
                    iterations,
                    totalAdded,
                    scan.negativeCandidates,
                    scan.bestReducedCost,
                    scan.bestRoute,
                    CliSupport.elapsedMs(startNanos));
        }
    }

    private static int addSeedColumns(GurobiRmp rmp, List<RouteColumn> seedColumns) throws Exception {
        int added = 0;
        for (RouteColumn column : seedColumns) {
            if (rmp.addColumn(column)) {
                added++;
            }
        }
        return added;
    }

    private static int addMostNegativeColumns(
            GurobiRmp rmp,
            List<ScoredColumn> negativeColumns,
            int maxColumnsPerIteration) throws Exception {
        int added = 0;
        int limit = Math.min(maxColumnsPerIteration, negativeColumns.size());
        for (int index = 0; index < limit; index++) {
            if (rmp.addColumn(negativeColumns.get(index).column)) {
                added++;
            }
        }
        return added;
    }

    private static Scan scanCandidates(
            GurobiRmp rmp,
            DualSolution duals,
            List<RouteColumn> candidateColumns,
            double tolerance) {
        Set<String> presentSignatures = new HashSet<String>();
        for (RouteColumn column : rmp.columns()) {
            presentSignatures.add(column.signature());
        }
        ArrayList<ScoredColumn> negativeColumns = new ArrayList<ScoredColumn>();
        double bestReducedCost = Double.NaN;
        String bestRoute = "NA";
        for (RouteColumn column : candidateColumns) {
            double reducedCost = duals.directReducedCost(column);
            if (Double.isNaN(bestReducedCost) || reducedCost < bestReducedCost) {
                bestReducedCost = reducedCost;
                bestRoute = RunRootLpPricingSmoke.routeText(column.vertexIds());
            }
            if (reducedCost < -tolerance && !presentSignatures.contains(column.signature())) {
                negativeColumns.add(new ScoredColumn(column, reducedCost));
            }
        }
        negativeColumns.sort((left, right) -> Double.compare(left.reducedCost, right.reducedCost));
        return new Scan(negativeColumns.size(), bestReducedCost, bestRoute, List.copyOf(negativeColumns));
    }

    private static int parseIntOption(Map<String, String> options, String name, int defaultValue) {
        String value = CliSupport.option(options, name, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer: " + value, exception);
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
            return RunRootLpPricingSmoke.defaultMaxThreeRequestRoutes(maxRouteRequests);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(optionName + " must be an integer: " + value, exception);
        }
    }

    private static double parseTolerance(Map<String, String> options) {
        String value = CliSupport.option(options, "tolerance", Double.toString(DEFAULT_TOLERANCE));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("tolerance must be a number: " + value, exception);
        }
    }

    private static void validateOptions(
            int maxRouteRequests,
            int maxThreeRequestRoutes,
            int maxIterations,
            int maxColumnsPerIteration,
            double tolerance) {
        if (maxRouteRequests < 1 || maxRouteRequests > 3) {
            throw new IllegalArgumentException("max-route-requests must be 1, 2, or 3: " + maxRouteRequests);
        }
        if (maxThreeRequestRoutes < 1) {
            throw new IllegalArgumentException("max-three-request-routes must be positive: "
                    + maxThreeRequestRoutes);
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("max-iterations must be positive: " + maxIterations);
        }
        if (maxColumnsPerIteration < 1) {
            throw new IllegalArgumentException("max-columns-per-iteration must be positive: "
                    + maxColumnsPerIteration);
        }
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
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
            int finalColumns,
            String lpSolveStatus,
            String status,
            double lpObjective,
            boolean positiveArtificial,
            int candidateRoutes,
            int iterations,
            int addedColumns,
            int remainingNegativeCandidates,
            double bestCandidateRc,
            String bestCandidateRoute,
            long totalTimeMs) {
        static final String HEADER = "mode,instance,paperGroup,format,requests,vertices,capacity,maxVehicles,"
                + "seedColumns,finalColumns,lpSolveStatus,status,lpObjective,positiveArtificial,candidateRoutes,"
                + "iterations,addedColumns,remainingNegativeCandidates,bestCandidateRc,bestCandidateRoute,totalTimeMs";

        String toCsv() {
            return String.join(",",
                    "root-finite-cg-smoke",
                    BenchmarkCsv.csv(instance),
                    BenchmarkCsv.csv(paperGroup),
                    BenchmarkCsv.csv(format),
                    Integer.toString(requests),
                    Integer.toString(vertices),
                    Integer.toString(capacity),
                    Integer.toString(maxVehicles),
                    Integer.toString(seedColumns),
                    Integer.toString(finalColumns),
                    BenchmarkCsv.csv(lpSolveStatus),
                    BenchmarkCsv.csv(status),
                    BenchmarkCsv.number(lpObjective),
                    Boolean.toString(positiveArtificial),
                    Integer.toString(candidateRoutes),
                    Integer.toString(iterations),
                    Integer.toString(addedColumns),
                    Integer.toString(remainingNegativeCandidates),
                    BenchmarkCsv.number(bestCandidateRc),
                    BenchmarkCsv.csv(bestCandidateRoute),
                    Long.toString(totalTimeMs));
        }
    }

    private record Scan(
            int negativeCandidates,
            double bestReducedCost,
            String bestRoute,
            List<ScoredColumn> negativeColumns) {
        static Scan empty() {
            return new Scan(0, Double.NaN, "NA", List.of());
        }
    }

    private record ScoredColumn(RouteColumn column, double reducedCost) {
    }
}
