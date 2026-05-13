package org.pdptw.cli;

import com.gurobi.gurobi.GRBException;
import org.pdptw.branch.BranchAndPriceSolver;
import org.pdptw.core.Instance;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingMode;
import org.pdptw.pricing.PricingSolver;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class BcpRunner {
    private final BranchAndPriceSolver branchAndPriceSolver;

    public BcpRunner() {
        this(new BranchAndPriceSolver());
    }

    public static BcpRunner withRootLabeling(PricingSolver solver) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabeling(solver));
    }

    public static BcpRunner withRootLabeling(
            PricingSolver solver,
            double artificialPenalty,
            int maxNodes) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabeling(solver, artificialPenalty, maxNodes));
    }

    public static BcpRunner withBenchmarkLabeling(
            PricingSolver solver,
            double artificialPenalty,
            int maxNodes,
            int maxColumnGenerationIterations,
            int maxSetOutflowBranchSetSize) {
        return new BcpRunner(BranchAndPriceSolver.withBenchmarkLabeling(
                solver,
                artificialPenalty,
                maxNodes,
                maxColumnGenerationIterations,
                maxSetOutflowBranchSetSize));
    }

    public static BcpRunner withBenchmarkLabelingAndSubsetRowSeparation(
            PricingSolver solver,
            double artificialPenalty,
            int maxNodes,
            int maxColumnGenerationIterations,
            int maxSetOutflowBranchSetSize) {
        return new BcpRunner(BranchAndPriceSolver.withBenchmarkLabelingAndSubsetRowSeparation(
                solver,
                artificialPenalty,
                maxNodes,
                maxColumnGenerationIterations,
                maxSetOutflowBranchSetSize));
    }

    public static BcpRunner withRootLabelingAndSubsetRowSeparation(PricingSolver solver) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabelingAndSubsetRowSeparation(solver));
    }

    public static BcpRunner withRootLabelingAndSubsetRowSeparation(PricingSolver solver, int maxNodes) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabelingAndSubsetRowSeparation(solver, maxNodes));
    }

    public static BcpRunner withRootLabelingAndGlobalSubsetRowSeparation(PricingSolver solver) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabelingAndGlobalSubsetRowSeparation(solver));
    }

    public static BcpRunner withRootLabelingAndGlobalSubsetRowSeparation(PricingSolver solver, int maxNodes) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabelingAndGlobalSubsetRowSeparation(solver, maxNodes));
    }

    public static BcpRunner withRootLabelingAndRobustTwoPathSeparation(PricingSolver solver) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabelingAndRobustTwoPathSeparation(solver));
    }

    public static BcpRunner withRootLabelingAndRobustTwoPathSeparation(PricingSolver solver, int maxNodes) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabelingAndRobustTwoPathSeparation(solver, maxNodes));
    }

    public static BcpRunner withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(PricingSolver solver) {
        return new BcpRunner(
                BranchAndPriceSolver.withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(solver));
    }

    public static BcpRunner withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(
            PricingSolver solver,
            int maxNodes) {
        return new BcpRunner(
                BranchAndPriceSolver.withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(solver, maxNodes));
    }

    public static BcpRunner withRootLabelingAndRobustRoundedCapacitySeparation(PricingSolver solver) {
        return new BcpRunner(BranchAndPriceSolver.withRootLabelingAndRobustRoundedCapacitySeparation(solver));
    }

    public static BcpRunner withRootLabelingAndRobustRoundedCapacitySeparation(
            PricingSolver solver,
            int maxNodes) {
        return new BcpRunner(
                BranchAndPriceSolver.withRootLabelingAndRobustRoundedCapacitySeparation(solver, maxNodes));
    }

    public static BcpRunner withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(
            PricingSolver solver) {
        return new BcpRunner(
                BranchAndPriceSolver.withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(solver));
    }

    public static BcpRunner withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(
            PricingSolver solver,
            int maxNodes) {
        return new BcpRunner(
                BranchAndPriceSolver.withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(
                        solver,
                        maxNodes));
    }

    public static BcpRunner fromPricingOption(String pricing) {
        String normalized = normalizedPricing(pricing);
        if (usesRouteUniversePricing(normalized)) {
            return new BcpRunner();
        }
        PricingMode mode;
        try {
            mode = PricingMode.fromString(normalized);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported BCP --pricing option: " + pricing
                    + ". Use route-universe or a pricing mode such as bidir-dynamic.", exception);
        }
        return withRootLabeling(mode.createSolver(BranchAndPriceSolver.DEFAULT_TOLERANCE));
    }

    public BcpRunner(BranchAndPriceSolver branchAndPriceSolver) {
        this.branchAndPriceSolver = Objects.requireNonNull(branchAndPriceSolver, "branchAndPriceSolver");
    }

    public BenchmarkCsv.Row run(Instance instance, String cuts, String branching) throws GRBException {
        return runDetailed(instance, cuts, branching).row();
    }

    public RunResult runDetailed(Instance instance, String cuts, String branching) throws GRBException {
        requireNoActiveCuts(cuts);
        return runDetailedWithActiveCutRows(instance, branching, List.of());
    }

    public RunResult runDetailedWithRouteUniverse(
            Instance instance,
            String cuts,
            String branching,
            Collection<RouteColumn> routeUniverse) throws GRBException {
        requireNoActiveCuts(cuts);
        return runDetailedWithActiveCutRowsAndRouteUniverse(
                instance,
                branching,
                routeUniverse,
                List.of());
    }

    public RunResult runDetailedWithActiveCutRowsAndRouteUniverse(
            Instance instance,
            String branching,
            Collection<RouteColumn> routeUniverse,
            Collection<? extends MasterCutRow> activeCutRows) throws GRBException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(routeUniverse, "routeUniverse");
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        requireTimoBranching(branching);
        List<MasterCutRow> cutRows = List.copyOf(activeCutRows);
        long start = System.nanoTime();
        BranchAndPriceSolver.Result result = branchAndPriceSolver.solve(instance, routeUniverse, cutRows);
        long totalMs = CliSupport.elapsedMs(start);
        return runResult(instance, cutRows, result, totalMs);
    }

    public RunResult runDetailedWithActiveCutRows(
            Instance instance,
            String branching,
            Collection<? extends MasterCutRow> activeCutRows) throws GRBException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        requireTimoBranching(branching);
        List<MasterCutRow> cutRows = List.copyOf(activeCutRows);
        long start = System.nanoTime();
        BranchAndPriceSolver.Result result = branchAndPriceSolver.solveWithActiveCutRows(instance, cutRows);
        long totalMs = CliSupport.elapsedMs(start);
        return runResult(instance, cutRows, result, totalMs);
    }

    private static RunResult runResult(
            Instance instance,
            List<MasterCutRow> cutRows,
            BranchAndPriceSolver.Result result,
            long totalMs) {
        BenchmarkCsv.Row row = new BenchmarkCsv.Row(
                instance.name(),
                "bcp",
                result.status(),
                result.rootLowerBound(),
                result.hasIncumbent() ? result.incumbentObjective() : Double.NaN,
                CliSupport.gap(result.rootLowerBound(),
                        result.hasIncumbent() ? result.incumbentObjective() : Double.NaN),
                result.processedNodes(),
                result.routeUniverseSize(),
                Math.max(cutRows.size(), maxActiveCutCount(result)),
                result.totalPricingCalls(),
                result.totalForwardLabels(),
                result.totalBackwardLabels(),
                result.totalDominatedLabels(),
                result.totalPricingTimeMs(),
                totalMs);
        return new RunResult(row, result);
    }

    private static int maxActiveCutCount(BranchAndPriceSolver.Result result) {
        int max = 0;
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            max = Math.max(max, record.activeCutCount());
        }
        return max;
    }

    public static boolean acceptsTimoBranching(String branching) {
        return branching == null || branching.isBlank() || "timo".equalsIgnoreCase(branching);
    }

    public static boolean usesRouteUniversePricing(String pricing) {
        String normalized = normalizedPricing(pricing);
        return normalized.isBlank()
                || "route-universe".equalsIgnoreCase(normalized)
                || "route_universe".equalsIgnoreCase(normalized)
                || "route-universe-exact".equalsIgnoreCase(normalized)
                || "route_universe_exact".equalsIgnoreCase(normalized);
    }

    private static String normalizedPricing(String pricing) {
        return pricing == null ? "" : pricing.trim();
    }

    private static void requireTimoBranching(String branching) {
        if (!acceptsTimoBranching(branching)) {
            throw new IllegalArgumentException("Only --branching timo is supported in the tiny branch tree runner");
        }
    }

    private static void requireNoActiveCuts(String cuts) {
        if (CliSupport.enabledCsvCount(cuts) > 0) {
            throw new IllegalArgumentException("RunBcp CLI does not separate cuts from --cuts yet; "
                    + "active robust/subset-row cut rows are available through programmatic BcpRunner APIs");
        }
    }

    public record RunResult(BenchmarkCsv.Row row, BranchAndPriceSolver.Result solverResult) {
        public RunResult {
            Objects.requireNonNull(row, "row");
            Objects.requireNonNull(solverResult, "solverResult");
        }
    }

}
