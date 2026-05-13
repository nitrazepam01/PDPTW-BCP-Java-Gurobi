package org.pdptw.branch;

import com.gurobi.gurobi.GRBException;
import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.cuts.RobustCutCandidateGenerator;
import org.pdptw.cuts.RobustCutRow;
import org.pdptw.cuts.RobustCutSeparator;
import org.pdptw.cuts.SubsetRowCutRow;
import org.pdptw.cuts.SubsetRowCutSeparator;
import org.pdptw.master.ArtificialColumnFactory;
import org.pdptw.master.DualSolution;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingContext;
import org.pdptw.pricing.PricingResult;
import org.pdptw.pricing.PricingSolver;
import org.pdptw.pricing.ReducedCostMatrices;
import org.pdptw.validation.BruteForcePricingOracle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class BranchAndPriceSolver {
    public static final int DEFAULT_MAX_NODES = 100;
    public static final int DEFAULT_MAX_COLUMN_GENERATION_ITERATIONS = 100;
    public static final int DEFAULT_MAX_SET_OUTFLOW_BRANCH_SET_SIZE = 3;
    public static final double DEFAULT_TOLERANCE = 1.0e-7;

    private final VehicleCountBrancher vehicleCountBrancher;
    private final SetOutflowBrancher setOutflowBrancher;
    private final double artificialPenalty;
    private final double tolerance;
    private final int maxNodes;
    private final int maxColumnGenerationIterations;
    private final BranchNodePricingBackend pricingBackend;
    private final NodeCutPropagationPolicy cutPropagationPolicy;
    private final List<RobustCutRow> robustCandidateRows;
    private final boolean separateRobustTwoPathRows;
    private final boolean separateRobustRoundedCapacityRows;
    private final String optimalStatus;

    public BranchAndPriceSolver() {
        this(new VehicleCountBrancher(), new SetOutflowBrancher(),
                ArtificialColumnFactory.DEFAULT_PENALTY, DEFAULT_TOLERANCE, DEFAULT_MAX_NODES);
    }

    public BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher) {
        this(vehicleCountBrancher, setOutflowBrancher,
                ArtificialColumnFactory.DEFAULT_PENALTY, DEFAULT_TOLERANCE, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabeling(PricingSolver solver) {
        return withRootLabeling(solver, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabeling(PricingSolver solver, int maxNodes) {
        return withRootLabeling(solver, ArtificialColumnFactory.DEFAULT_PENALTY, maxNodes);
    }

    public static BranchAndPriceSolver withRootLabeling(
            PricingSolver solver,
            double artificialPenalty,
            int maxNodes) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                artificialPenalty,
                DEFAULT_TOLERANCE,
                maxNodes,
                new HybridNodePricingBackend(
                        new LabelingNodePricingBackend(solver),
                        new RouteUniverseNodePricingBackend()));
    }

    public static BranchAndPriceSolver withBenchmarkLabeling(
            PricingSolver solver,
            double artificialPenalty,
            int maxNodes,
            int maxColumnGenerationIterations,
            int maxSetOutflowBranchSetSize) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(DEFAULT_TOLERANCE, maxSetOutflowBranchSetSize),
                artificialPenalty,
                DEFAULT_TOLERANCE,
                maxNodes,
                new LabelingNodePricingBackend(solver),
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false,
                false,
                maxColumnGenerationIterations,
                "optimal_benchmark_branch_tree");
    }

    public static BranchAndPriceSolver withBenchmarkLabelingAndSubsetRowSeparation(
            PricingSolver solver,
            double artificialPenalty,
            int maxNodes,
            int maxColumnGenerationIterations,
            int maxSetOutflowBranchSetSize) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(DEFAULT_TOLERANCE, maxSetOutflowBranchSetSize),
                artificialPenalty,
                DEFAULT_TOLERANCE,
                maxNodes,
                new LabelingNodePricingBackend(solver),
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR,
                List.of(),
                false,
                false,
                maxColumnGenerationIterations,
                "optimal_benchmark_branch_tree");
    }

    public static BranchAndPriceSolver withRootLabelingAndSubsetRowSeparation(PricingSolver solver) {
        return withRootLabelingAndSubsetRowSeparation(solver, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabelingAndSubsetRowSeparation(
            PricingSolver solver,
            int maxNodes) {
        return withRootLabelingAndSubsetRowSeparation(
                solver,
                maxNodes,
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR);
    }

    public static BranchAndPriceSolver withRootLabelingAndGlobalSubsetRowSeparation(PricingSolver solver) {
        return withRootLabelingAndGlobalSubsetRowSeparation(solver, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabelingAndGlobalSubsetRowSeparation(
            PricingSolver solver,
            int maxNodes) {
        return withRootLabelingAndSubsetRowSeparation(
                solver,
                maxNodes,
                NodeCutPropagationPolicy.GLOBAL_AUTOMATIC_SR);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustTwoPathSeparation(PricingSolver solver) {
        return withRootLabelingAndRobustTwoPathSeparation(solver, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustTwoPathSeparation(
            PricingSolver solver,
            int maxNodes) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                ArtificialColumnFactory.DEFAULT_PENALTY,
                DEFAULT_TOLERANCE,
                maxNodes,
                new HybridNodePricingBackend(
                        new LabelingNodePricingBackend(solver),
                        new RouteUniverseNodePricingBackend()),
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                true,
                false);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(
            PricingSolver solver) {
        return withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(solver, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(
            PricingSolver solver,
            int maxNodes) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                ArtificialColumnFactory.DEFAULT_PENALTY,
                DEFAULT_TOLERANCE,
                maxNodes,
                new HybridNodePricingBackend(
                        new LabelingNodePricingBackend(solver),
                        new RouteUniverseNodePricingBackend()),
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR,
                List.of(),
                true,
                false);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustRoundedCapacitySeparation(PricingSolver solver) {
        return withRootLabelingAndRobustRoundedCapacitySeparation(solver, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustRoundedCapacitySeparation(
            PricingSolver solver,
            int maxNodes) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                ArtificialColumnFactory.DEFAULT_PENALTY,
                DEFAULT_TOLERANCE,
                maxNodes,
                new HybridNodePricingBackend(
                        new LabelingNodePricingBackend(solver),
                        new RouteUniverseNodePricingBackend()),
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false,
                true);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(
            PricingSolver solver) {
        return withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(solver, DEFAULT_MAX_NODES);
    }

    public static BranchAndPriceSolver withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(
            PricingSolver solver,
            int maxNodes) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                ArtificialColumnFactory.DEFAULT_PENALTY,
                DEFAULT_TOLERANCE,
                maxNodes,
                new HybridNodePricingBackend(
                        new LabelingNodePricingBackend(solver),
                        new RouteUniverseNodePricingBackend()),
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR,
                List.of(),
                false,
                true);
    }

    private static BranchAndPriceSolver withRootLabelingAndSubsetRowSeparation(
            PricingSolver solver,
            int maxNodes,
            NodeCutPropagationPolicy policy) {
        return new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                ArtificialColumnFactory.DEFAULT_PENALTY,
                DEFAULT_TOLERANCE,
                maxNodes,
                new HybridNodePricingBackend(
                        new LabelingNodePricingBackend(solver),
                        new RouteUniverseNodePricingBackend()),
                policy);
    }

    public BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes) {
        this(
                vehicleCountBrancher,
                setOutflowBrancher,
                artificialPenalty,
                tolerance,
                maxNodes,
                new RouteUniverseNodePricingBackend());
    }

    BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes,
            BranchNodePricingBackend pricingBackend) {
        this(
                vehicleCountBrancher,
                setOutflowBrancher,
                artificialPenalty,
                tolerance,
                maxNodes,
                pricingBackend,
                false);
    }

    BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes,
            BranchNodePricingBackend pricingBackend,
            boolean separateSubsetRowsAtNodes) {
        this(
                vehicleCountBrancher,
                setOutflowBrancher,
                artificialPenalty,
                tolerance,
                maxNodes,
                pricingBackend,
                separateSubsetRowsAtNodes
                        ? NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR
                        : NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS);
    }

    BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes,
            BranchNodePricingBackend pricingBackend,
            NodeCutPropagationPolicy cutPropagationPolicy) {
        this(
                vehicleCountBrancher,
                setOutflowBrancher,
                artificialPenalty,
                tolerance,
                maxNodes,
                pricingBackend,
                cutPropagationPolicy,
                List.of());
    }

    BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes,
            BranchNodePricingBackend pricingBackend,
            NodeCutPropagationPolicy cutPropagationPolicy,
            List<? extends RobustCutRow> robustCandidateRows) {
        this(
                vehicleCountBrancher,
                setOutflowBrancher,
                artificialPenalty,
                tolerance,
                maxNodes,
                pricingBackend,
                cutPropagationPolicy,
                robustCandidateRows,
                false);
    }

    BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes,
            BranchNodePricingBackend pricingBackend,
            NodeCutPropagationPolicy cutPropagationPolicy,
            List<? extends RobustCutRow> robustCandidateRows,
            boolean separateRobustTwoPathRows) {
        this(
                vehicleCountBrancher,
                setOutflowBrancher,
                artificialPenalty,
                tolerance,
                maxNodes,
                pricingBackend,
                cutPropagationPolicy,
                robustCandidateRows,
                separateRobustTwoPathRows,
                false);
    }

    BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes,
            BranchNodePricingBackend pricingBackend,
            NodeCutPropagationPolicy cutPropagationPolicy,
            List<? extends RobustCutRow> robustCandidateRows,
            boolean separateRobustTwoPathRows,
            boolean separateRobustRoundedCapacityRows) {
        this(
                vehicleCountBrancher,
                setOutflowBrancher,
                artificialPenalty,
                tolerance,
                maxNodes,
                pricingBackend,
                cutPropagationPolicy,
                robustCandidateRows,
                separateRobustTwoPathRows,
                separateRobustRoundedCapacityRows,
                DEFAULT_MAX_COLUMN_GENERATION_ITERATIONS,
                "optimal_tiny_branch_tree");
    }

    BranchAndPriceSolver(
            VehicleCountBrancher vehicleCountBrancher,
            SetOutflowBrancher setOutflowBrancher,
            double artificialPenalty,
            double tolerance,
            int maxNodes,
            BranchNodePricingBackend pricingBackend,
            NodeCutPropagationPolicy cutPropagationPolicy,
            List<? extends RobustCutRow> robustCandidateRows,
            boolean separateRobustTwoPathRows,
            boolean separateRobustRoundedCapacityRows,
            int maxColumnGenerationIterations,
            String optimalStatus) {
        this.vehicleCountBrancher = Objects.requireNonNull(vehicleCountBrancher, "vehicleCountBrancher");
        this.setOutflowBrancher = Objects.requireNonNull(setOutflowBrancher, "setOutflowBrancher");
        this.artificialPenalty = requirePositiveFinite(artificialPenalty, "artificialPenalty");
        this.tolerance = requireTolerance(tolerance);
        if (maxNodes < 1) {
            throw new IllegalArgumentException("maxNodes must be positive: " + maxNodes);
        }
        if (maxColumnGenerationIterations < 1) {
            throw new IllegalArgumentException("maxColumnGenerationIterations must be positive: "
                    + maxColumnGenerationIterations);
        }
        this.maxNodes = maxNodes;
        this.maxColumnGenerationIterations = maxColumnGenerationIterations;
        this.pricingBackend = Objects.requireNonNull(pricingBackend, "pricingBackend");
        this.cutPropagationPolicy = Objects.requireNonNull(cutPropagationPolicy, "cutPropagationPolicy");
        this.robustCandidateRows = List.copyOf(Objects.requireNonNull(robustCandidateRows, "robustCandidateRows"));
        this.separateRobustTwoPathRows = separateRobustTwoPathRows;
        this.separateRobustRoundedCapacityRows = separateRobustRoundedCapacityRows;
        if (optimalStatus == null || optimalStatus.isBlank()) {
            throw new IllegalArgumentException("optimalStatus must not be blank");
        }
        this.optimalStatus = optimalStatus;
        if ((!this.robustCandidateRows.isEmpty()
                || this.separateRobustTwoPathRows
                || this.separateRobustRoundedCapacityRows)
                && this.cutPropagationPolicy.publishSeparatedRowsToGlobalPool()) {
            throw new IllegalArgumentException(
                    "branch-node robust candidate separation cannot be combined with global SR propagation yet");
        }
    }

    NodeCutPropagationPolicy cutPropagationPolicy() {
        return cutPropagationPolicy;
    }

    public Optional<BranchDecision> chooseBranch(
            BranchNode parent,
            List<BranchDecision.RouteValue> solution) {
        Objects.requireNonNull(parent, "parent");
        List<BranchDecision.RouteValue> values = BranchDecision.copySolution(solution);
        Optional<BranchDecision> vehicleDecision = vehicleCountBrancher.branch(parent, values);
        if (vehicleDecision.isPresent()) {
            return vehicleDecision;
        }
        return setOutflowBrancher.branch(parent, values);
    }

    public Result solve(Instance instance) throws GRBException {
        return solveWithActiveCutRows(instance, List.of());
    }

    public Result solveWithActiveCutRows(
            Instance instance,
            Collection<? extends MasterCutRow> activeCutRows) throws GRBException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        ArrayList<RouteColumn> columns = new ArrayList<RouteColumn>();
        for (BruteForcePricingOracle.RouteEvaluation route
                : new BruteForcePricingOracle().enumerate(instance)) {
            columns.add(RouteColumn.fromRoute(
                    "route_" + columns.size(),
                    new Route(route.vertexIds()),
                    instance));
        }
        return solve(instance, columns, activeCutRows);
    }

    public Result solve(Instance instance, Collection<RouteColumn> routeUniverse) throws GRBException {
        return solve(instance, routeUniverse, List.of());
    }

    public Result solve(
            Instance instance,
            Collection<RouteColumn> routeUniverse,
            Collection<? extends MasterCutRow> activeCutRows) throws GRBException {
        Objects.requireNonNull(instance, "instance");
        List<RouteColumn> universe = copyRouteUniverse(routeUniverse);
        ArrayList<MasterCutRow> cutRows = new ArrayList<MasterCutRow>(copyCutRows(activeCutRows));
        rejectRobustActiveRowsWithGlobalSr(cutRows);
        NodeQueue queue = new NodeQueue();
        queue.add(BranchNode.root());
        ArrayList<NodeRecord> records = new ArrayList<NodeRecord>();
        double incumbent = Double.POSITIVE_INFINITY;
        ArrayList<RouteColumn> incumbentColumns = new ArrayList<RouteColumn>();
        int createdNodes = 1;
        int processedNodes = 0;
        boolean nodeLimitReached = false;

        while (!queue.isEmpty()) {
            if (processedNodes >= maxNodes) {
                nodeLimitReached = true;
                break;
            }
            BranchNode node = queue.poll().orElseThrow();
            processedNodes++;
            NodeRelaxation relaxation = solveNodeRelaxation(instance, node, universe, cutRows);
            String pruneReason = "";

            if (relaxation.hasPositiveArtificial()) {
                pruneReason = "positive_artificial";
            } else if (relaxation.lowerBound() >= incumbent - tolerance) {
                pruneReason = "bound";
            } else if (relaxation.integral(tolerance)) {
                incumbent = relaxation.lowerBound();
                incumbentColumns.clear();
                incumbentColumns.addAll(relaxation.selectedIntegerColumns());
                pruneReason = "incumbent";
            } else {
                Optional<BranchDecision> branch = chooseBranch(node, relaxation.branchSolution());
                if (branch.isPresent()) {
                    BranchDecision decision = branch.get();
                    queue.add(decision.leftChild().withLowerBound(relaxation.lowerBound()));
                    queue.add(decision.rightChild().withLowerBound(relaxation.lowerBound()));
                    createdNodes += 2;
                    pruneReason = "branched_" + decision.type().name().toLowerCase();
                    records.add(NodeRecord.of(node, relaxation, incumbent, pruneReason, decision));
                    continue;
                }
                pruneReason = "no_branch";
            }

            records.add(NodeRecord.of(node, relaxation, incumbent, pruneReason, null));
        }

        String status;
        if (nodeLimitReached) {
            status = "node_limit";
        } else if (Double.isFinite(incumbent)) {
            status = optimalStatus;
        } else {
            status = "no_incumbent";
        }
        return new Result(
                status,
                processedNodes,
                createdNodes,
                universe.size(),
                records,
                incumbent,
                incumbentColumns);
    }

    private NodeRelaxation solveNodeRelaxation(
            Instance instance,
            BranchNode node,
            List<RouteColumn> routeUniverse,
            List<MasterCutRow> activeCutRows) throws GRBException {
        try (GurobiRmp rmp = new GurobiRmp(instance, artificialPenalty)) {
            for (MasterCutRow cutRow : activeCutRows) {
                rmp.addCutRow(cutRow);
            }
            for (BranchConstraint constraint : node.constraints()) {
                rmp.addCutRow(BranchMasterRow.of(constraint));
            }
            int activeCutCount = activeCutCount(rmp, node);
            seedBranchFeasibilityColumns(instance, node, rmp, routeUniverse);
            int pricingCalls = 0;
            int generatedColumns = 0;
            int pricedColumns = 0;
            int forwardLabels = 0;
            int backwardLabels = 0;
            int dominatedLabels = 0;
            boolean labelStatsAvailable = true;
            long pricingTimeMs = 0L;

            for (int iteration = 0; iteration < maxColumnGenerationIterations; iteration++) {
                NodeLpSolve nodeSolve = solveNodeLpWithSeparatedRows(rmp);
                GurobiRmp.SolveResult solve = nodeSolve.solveResult();
                activeCutCount = activeCutCount(rmp, node);
                if (!solve.isOptimal()) {
                    return NodeRelaxation.infeasible(solve.status(), activeCutCount);
                }
                publishSeparatedCutRows(activeCutRows, nodeSolve.separatedCutRows());

                DualSolution duals = rmp.dualSolution();
                ReducedCostMatrices matrices = ReducedCostMatrices.fromDualSolution(instance, duals);
                PricingContext pricingContext = PricingContext.withCuts(
                        matrices,
                        rmp.robustCutsFromDuals(),
                        rmp.subsetRowCutsFromDuals());
                long pricingStart = System.nanoTime();
                BranchNodePricingResult pricingResult = pricingBackend.price(new BranchNodePricingRequest(
                        instance,
                        pricingContext,
                        rmp.cutDuals(),
                        routeUniverse,
                        rmp.realColumns(),
                        tolerance));
                PricingAudit pricingAudit = new PricingAudit(
                        pricingResult.bestReducedCost(),
                        pricingResult.pricedColumns(),
                        pricingResult.columns(),
                        pricingResult.stats(),
                        pricingResult.statsAvailable(),
                        0,
                        0,
                        0L);
                pricingTimeMs += elapsedMs(pricingStart);
                pricingCalls++;
                pricedColumns += pricingAudit.pricedColumns();
                if (pricingResult.statsAvailable()) {
                    forwardLabels += pricingAudit.forwardLabels();
                    backwardLabels += pricingAudit.backwardLabels();
                    dominatedLabels += pricingAudit.dominatedLabels();
                } else {
                    labelStatsAvailable = false;
                }

                if (pricingAudit.bestReducedCost() < -tolerance && !pricingAudit.hasColumns()) {
                    throw new IllegalStateException("branch-node pricing backend found negative reduced cost"
                            + " but returned no columns"
                            + " backend=" + pricingBackend.name()
                            + " bestReducedCost=" + pricingAudit.bestReducedCost());
                }

                if (pricingAudit.bestReducedCost() >= -tolerance) {
                    List<GurobiRmp.ColumnValue> values = rmp.solutionValues();
                    List<BranchDecision.RouteValue> branchSolution = branchSolution(instance, values);
                    ensureNodeConstraintsSatisfied(node, branchSolution);
                    return NodeRelaxation.optimal(
                            solve.objectiveValue(),
                            rmp.hasPositiveArtificial(),
                            values,
                            branchSolution,
                            pricingAudit.withTotals(
                                    pricingCalls,
                                    generatedColumns,
                                    pricedColumns,
                                    labelStatsAvailable ? forwardLabels : -1,
                                    labelStatsAvailable ? backwardLabels : -1,
                                    labelStatsAvailable ? dominatedLabels : -1,
                                    pricingTimeMs),
                            activeCutCount);
                }

                int added = 0;
                for (RouteColumn column : pricingAudit.columns()) {
                    if (rmp.addColumn(column)) {
                        added++;
                    }
                }
                if (added == 0) {
                    throw new IllegalStateException("branch-node pricing found negative columns but added none"
                            + " bestReducedCost=" + pricingAudit.bestReducedCost());
                }
                generatedColumns += added;
            }
        }
        throw new IllegalStateException("branch-node column generation exceeded max iterations="
                + maxColumnGenerationIterations
                + " seedRouteUniverseSize="
                + routeUniverse.size());
    }

    private NodeLpSolve solveNodeLpWithSeparatedRows(GurobiRmp rmp) throws GRBException {
        GurobiRmp.SolveResult solve = rmp.solveLp();
        if ((!cutPropagationPolicy.separateSubsetRowsAtNodes()
                && robustCandidateRows.isEmpty()
                && !separateRobustTwoPathRows
                && !separateRobustRoundedCapacityRows)
                || !solve.isOptimal()) {
            return new NodeLpSolve(solve, List.of());
        }
        ArrayList<MasterCutRow> separatedRows = new ArrayList<MasterCutRow>();
        while (true) {
            int added = 0;
            if (!robustCandidateRows.isEmpty()) {
                List<RobustCutRow> violatedRobust = RobustCutSeparator.violatedRows(
                        rmp.instance(),
                        rmp.solutionValues(),
                        rmp.cutRows(),
                        robustCandidateRows,
                        tolerance);
                for (RobustCutRow row : violatedRobust) {
                    rmp.addCutRow(row);
                    separatedRows.add(row);
                    added++;
                }
            }
            if (added == 0 && !rmp.hasPositiveArtificial()) {
                if (separateRobustTwoPathRows) {
                    List<RobustCutRow> generatedRobust = RobustCutCandidateGenerator.violatedTwoPathRequestSetRows(
                            rmp.instance(),
                            rmp.solutionValues(),
                            rmp.cutRows(),
                            tolerance);
                    for (RobustCutRow row : generatedRobust) {
                        rmp.addCutRow(row);
                        separatedRows.add(row);
                        added++;
                    }
                }
                if (separateRobustRoundedCapacityRows) {
                    List<RobustCutRow> generatedRobust =
                            RobustCutCandidateGenerator.violatedRoundedCapacityRequestSetRows(
                                    rmp.instance(),
                                    rmp.solutionValues(),
                                    rmp.cutRows(),
                                    tolerance);
                    for (RobustCutRow row : generatedRobust) {
                        rmp.addCutRow(row);
                        separatedRows.add(row);
                        added++;
                    }
                }
            }
            if (added > 0) {
                solve = rmp.solveLp();
                if (!solve.isOptimal()) {
                    return new NodeLpSolve(solve, separatedRows);
                }
                continue;
            }
            if (cutPropagationPolicy.separateSubsetRowsAtNodes()) {
                List<SubsetRowCutRow> violated = SubsetRowCutSeparator.violatedL2Triples(
                        rmp.instance(),
                        rmp.solutionValues(),
                        rmp.cutRows(),
                        tolerance);
                for (SubsetRowCutRow row : violated) {
                    rmp.addCutRow(row);
                    separatedRows.add(row);
                    added++;
                }
            }
            if (added == 0) {
                return new NodeLpSolve(solve, separatedRows);
            }
            solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                return new NodeLpSolve(solve, separatedRows);
            }
        }
    }

    private void publishSeparatedCutRows(
            List<MasterCutRow> activeCutRows,
            List<MasterCutRow> separatedCutRows) {
        if (!cutPropagationPolicy.publishSeparatedRowsToGlobalPool() || separatedCutRows.isEmpty()) {
            return;
        }
        for (MasterCutRow row : separatedCutRows) {
            if (!(row instanceof SubsetRowCutRow)) {
                throw new IllegalStateException("global SR cut pool received non-SR row: " + row.name());
            }
            SubsetRowCutRow subsetRow = (SubsetRowCutRow) row;
            if (!hasEquivalentSubsetRow(activeCutRows, subsetRow)) {
                activeCutRows.add(subsetRow);
            }
        }
    }

    private static boolean hasEquivalentSubsetRow(List<MasterCutRow> cutRows, SubsetRowCutRow candidate) {
        for (MasterCutRow row : cutRows) {
            if (row.name().equals(candidate.name())) {
                return true;
            }
            if (row instanceof SubsetRowCutRow) {
                SubsetRowCutRow existing = (SubsetRowCutRow) row;
                if (existing.template().l() == candidate.template().l()
                        && existing.template().requestMask() == candidate.template().requestMask()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int activeCutCount(GurobiRmp rmp, BranchNode node) {
        return rmp.cutRows().size() - node.constraints().size();
    }

    private void rejectRobustActiveRowsWithGlobalSr(List<MasterCutRow> activeCutRows) {
        if (!cutPropagationPolicy.publishSeparatedRowsToGlobalPool()) {
            return;
        }
        for (MasterCutRow row : activeCutRows) {
            if (row instanceof RobustCutRow) {
                throw new IllegalArgumentException("GLOBAL_AUTOMATIC_SR does not support active robust cut rows; "
                        + "use node-local programmatic SR/robust tests or no automatic cuts");
            }
        }
    }

    private void seedBranchFeasibilityColumns(
            Instance instance,
            BranchNode node,
            GurobiRmp rmp,
            List<RouteColumn> routeUniverse) throws GRBException {
        for (BranchConstraint constraint : node.constraints()) {
            if (constraint.sense() != BranchConstraint.Sense.GREATER_OR_EQUAL) {
                continue;
            }
            BranchMasterRow row = BranchMasterRow.of(constraint);
            double seeded = 0.0;
            double target = cutPropagationPolicy.separateSubsetRowsAtNodes()
                    ? Math.max(1.0, constraint.rhs())
                    : 1.0;
            boolean seedAllCandidates = cutPropagationPolicy.separateSubsetRowsAtNodes();
            for (RouteColumn column : routeUniverse) {
                double coefficient = row.routeCoefficient(instance, column);
                if (coefficient > 0.0 && rmp.addColumn(column)) {
                    seeded += coefficient;
                    if (!seedAllCandidates && seeded + tolerance >= target) {
                        break;
                    }
                } else if (!seedAllCandidates && seeded + tolerance >= target) {
                    break;
                }
            }
        }
    }

    private static List<BranchDecision.RouteValue> branchSolution(
            Instance instance,
            List<GurobiRmp.ColumnValue> values) {
        ArrayList<BranchDecision.RouteValue> result = new ArrayList<BranchDecision.RouteValue>();
        for (GurobiRmp.ColumnValue value : values) {
            RouteColumn column = value.column();
            if (column.isArtificial()) {
                continue;
            }
            result.add(BranchDecision.RouteValue.withRequestVisits(
                    column.name(),
                    value.value(),
                    column.fleetCoefficient(),
                    BranchMasterRow.requestVisitSequence(instance, column)));
        }
        return List.copyOf(result);
    }

    private void ensureNodeConstraintsSatisfied(
            BranchNode node,
            List<BranchDecision.RouteValue> solution) {
        for (BranchConstraint constraint : node.constraints()) {
            if (!constraint.isSatisfied(solution, tolerance)) {
                throw new IllegalStateException("node LP solution violates inherited branch constraint "
                        + constraint.expression());
            }
        }
    }

    private static List<RouteColumn> copyRouteUniverse(Collection<RouteColumn> routeUniverse) {
        Objects.requireNonNull(routeUniverse, "routeUniverse");
        ArrayList<RouteColumn> copy = new ArrayList<RouteColumn>();
        for (RouteColumn column : routeUniverse) {
            Objects.requireNonNull(column, "routeUniverse contains null");
            if (column.isRealRoute()) {
                copy.add(column);
            }
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("routeUniverse must contain at least one real route column");
        }
        return List.copyOf(copy);
    }

    private static List<MasterCutRow> copyCutRows(Collection<? extends MasterCutRow> activeCutRows) {
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        ArrayList<MasterCutRow> copy = new ArrayList<MasterCutRow>();
        for (MasterCutRow cutRow : activeCutRows) {
            MasterCutRow row = Objects.requireNonNull(cutRow, "activeCutRows contains null");
            if (row instanceof BranchMasterRow) {
                throw new IllegalArgumentException("activeCutRows must not contain inherited branch rows");
            }
            copy.add(row);
        }
        return List.copyOf(copy);
    }

    private static double requireTolerance(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + value);
        }
        return value;
    }

    private static double requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite: " + value);
        }
        return value;
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    public static final class Result {
        private final String status;
        private final int processedNodes;
        private final int createdNodes;
        private final int routeUniverseSize;
        private final List<NodeRecord> nodeRecords;
        private final double incumbentObjective;
        private final List<RouteColumn> incumbentColumns;

        private Result(
                String status,
                int processedNodes,
                int createdNodes,
                int routeUniverseSize,
                List<NodeRecord> nodeRecords,
                double incumbentObjective,
                List<RouteColumn> incumbentColumns) {
            this.status = Objects.requireNonNull(status, "status");
            this.processedNodes = processedNodes;
            this.createdNodes = createdNodes;
            this.routeUniverseSize = routeUniverseSize;
            this.nodeRecords = Collections.unmodifiableList(new ArrayList<NodeRecord>(nodeRecords));
            this.incumbentObjective = incumbentObjective;
            this.incumbentColumns = Collections.unmodifiableList(new ArrayList<RouteColumn>(incumbentColumns));
        }

        public String status() {
            return status;
        }

        public int processedNodes() {
            return processedNodes;
        }

        public int createdNodes() {
            return createdNodes;
        }

        public int routeUniverseSize() {
            return routeUniverseSize;
        }

        public List<NodeRecord> nodeRecords() {
            return nodeRecords;
        }

        public boolean hasIncumbent() {
            return Double.isFinite(incumbentObjective);
        }

        public double incumbentObjective() {
            return incumbentObjective;
        }

        public List<RouteColumn> incumbentColumns() {
            return incumbentColumns;
        }

        public double rootLowerBound() {
            if (nodeRecords.isEmpty()) {
                return Double.NaN;
            }
            return nodeRecords.get(0).lowerBound();
        }

        public int totalPricingCalls() {
            int total = 0;
            for (NodeRecord record : nodeRecords) {
                total += record.pricingCalls();
            }
            return total;
        }

        public int totalGeneratedColumns() {
            int total = 0;
            for (NodeRecord record : nodeRecords) {
                total += record.generatedColumns();
            }
            return total;
        }

        public long totalPricingTimeMs() {
            long total = 0L;
            for (NodeRecord record : nodeRecords) {
                total += record.pricingTimeMs();
            }
            return total;
        }

        public int totalForwardLabels() {
            int total = 0;
            for (NodeRecord record : nodeRecords) {
                if (record.forwardLabels() < 0) {
                    if (record.pricingCalls() > 0) {
                        return -1;
                    }
                    continue;
                }
                total += record.forwardLabels();
            }
            return total;
        }

        public int totalBackwardLabels() {
            int total = 0;
            for (NodeRecord record : nodeRecords) {
                if (record.backwardLabels() < 0) {
                    if (record.pricingCalls() > 0) {
                        return -1;
                    }
                    continue;
                }
                total += record.backwardLabels();
            }
            return total;
        }

        public int totalDominatedLabels() {
            int total = 0;
            for (NodeRecord record : nodeRecords) {
                if (record.dominatedLabels() < 0) {
                    if (record.pricingCalls() > 0) {
                        return -1;
                    }
                    continue;
                }
                total += record.dominatedLabels();
            }
            return total;
        }

        public int totalPricedColumns() {
            int total = 0;
            for (NodeRecord record : nodeRecords) {
                total += record.pricedColumns();
            }
            return total;
        }
    }

    public static final class NodeRecord {
        private final String nodeId;
        private final int depth;
        private final List<String> constraints;
        private final double lowerBound;
        private final double incumbentUpperBound;
        private final double bestReducedCost;
        private final int pricingCalls;
        private final int pricedColumns;
        private final int generatedColumns;
        private final int forwardLabels;
        private final int backwardLabels;
        private final int dominatedLabels;
        private final long pricingTimeMs;
        private final int activeCutCount;
        private final String pruneReason;
        private final BranchConstraint.Type branchType;

        private NodeRecord(
                String nodeId,
                int depth,
                List<String> constraints,
                double lowerBound,
                double incumbentUpperBound,
                double bestReducedCost,
                int pricingCalls,
                int pricedColumns,
                int generatedColumns,
                int forwardLabels,
                int backwardLabels,
                int dominatedLabels,
                long pricingTimeMs,
                int activeCutCount,
                String pruneReason,
                BranchConstraint.Type branchType) {
            this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
            this.depth = depth;
            this.constraints = Collections.unmodifiableList(new ArrayList<String>(constraints));
            this.lowerBound = lowerBound;
            this.incumbentUpperBound = incumbentUpperBound;
            this.bestReducedCost = bestReducedCost;
            this.pricingCalls = pricingCalls;
            this.pricedColumns = requireNonNegative(pricedColumns, "pricedColumns");
            this.generatedColumns = generatedColumns;
            this.forwardLabels = requireCounterOrUnknown(forwardLabels, "forwardLabels");
            this.backwardLabels = requireCounterOrUnknown(backwardLabels, "backwardLabels");
            this.dominatedLabels = requireCounterOrUnknown(dominatedLabels, "dominatedLabels");
            this.pricingTimeMs = requireNonNegative(pricingTimeMs, "pricingTimeMs");
            this.activeCutCount = requireNonNegative(activeCutCount, "activeCutCount");
            this.pruneReason = Objects.requireNonNull(pruneReason, "pruneReason");
            this.branchType = branchType;
        }

        private static NodeRecord of(
                BranchNode node,
                NodeRelaxation relaxation,
                double incumbentUpperBound,
                String pruneReason,
                BranchDecision decision) {
            ArrayList<String> constraints = new ArrayList<String>();
            for (BranchConstraint constraint : node.constraints()) {
                constraints.add(constraint.expression());
            }
            return new NodeRecord(
                    node.id(),
                    node.depth(),
                    constraints,
                    relaxation.lowerBound(),
                    incumbentUpperBound,
                    relaxation.pricingAudit().bestReducedCost(),
                    relaxation.pricingAudit().pricingCalls(),
                    relaxation.pricingAudit().pricedColumns(),
                    relaxation.pricingAudit().generatedColumns(),
                    relaxation.pricingAudit().forwardLabels(),
                    relaxation.pricingAudit().backwardLabels(),
                    relaxation.pricingAudit().dominatedLabels(),
                    relaxation.pricingAudit().pricingTimeMs(),
                    relaxation.activeCutCount(),
                    pruneReason,
                    decision == null ? null : decision.type());
        }

        public String nodeId() {
            return nodeId;
        }

        public int depth() {
            return depth;
        }

        public List<String> constraints() {
            return constraints;
        }

        public double lowerBound() {
            return lowerBound;
        }

        public double incumbentUpperBound() {
            return incumbentUpperBound;
        }

        public double bestReducedCost() {
            return bestReducedCost;
        }

        public int pricingCalls() {
            return pricingCalls;
        }

        public int pricedColumns() {
            return pricedColumns;
        }

        public int generatedColumns() {
            return generatedColumns;
        }

        public int forwardLabels() {
            return forwardLabels;
        }

        public int backwardLabels() {
            return backwardLabels;
        }

        public int dominatedLabels() {
            return dominatedLabels;
        }

        public long pricingTimeMs() {
            return pricingTimeMs;
        }

        public int activeCutCount() {
            return activeCutCount;
        }

        public String pruneReason() {
            return pruneReason;
        }

        public Optional<BranchConstraint.Type> branchType() {
            return Optional.ofNullable(branchType);
        }
    }

    private static final class NodeRelaxation {
        private final double lowerBound;
        private final boolean hasPositiveArtificial;
        private final List<GurobiRmp.ColumnValue> solutionValues;
        private final List<BranchDecision.RouteValue> branchSolution;
        private final PricingAudit pricingAudit;
        private final int activeCutCount;
        private final int lpStatus;

        private NodeRelaxation(
                double lowerBound,
                boolean hasPositiveArtificial,
                List<GurobiRmp.ColumnValue> solutionValues,
                List<BranchDecision.RouteValue> branchSolution,
                PricingAudit pricingAudit,
                int activeCutCount,
                int lpStatus) {
            this.lowerBound = lowerBound;
            this.hasPositiveArtificial = hasPositiveArtificial;
            this.solutionValues = List.copyOf(solutionValues);
            this.branchSolution = List.copyOf(branchSolution);
            this.pricingAudit = pricingAudit;
            this.activeCutCount = requireNonNegative(activeCutCount, "activeCutCount");
            this.lpStatus = lpStatus;
        }

        private static NodeRelaxation optimal(
                double lowerBound,
                boolean hasPositiveArtificial,
                List<GurobiRmp.ColumnValue> solutionValues,
                List<BranchDecision.RouteValue> branchSolution,
                PricingAudit pricingAudit,
                int activeCutCount) {
            return new NodeRelaxation(
                    lowerBound,
                    hasPositiveArtificial,
                    solutionValues,
                    branchSolution,
                    pricingAudit,
                    activeCutCount,
                    0);
        }

        private static NodeRelaxation infeasible(int status, int activeCutCount) {
            return new NodeRelaxation(
                    Double.POSITIVE_INFINITY,
                    true,
                    List.of(),
                    List.of(),
                    new PricingAudit(Double.NaN, 0, List.of(), PricingResult.Stats.empty(), false, 0, 0, 0L),
                    activeCutCount,
                    status);
        }

        private double lowerBound() {
            return lowerBound;
        }

        private boolean hasPositiveArtificial() {
            return hasPositiveArtificial || lpStatus != 0;
        }

        private List<BranchDecision.RouteValue> branchSolution() {
            return branchSolution;
        }

        private PricingAudit pricingAudit() {
            return pricingAudit;
        }

        private int activeCutCount() {
            return activeCutCount;
        }

        private boolean integral(double tolerance) {
            if (hasPositiveArtificial()) {
                return false;
            }
            for (GurobiRmp.ColumnValue value : solutionValues) {
                if (value.column().isArtificial()) {
                    continue;
                }
                double lambda = value.value();
                if (Math.abs(lambda - Math.rint(lambda)) > tolerance) {
                    return false;
                }
            }
            return true;
        }

        private List<RouteColumn> selectedIntegerColumns() {
            ArrayList<RouteColumn> selected = new ArrayList<RouteColumn>();
            for (GurobiRmp.ColumnValue value : solutionValues) {
                if (value.column().isRealRoute() && value.value() > 0.5) {
                    selected.add(value.column());
                }
            }
            return selected;
        }
    }

    private static final class NodeLpSolve {
        private final GurobiRmp.SolveResult solveResult;
        private final List<MasterCutRow> separatedCutRows;

        private NodeLpSolve(GurobiRmp.SolveResult solveResult, List<? extends MasterCutRow> separatedCutRows) {
            this.solveResult = Objects.requireNonNull(solveResult, "solveResult");
            this.separatedCutRows = List.copyOf(Objects.requireNonNull(separatedCutRows, "separatedCutRows"));
        }

        private GurobiRmp.SolveResult solveResult() {
            return solveResult;
        }

        private List<MasterCutRow> separatedCutRows() {
            return separatedCutRows;
        }
    }

    private static final class PricingAudit {
        private final double bestReducedCost;
        private final int pricedColumns;
        private final List<RouteColumn> columns;
        private final int forwardLabels;
        private final int backwardLabels;
        private final int dominatedLabels;
        private final int pricingCalls;
        private final int generatedColumns;
        private final long pricingTimeMs;

        private PricingAudit(
                double bestReducedCost,
                int pricedColumns,
                List<RouteColumn> columns,
                PricingResult.Stats stats,
                boolean statsAvailable,
                int pricingCalls,
                int generatedColumns,
                long pricingTimeMs) {
            this.bestReducedCost = bestReducedCost;
            this.pricedColumns = requireNonNegative(pricedColumns, "pricedColumns");
            this.columns = List.copyOf(columns);
            Objects.requireNonNull(stats, "stats");
            this.forwardLabels = statsAvailable ? requireNonNegative(stats.generatedForwardLabels(), "forwardLabels") : -1;
            this.backwardLabels = statsAvailable ? requireNonNegative(stats.generatedBackwardLabels(), "backwardLabels") : -1;
            this.dominatedLabels = statsAvailable ? requireNonNegative(stats.dominatedLabels(), "dominatedLabels") : -1;
            this.pricingCalls = pricingCalls;
            this.generatedColumns = generatedColumns;
            this.pricingTimeMs = requireNonNegative(pricingTimeMs, "pricingTimeMs");
        }

        private double bestReducedCost() {
            return bestReducedCost;
        }

        private boolean hasColumns() {
            return !columns.isEmpty();
        }

        private List<RouteColumn> columns() {
            return columns;
        }

        private PricingAudit withTotals(
                int pricingCalls,
                int generatedColumns,
                int pricedColumns,
                int forwardLabels,
                int backwardLabels,
                int dominatedLabels,
                long pricingTimeMs) {
            return new PricingAudit(
                    bestReducedCost,
                    pricedColumns,
                    columns,
                    PricingResult.Stats.of(
                            Math.max(0, forwardLabels),
                            Math.max(0, backwardLabels),
                            0,
                            0,
                            0,
                            0,
                            Math.max(0, dominatedLabels)),
                    forwardLabels >= 0 && backwardLabels >= 0 && dominatedLabels >= 0,
                    pricingCalls,
                    generatedColumns,
                    pricingTimeMs);
        }

        private int pricedColumns() {
            return pricedColumns;
        }

        private int pricingCalls() {
            return pricingCalls;
        }

        private int generatedColumns() {
            return generatedColumns;
        }

        private int forwardLabels() {
            return forwardLabels;
        }

        private int backwardLabels() {
            return backwardLabels;
        }

        private int dominatedLabels() {
            return dominatedLabels;
        }

        private long pricingTimeMs() {
            return pricingTimeMs;
        }
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }

    private static int requireCounterOrUnknown(int value, String name) {
        if (value < -1) {
            throw new IllegalArgumentException(name + " must be non-negative or -1 for unknown: " + value);
        }
        return value;
    }
}
