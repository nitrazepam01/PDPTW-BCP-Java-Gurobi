package org.pdptw.cli;

import org.pdptw.branch.BranchAndPriceSolver;
import org.pdptw.core.Instance;
import org.pdptw.cuts.SubsetRowCutRow;
import org.pdptw.io.BenchmarkInstanceReader;
import org.pdptw.master.ArtificialColumnFactory;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RunBcp {
    private RunBcp() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        CliSupport.requireKnownOptions(
                options,
                "bcp",
                "instance",
                "pricing",
                "cuts",
                "branching",
                "trace",
                "max-nodes",
                "max-cg-iterations",
                "max-set-branch-size",
                "max-route-requests",
                "max-three-request-routes",
                "max-candidate-routes");
        CliSupport.requirePositionalCount(
                options,
                "bcp",
                CliSupport.instanceOnlyPositionalLimit(options),
                "[instance]");
        String branching = CliSupport.option(options, "branching", "timo");
        if (!BcpRunner.acceptsTimoBranching(branching)) {
            throw new IllegalArgumentException("Only --branching timo is supported in the tiny branch tree runner");
        }
        String pricing = CliSupport.option(options, "pricing", "route-universe");
        String cuts = CliSupport.option(options, "cuts", "none");
        String normalizedCuts = normalizedCuts(cuts);
        if (hasExplicitBenchmarkTextInstance(options)) {
            runBenchmarkBcp(options, pricing, normalizedCuts, branching);
            return;
        }
        if ("sr".equals(normalizedCuts)) {
            if (BcpRunner.usesRouteUniversePricing(pricing)) {
                throw new IllegalArgumentException("RunBcp --cuts sr requires an explicit labeling --pricing mode; "
                        + "route-universe exact pricing does not carry SR parity state");
            }
            Instance instance = CliSupport.readInstance(options, "tiny-a-wide.json");
            BcpRunner runner = BcpRunner.withRootLabelingAndSubsetRowSeparation(
                    PricingMode.fromString(pricing).createSolver(
                            org.pdptw.branch.BranchAndPriceSolver.DEFAULT_TOLERANCE));
            BcpRunner.RunResult result = runner.runDetailedWithActiveCutRows(
                    instance,
                    branching,
                    List.of(tinySubsetRowSeed(instance)));
            System.out.println(BenchmarkCsv.rowsToCsv(List.of(result.row())));
            if (CliSupport.enabled(options, "trace")) {
                System.out.println(TraceCsv.bcpNodes(instance.name(), result.solverResult()));
            }
            return;
        }
        if ("robust".equals(normalizedCuts)) {
            if (BcpRunner.usesRouteUniversePricing(pricing)) {
                throw new IllegalArgumentException("RunBcp --cuts robust requires an explicit labeling --pricing mode; "
                        + "route-universe exact pricing does not expose robust DTI/PTI repair counters");
            }
            Instance instance = CliSupport.readInstance(options, "tiny-robust-cli.json");
            BcpRunner runner = BcpRunner.withRootLabelingAndRobustTwoPathSeparation(
                    PricingMode.fromString(pricing).createSolver(
                            org.pdptw.branch.BranchAndPriceSolver.DEFAULT_TOLERANCE),
                    1);
            BcpRunner.RunResult result = runner.runDetailed(instance, "none", branching);
            System.out.println(BenchmarkCsv.rowsToCsv(List.of(result.row())));
            if (CliSupport.enabled(options, "trace")) {
                System.out.println(TraceCsv.bcpNodes(instance.name(), result.solverResult()));
            }
            return;
        }
        if (isRobustSubsetRowCuts(normalizedCuts)) {
            if (BcpRunner.usesRouteUniversePricing(pricing)) {
                throw new IllegalArgumentException("RunBcp --cuts robust,sr requires an explicit labeling "
                        + "--pricing mode; route-universe exact pricing does not carry robust/SR pricing state");
            }
            Instance instance = CliSupport.readInstance(options, "tiny-robust-sr-cli.json");
            BcpRunner runner = BcpRunner.withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(
                    PricingMode.fromString(pricing).createSolver(
                            org.pdptw.branch.BranchAndPriceSolver.DEFAULT_TOLERANCE),
                    1);
            BcpRunner.RunResult result = runner.runDetailed(instance, "none", branching);
            System.out.println(BenchmarkCsv.rowsToCsv(List.of(result.row())));
            if (CliSupport.enabled(options, "trace")) {
                System.out.println(TraceCsv.bcpNodes(instance.name(), result.solverResult()));
            }
            return;
        }
        Instance instance = CliSupport.readInstance(options, "tiny-a-wide.json");
        BcpRunner.RunResult result = runnerFor(pricing, cuts).runDetailed(instance, "none", branching);
        System.out.println(BenchmarkCsv.rowsToCsv(List.of(result.row())));
        if (CliSupport.enabled(options, "trace")) {
            System.out.println(TraceCsv.bcpNodes(instance.name(), result.solverResult()));
        }
    }

    private static void runBenchmarkBcp(
            Map<String, String> options,
            String pricing,
            String normalizedCuts,
            String branching) throws Exception {
        if (BcpRunner.usesRouteUniversePricing(pricing)) {
            throw new IllegalArgumentException("Benchmark RC/LL BCP requires explicit labeling pricing, "
                    + "for example --pricing bidir-dynamic; route-universe exact pricing is tiny-only");
        }
        if ("robust".equals(normalizedCuts) || isRobustSubsetRowCuts(normalizedCuts)) {
            throw new IllegalArgumentException("Benchmark RC/LL BCP cannot use --cuts robust yet; "
                    + "the current robust cut candidate generator is tiny-only");
        }
        if (!"none".equals(normalizedCuts) && !"sr".equals(normalizedCuts)) {
            throw new IllegalArgumentException("Benchmark RC/LL BCP supports --cuts none or --cuts sr only");
        }

        Instance instance = CliSupport.readBenchmarkInstance(options, null);
        PricingMode mode = PricingMode.fromString(pricing);
        int maxNodes = parsePositiveInt(
                options,
                "max-nodes",
                BranchAndPriceSolver.DEFAULT_MAX_NODES);
        int maxCgIterations = parsePositiveInt(
                options,
                "max-cg-iterations",
                BranchAndPriceSolver.DEFAULT_MAX_COLUMN_GENERATION_ITERATIONS);
        int maxSetBranchSize = parsePositiveInt(
                options,
                "max-set-branch-size",
                BranchAndPriceSolver.DEFAULT_MAX_SET_OUTFLOW_BRANCH_SET_SIZE);
        int maxRouteRequests = parsePositiveInt(options, "max-route-requests", 3);
        int maxThreeRequestRoutes = parseMaxThreeRequestRoutes(options, maxRouteRequests);
        List<RouteColumn> seedColumns = benchmarkSeedColumns(instance, maxRouteRequests, maxThreeRequestRoutes);

        BcpRunner runner = "sr".equals(normalizedCuts)
                ? BcpRunner.withBenchmarkLabelingAndSubsetRowSeparation(
                        mode.createSolver(BranchAndPriceSolver.DEFAULT_TOLERANCE),
                        ArtificialColumnFactory.DEFAULT_PENALTY,
                        maxNodes,
                        maxCgIterations,
                        maxSetBranchSize)
                : BcpRunner.withBenchmarkLabeling(
                        mode.createSolver(BranchAndPriceSolver.DEFAULT_TOLERANCE),
                        ArtificialColumnFactory.DEFAULT_PENALTY,
                        maxNodes,
                        maxCgIterations,
                        maxSetBranchSize);
        long start = System.nanoTime();
        BcpRunner.RunResult result = runner.runDetailedWithActiveCutRowsAndRouteUniverse(
                instance,
                branching,
                seedColumns,
                List.of());
        System.out.println(BenchmarkCsv.rowsToCsv(List.of(result.row())));
        if (CliSupport.enabled(options, "trace")) {
            System.out.println(TraceCsv.bcpNodes(instance.name(), result.solverResult()));
        }
    }

    private static boolean hasExplicitBenchmarkTextInstance(Map<String, String> options) {
        if (!options.containsKey("instance") && !options.containsKey("arg0")) {
            return false;
        }
        Path path = CliSupport.instancePath(options, null);
        return !"tiny-json".equals(BenchmarkInstanceReader.formatName(path));
    }

    private static List<RouteColumn> benchmarkSeedColumns(
            Instance instance,
            int maxRouteRequests,
            int maxThreeRequestRoutes) {
        LinkedHashMap<String, RouteColumn> bySignature = new LinkedHashMap<String, RouteColumn>();
        for (RouteColumn column : RunRootLpPricingSmoke.restrictedSeedColumns(instance)) {
            bySignature.put(column.signature(), column);
        }
        for (RouteColumn column : RunRootLpPricingSmoke.candidateColumns(
                instance,
                maxRouteRequests,
                maxThreeRequestRoutes)) {
            bySignature.putIfAbsent(column.signature(), column);
        }
        return new ArrayList<RouteColumn>(bySignature.values());
    }

    private static int parseMaxThreeRequestRoutes(Map<String, String> options, int maxRouteRequests) {
        String value = options.get("max_three_request_routes");
        String optionName = "max-three-request-routes";
        if (value == null) {
            value = options.get("max_candidate_routes");
            optionName = "max-candidate-routes";
        }
        if (value == null) {
            return maxRouteRequests >= 3 ? 600 : RunRootLpPricingSmoke.NO_CANDIDATE_LIMIT;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(optionName + " must be positive: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(optionName + " must be an integer: " + value, exception);
        }
    }

    private static int parsePositiveInt(Map<String, String> options, String name, int defaultValue) {
        String value = CliSupport.option(options, name, Integer.toString(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(name + " must be positive: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer: " + value, exception);
        }
    }

    private static BcpRunner runnerFor(String pricing, String cuts) {
        String normalizedCuts = normalizedCuts(cuts);
        if ("none".equals(normalizedCuts)) {
            return BcpRunner.fromPricingOption(pricing);
        }
        throw new IllegalArgumentException("RunBcp supports only tiny explicit-labeling --cuts sr, --cuts robust, "
                + "or --cuts robust,sr with non-route-universe pricing");
    }

    private static String normalizedCuts(String cuts) {
        if (cuts == null || cuts.isBlank()) {
            return "none";
        }
        String[] parts = cuts.trim().toLowerCase(Locale.ROOT).split(",");
        java.util.ArrayList<String> normalized = new java.util.ArrayList<String>();
        for (String part : parts) {
            String token = part.trim();
            if (!token.isEmpty() && !"none".equals(token)) {
                normalized.add(token);
            }
        }
        if (normalized.isEmpty()) {
            return "none";
        }
        return String.join(",", normalized);
    }

    private static boolean isRobustSubsetRowCuts(String normalizedCuts) {
        return "robust,sr".equals(normalizedCuts) || "sr,robust".equals(normalizedCuts);
    }

    private static SubsetRowCutRow tinySubsetRowSeed(Instance instance) {
        if (instance.nRequests() < 3) {
            throw new IllegalArgumentException(
                    "RunBcp --cuts sr tiny path requires an instance with at least 3 requests");
        }
        return SubsetRowCutRow.ofL2Triple("run_bcp_tiny_sr_seed", 1, 2, 3);
    }

}
