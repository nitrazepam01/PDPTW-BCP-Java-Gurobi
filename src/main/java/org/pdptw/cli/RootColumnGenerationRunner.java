package org.pdptw.cli;

import com.gurobi.gurobi.GRBException;
import org.pdptw.core.Instance;
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
import org.pdptw.pricing.PricingMode;
import org.pdptw.pricing.PricingResult;
import org.pdptw.pricing.PricingSolver;
import org.pdptw.pricing.ReducedCostMatrices;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RootColumnGenerationRunner {
    public static final double DEFAULT_TOLERANCE = 1.0e-7;
    public static final int DEFAULT_MAX_ITERATIONS = 100;

    private final double artificialPenalty;
    private final double tolerance;
    private final int maxIterations;

    public RootColumnGenerationRunner() {
        this(ArtificialColumnFactory.DEFAULT_PENALTY, DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    public RootColumnGenerationRunner(double artificialPenalty, double tolerance, int maxIterations) {
        if (!Double.isFinite(artificialPenalty) || artificialPenalty <= 0.0) {
            throw new IllegalArgumentException("artificialPenalty must be positive and finite: " + artificialPenalty);
        }
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be positive: " + maxIterations);
        }
        this.artificialPenalty = artificialPenalty;
        this.tolerance = tolerance;
        this.maxIterations = maxIterations;
    }

    public RootCgResult run(Instance instance, PricingMode mode) throws GRBException {
        Objects.requireNonNull(mode, "mode");
        return run(instance, mode.name(), mode.createSolver(tolerance));
    }

    public RootCgResult run(
            Instance instance,
            PricingMode mode,
            List<? extends MasterCutRow> cutRows) throws GRBException {
        Objects.requireNonNull(mode, "mode");
        return run(instance, mode.name(), mode.createSolver(tolerance), cutRows);
    }

    public RootCgResult run(Instance instance, PricingSolver solver) throws GRBException {
        return run(instance, "CUSTOM", solver);
    }

    public RootCgResult run(Instance instance, String pricingMode, PricingSolver solver) throws GRBException {
        return run(instance, pricingMode, solver, List.of());
    }

    public RootCgResult run(
            Instance instance,
            String pricingMode,
            PricingSolver solver,
            List<? extends MasterCutRow> cutRows) throws GRBException {
        return run(instance, pricingMode, solver, cutRows, false, List.of());
    }

    public RootCgResult runWithRobustCutSeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver,
            List<? extends RobustCutRow> robustCandidateRows) throws GRBException {
        return runWithRobustCutSeparation(instance, pricingMode, solver, List.of(), robustCandidateRows);
    }

    public RootCgResult runWithRobustCutSeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver,
            List<? extends MasterCutRow> cutRows,
            List<? extends RobustCutRow> robustCandidateRows) throws GRBException {
        return run(instance, pricingMode, solver, cutRows, false, robustCandidateRows);
    }

    public RootCgResult runWithRobustTwoPathSeparation(
            Instance instance,
            PricingMode mode) throws GRBException {
        Objects.requireNonNull(mode, "mode");
        return runWithRobustTwoPathSeparation(instance, mode.name(), mode.createSolver(tolerance));
    }

    public RootCgResult runWithRobustTwoPathSeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver) throws GRBException {
        return run(instance, pricingMode, solver, List.of(), false, List.of(), true, false);
    }

    public RootCgResult runWithRobustTwoPathAndSubsetRowSeparation(
            Instance instance,
            PricingMode mode) throws GRBException {
        Objects.requireNonNull(mode, "mode");
        return runWithRobustTwoPathAndSubsetRowSeparation(
                instance,
                mode.name(),
                mode.createSolver(tolerance));
    }

    public RootCgResult runWithRobustTwoPathAndSubsetRowSeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver) throws GRBException {
        return run(instance, pricingMode, solver, List.of(), true, List.of(), true, false);
    }

    public RootCgResult runWithRobustRoundedCapacitySeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver) throws GRBException {
        return run(instance, pricingMode, solver, List.of(), false, List.of(), false, true);
    }

    public RootCgResult runWithRobustRoundedCapacityAndSubsetRowSeparation(
            Instance instance,
            PricingMode mode) throws GRBException {
        Objects.requireNonNull(mode, "mode");
        return runWithRobustRoundedCapacityAndSubsetRowSeparation(
                instance,
                mode.name(),
                mode.createSolver(tolerance));
    }

    public RootCgResult runWithRobustRoundedCapacityAndSubsetRowSeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver) throws GRBException {
        return run(instance, pricingMode, solver, List.of(), true, List.of(), false, true);
    }

    public RootCgResult runWithSubsetRowSeparation(
            Instance instance,
            PricingMode mode) throws GRBException {
        Objects.requireNonNull(mode, "mode");
        return runWithSubsetRowSeparation(instance, mode.name(), mode.createSolver(tolerance));
    }

    public RootCgResult runWithSubsetRowSeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver) throws GRBException {
        return run(instance, pricingMode, solver, List.of(), true, List.of());
    }

    public RootCgResult runWithSubsetRowSeparation(
            Instance instance,
            String pricingMode,
            PricingSolver solver,
            List<? extends MasterCutRow> cutRows) throws GRBException {
        return run(instance, pricingMode, solver, cutRows, true, List.of());
    }

    private RootCgResult run(
            Instance instance,
            String pricingMode,
            PricingSolver solver,
            List<? extends MasterCutRow> cutRows,
            boolean separateSubsetRows,
            List<? extends RobustCutRow> robustCandidateRows) throws GRBException {
        return run(instance, pricingMode, solver, cutRows, separateSubsetRows, robustCandidateRows, false, false);
    }

    private RootCgResult run(
            Instance instance,
            String pricingMode,
            PricingSolver solver,
            List<? extends MasterCutRow> cutRows,
            boolean separateSubsetRows,
            List<? extends RobustCutRow> robustCandidateRows,
            boolean separateRobustTwoPathRows,
            boolean separateRobustRoundedCapacityRows) throws GRBException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(solver, "solver");
        Objects.requireNonNull(cutRows, "cutRows");
        Objects.requireNonNull(robustCandidateRows, "robustCandidateRows");
        if (pricingMode == null || pricingMode.isBlank()) {
            throw new IllegalArgumentException("pricingMode must not be blank");
        }

        try (GurobiRmp rmp = new GurobiRmp(instance, artificialPenalty)) {
            for (MasterCutRow cutRow : cutRows) {
                rmp.addCutRow(cutRow);
            }
            int initialColumnCount = rmp.columnCount();
            ArrayList<RootCgResult.Iteration> iterations = new ArrayList<RootCgResult.Iteration>();

            for (int iteration = 0; iteration < maxIterations; iteration++) {
                GurobiRmp.SolveResult solve = solveLpWithSeparatedRows(
                        rmp,
                        separateSubsetRows,
                        robustCandidateRows,
                        separateRobustTwoPathRows,
                        separateRobustRoundedCapacityRows);
                if (!solve.isOptimal()) {
                    throw new IllegalStateException("Root RMP LP did not solve to optimality. status="
                            + solve.status());
                }

                DualSolution duals = rmp.dualSolution();
                ReducedCostMatrices matrices = ReducedCostMatrices.fromDualSolution(instance, duals);
                PricingContext pricingContext = PricingContext.withCuts(
                        matrices,
                        rmp.robustCutsFromDuals(),
                        rmp.subsetRowCutsFromDuals());
                long pricingStart = System.nanoTime();
                PricingResult pricing = solver.price(pricingContext);
                long pricingTimeMs = CliSupport.elapsedMs(pricingStart);
                if (!pricing.exact()) {
                    throw new IllegalStateException("Root CG cannot terminate or continue from inexact pricing: "
                            + pricing.status());
                }

                if (pricing.bestReducedCost() >= -tolerance) {
                    iterations.add(new RootCgResult.Iteration(
                            iteration,
                            solve.objectiveValue(),
                            pricing.bestReducedCost(),
                            0,
                            rmp.columnCount(),
                            rmp.cutRows().size(),
                            pricing.stats(),
                            pricingTimeMs,
                            pricing.exact(),
                            pricing.status()));
                    return new RootCgResult(
                            pricingMode,
                            iterations,
                            rmp.realColumns(),
                            solve.objectiveValue(),
                            initialColumnCount,
                            rmp.columnCount(),
                            rmp.hasPositiveArtificial(),
                            RootCgResult.EXACT_NO_NEGATIVE);
                }

                int addedColumns = addNegativeColumns(rmp, pricingContext, pricing);
                iterations.add(new RootCgResult.Iteration(
                        iteration,
                        solve.objectiveValue(),
                        pricing.bestReducedCost(),
                        addedColumns,
                        rmp.columnCount(),
                        rmp.cutRows().size(),
                        pricing.stats(),
                        pricingTimeMs,
                        pricing.exact(),
                        pricing.status()));
                if (addedColumns == 0) {
                    throw new IllegalStateException("Exact pricing found a negative reduced-cost column but no new "
                            + "column was added. bestReducedCost=" + pricing.bestReducedCost());
                }
            }
        }
        throw new IllegalStateException("Root column generation exceeded maxIterations=" + maxIterations);
    }

    private GurobiRmp.SolveResult solveLpWithSeparatedRows(
            GurobiRmp rmp,
            boolean separateSubsetRows,
            List<? extends RobustCutRow> robustCandidateRows,
            boolean separateRobustTwoPathRows,
            boolean separateRobustRoundedCapacityRows) throws GRBException {
        GurobiRmp.SolveResult solve = rmp.solveLp();
        if ((!separateSubsetRows
                && robustCandidateRows.isEmpty()
                && !separateRobustTwoPathRows
                && !separateRobustRoundedCapacityRows)
                || !solve.isOptimal()) {
            return solve;
        }
        while (true) {
            int added = addViolatedRobustCutRows(rmp, robustCandidateRows);
            if (added == 0 && !rmp.hasPositiveArtificial()) {
                if (separateRobustTwoPathRows) {
                    added += addViolatedGeneratedTwoPathRows(rmp);
                }
                if (separateRobustRoundedCapacityRows) {
                    added += addViolatedGeneratedRoundedCapacityRows(rmp);
                }
            }
            if (added > 0) {
                solve = rmp.solveLp();
                if (!solve.isOptimal()) {
                    return solve;
                }
                continue;
            }
            if (separateSubsetRows) {
                added += addViolatedSubsetRowCuts(rmp);
            }
            if (added == 0) {
                return solve;
            }
            solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                return solve;
            }
        }
    }

    private int addViolatedRobustCutRows(
            GurobiRmp rmp,
            List<? extends RobustCutRow> robustCandidateRows) throws GRBException {
        if (robustCandidateRows.isEmpty()) {
            return 0;
        }
        List<RobustCutRow> violated = RobustCutSeparator.violatedRows(
                rmp.instance(),
                rmp.solutionValues(),
                rmp.cutRows(),
                robustCandidateRows,
                tolerance);
        for (RobustCutRow row : violated) {
            rmp.addCutRow(row);
        }
        return violated.size();
    }

    private int addViolatedGeneratedTwoPathRows(GurobiRmp rmp) throws GRBException {
        List<RobustCutRow> violated = RobustCutCandidateGenerator.violatedTwoPathRequestSetRows(
                rmp.instance(),
                rmp.solutionValues(),
                rmp.cutRows(),
                tolerance);
        for (RobustCutRow row : violated) {
            rmp.addCutRow(row);
        }
        return violated.size();
    }

    private int addViolatedGeneratedRoundedCapacityRows(GurobiRmp rmp) throws GRBException {
        List<RobustCutRow> violated = RobustCutCandidateGenerator.violatedRoundedCapacityRequestSetRows(
                rmp.instance(),
                rmp.solutionValues(),
                rmp.cutRows(),
                tolerance);
        for (RobustCutRow row : violated) {
            rmp.addCutRow(row);
        }
        return violated.size();
    }

    private int addViolatedSubsetRowCuts(GurobiRmp rmp) throws GRBException {
        List<SubsetRowCutRow> violated = SubsetRowCutSeparator.violatedL2Triples(
                rmp.instance(),
                rmp.solutionValues(),
                rmp.cutRows(),
                tolerance);
        for (SubsetRowCutRow row : violated) {
            rmp.addCutRow(row);
        }
        return violated.size();
    }

    private int addNegativeColumns(GurobiRmp rmp, PricingContext context, PricingResult pricing) throws GRBException {
        int added = 0;
        for (RouteColumn column : pricing.columns()) {
            if (context.directReducedCost(column.route()) < -tolerance && rmp.addColumn(column)) {
                added++;
            }
        }
        return added;
    }
}
