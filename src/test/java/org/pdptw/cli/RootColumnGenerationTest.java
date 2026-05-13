package org.pdptw.cli;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.RouteChecker;
import org.pdptw.core.Vertex;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.cuts.RobustCut;
import org.pdptw.cuts.RobustCutCandidateGenerator;
import org.pdptw.cuts.RobustCutRow;
import org.pdptw.cuts.RobustCutSeparator;
import org.pdptw.cuts.RoundedCapacityCut;
import org.pdptw.cuts.SRPricingAdjuster;
import org.pdptw.cuts.SubsetRowCut;
import org.pdptw.cuts.SubsetRowCutRow;
import org.pdptw.cuts.SubsetRowCutSeparator;
import org.pdptw.io.TinyJsonReader;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingContext;
import org.pdptw.pricing.PricingMode;
import org.pdptw.pricing.PricingResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RootColumnGenerationTest {
    private static final double TOLERANCE = 1.0e-7;

    private RootColumnGenerationTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("RootColumnGenerationTest OK");
    }

    public static void run() throws Exception {
        Instance tinyA = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        RootColumnGenerationRunner runner = new RootColumnGenerationRunner(1000.0, TOLERANCE, 20);

        RootCgResult forward = runner.run(tinyA, PricingMode.FORWARD);
        RootCgResult backward = runner.run(tinyA, PricingMode.BACKWARD);
        RootCgResult bidirectional = runner.run(tinyA, PricingMode.BIDIR_STATIC);
        RootCgResult dynamic = runner.run(tinyA, PricingMode.BIDIR_DYNAMIC);

        assertRootCgResult("forward", forward);
        assertRootCgResult("backward", backward);
        assertRootCgResult("bidir-static", bidirectional);
        assertRootCgResult("bidir-dynamic", dynamic);
        assertRootCgAggregates("forward", forward);
        assertRootCgAggregates("backward", backward);
        assertRootCgAggregates("bidir-static", bidirectional);
        assertRootCgAggregates("bidir-dynamic", dynamic);
        if (bidirectional.totalForwardLabels() <= 0 || bidirectional.totalBackwardLabels() <= 0
                || dynamic.totalForwardLabels() <= 0 || dynamic.totalBackwardLabels() <= 0) {
            throw new AssertionError("bidirectional root CG summary counters must expose real label counts");
        }
        assertClose("forward/backward LP bound",
                forward.finalObjectiveValue(), backward.finalObjectiveValue());
        assertClose("forward/bidir-static LP bound",
                forward.finalObjectiveValue(), bidirectional.finalObjectiveValue());
        assertClose("bidir-static/bidir-dynamic LP bound",
                bidirectional.finalObjectiveValue(), dynamic.finalObjectiveValue());

        assertRejectsInexactPricing(tinyA);
        assertRobustCutRowsReachPricingContext(tinyA);
        assertRootCgUsesNonZeroRobustCutDualAndDirectRcShift();
        assertRoundedCapacityCandidateSeparationAddsViolatedCut();
        assertRobustCandidateSeparationAddsViolatedCut();
        assertRobustTwoPathSeparationAddsGeneratedCut();
        assertGeneratedTwoPathRowsCoexistWithSubsetRowSeparation();
        assertRobustGeneratorsSkipArtificialAndSatisfiedSolutions();
        assertSubsetRowSeparationAddsViolatedCut();
        assertSubsetRowSeparatorSkipsDuplicatesAndNoViolation();
        assertSubsetRowSeparationSkipsSemanticDuplicateInitialRow();
        assertRobustRowsCoexistWithSubsetRowSeparation();
        assertGeneratedRoundedCapacityRowsCoexistWithSubsetRowSeparation();
    }

    private static void assertRootCgResult(String label, RootCgResult result) {
        if (!result.terminatedByExactNoNegative()) {
            throw new AssertionError(label + " must terminate by exact no-negative pricing, got "
                    + result.terminationReason());
        }
        if (result.iterationCount() < 2) {
            throw new AssertionError(label + " should include a pricing-add iteration and a no-negative iteration");
        }
        assertClose(label + " final LP objective", 8.0, result.finalObjectiveValue());
        if (result.hasPositiveArtificial()) {
            throw new AssertionError(label + " should not leave positive artificial columns in the final LP");
        }
        if (result.realColumnCount() == 0) {
            throw new AssertionError(label + " should add real route columns");
        }
        if (result.initialColumnCount() != 2) {
            throw new AssertionError(label + " should start with one artificial column per request");
        }
        RootCgResult.Iteration first = result.iterations().get(0);
        if (first.addedColumns() <= 0) {
            throw new AssertionError(label + " first iteration should add negative reduced-cost columns");
        }
        RootCgResult.Iteration last = result.iterations().get(result.iterations().size() - 1);
        if (!last.exactPricing() || last.bestReducedCost() < -TOLERANCE || last.addedColumns() != 0) {
            throw new AssertionError(label + " final iteration must be exact no-negative pricing");
        }
        assertAllIterationsExact(label, result.iterations());
    }

    private static void assertRootCgAggregates(String label, RootCgResult result) {
        long forwardLabels = 0L;
        long backwardLabels = 0L;
        long dominatedLabels = 0L;
        long pricingTimeMs = 0L;
        for (RootCgResult.Iteration iteration : result.iterations()) {
            forwardLabels += iteration.pricingStats().generatedForwardLabels();
            backwardLabels += iteration.pricingStats().generatedBackwardLabels();
            dominatedLabels += iteration.pricingStats().dominatedLabels();
            pricingTimeMs += iteration.pricingTimeMs();
        }
        assertEquals(label + " total forward labels", forwardLabels, result.totalForwardLabels());
        assertEquals(label + " total backward labels", backwardLabels, result.totalBackwardLabels());
        assertEquals(label + " total dominated labels", dominatedLabels, result.totalDominatedLabels());
        assertEquals(label + " total pricing time", pricingTimeMs, result.totalPricingTimeMs());
        assertEquals(label + " final active cuts",
                result.iterations().get(result.iterations().size() - 1).cutsActive(),
                result.finalCutsActive());
    }

    private static void assertRejectsInexactPricing(Instance instance) throws Exception {
        RootColumnGenerationRunner runner = new RootColumnGenerationRunner(1000.0, TOLERANCE, 5);
        try {
            runner.run(instance, matrices -> PricingResult.of(List.of(), 0.0, false, "heuristic_no_column"));
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Root CG must reject inexact pricing termination");
    }

    private static void assertRobustCutRowsReachPricingContext(Instance instance) throws Exception {
        List<Integer> routeIds = List.of(0, 1, 2, 3, 4, 5);
        Route route = new Route(routeIds);
        RouteColumn column = RouteColumn.fromRoute("robust_negative", route, instance);
        RobustCutRow cutRow = robustToyCutRow();
        RecordingPricingSolver solver = new RecordingPricingSolver(column);

        RootColumnGenerationRunner runner = new RootColumnGenerationRunner(1000.0, TOLERANCE, 3);
        RootCgResult result = runner.run(
                instance,
                "ROBUST_TOY",
                solver,
                List.of(cutRow));

        if (solver.calls < 2) {
            throw new AssertionError("robust toy pricing should be called at least twice");
        }
        if (!solver.sawRobustContext) {
            throw new AssertionError("root CG must pass active robust cut rows into PricingContext");
        }
        if (solver.firstRobustReducedCost >= -TOLERANCE) {
            throw new AssertionError("toy robust cut should make the route robust-negative on first pricing call");
        }
        if (!result.terminatedByExactNoNegative()) {
            throw new AssertionError("robust toy root CG should terminate by exact no-negative pricing");
        }
        if (result.realColumnCount() != 1) {
            throw new AssertionError("robust toy root CG should add the one robust-negative column");
        }
        if (result.finalCutsActive() != 1) {
            throw new AssertionError("robust toy root CG should report one active cut");
        }
        for (RootCgResult.Iteration iteration : result.iterations()) {
            if (iteration.cutsActive() != 1) {
                throw new AssertionError("robust toy iteration should report one active cut"
                        + " iteration=" + iteration.index()
                        + " cutsActive=" + iteration.cutsActive());
            }
        }
        assertRootTraceCutCount("robust toy", TraceCsv.rootCg(instance.name(), result), result.iterationCount(), 1);
        BenchmarkCsv.Row summary = RunRootCg.summaryRow(instance, PricingMode.BIDIR_STATIC, result, 0L);
        assertEquals("robust toy summary active cuts", 1, summary.cuts());
        String[] summaryRows = BenchmarkCsv.rowsToCsv(List.of(summary)).split("\\R", -1);
        String[] summaryFields = summaryRows[1].split(",", -1);
        if (!"1".equals(summaryFields[8])) {
            throw new AssertionError("robust toy summary CSV cuts field expected 1 but got "
                    + summaryFields[8]);
        }
    }

    private static void assertRootCgUsesNonZeroRobustCutDualAndDirectRcShift() throws Exception {
        Instance instance = robustShiftInstance();
        List<RouteColumn> routeUniverse = robustShiftColumns(instance);
        FiniteRoutePricingSolver noCutSolver = new FiniteRoutePricingSolver(routeUniverse);
        FiniteRoutePricingSolver solver = new FiniteRoutePricingSolver(routeUniverse);

        RootCgResult noCut = new RootColumnGenerationRunner(10000.0, TOLERANCE, 6)
                .run(instance, "ROBUST_SHIFT_NO_CUT", noCutSolver, List.of());
        RootCgResult active = new RootColumnGenerationRunner(10000.0, TOLERANCE, 6)
                .run(instance, "ROBUST_SHIFT_ACTIVE", solver, List.of(robustShiftCutRow()));

        if (noCutSolver.calls <= 0 || solver.calls <= 0) {
            throw new AssertionError("robust root-CG audit should exercise no-cut and active-cut pricing contexts");
        }
        if (noCutSolver.sawRobustContext || noCutSolver.sawNonZeroRobustDual
                || noCutSolver.sawRobustReducedCostShift) {
            throw new AssertionError("no-cut root CG must not observe robust cut pricing effects");
        }
        if (!active.terminatedByExactNoNegative()) {
            throw new AssertionError("active robust root CG must terminate by exact no-negative pricing");
        }
        if (active.finalCutsActive() != 1) {
            throw new AssertionError("active robust root CG should finish with one active cut, got "
                    + active.finalCutsActive());
        }
        if (!solver.sawNonZeroRobustDual) {
            throw new AssertionError("active robust root CG should produce a nonzero robust pricing dual");
        }
        if (!solver.sawRobustReducedCostShift) {
            throw new AssertionError("active robust root CG should change route direct reduced cost");
        }
        if (!solver.sawRobustSeedPositiveShift) {
            throw new AssertionError("active robust root CG should increase RC for the positive-coefficient route");
        }
        if (!solver.sawRobustOnlyNegativeShift) {
            throw new AssertionError("active robust root CG should decrease RC for the negative-coefficient route");
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(instance.name(), noCut), 0, 0);
        assertRootTraceCutCount("active robust shift", TraceCsv.rootCg(instance.name(), active),
                active.iterationCount(), 1);
    }

    private static void assertRobustCandidateSeparationAddsViolatedCut() throws Exception {
        Instance instance = robustShiftInstance();
        RobustCutRow candidate = robustShiftCutRow();
        RobustCutRow semanticDuplicate = robustShiftCutRow("custom_initial_robust_shift_probe");
        List<RouteColumn> routeUniverse = robustShiftColumns(instance);

        try (GurobiRmp rmp = new GurobiRmp(instance, 10000.0)) {
            rmp.addColumn(routeUniverse.get(0));
            GurobiRmp.SolveResult solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                throw new AssertionError("robust separator probe LP should solve");
            }
            List<RobustCutRow> violated = RobustCutSeparator.violatedRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(),
                    List.of(candidate),
                    TOLERANCE);
            assertEquals("robust separator finds the violated candidate", 1, violated.size());
            assertClose("robust separator activity",
                    1.0,
                    RobustCutSeparator.activity(instance, candidate, rmp.solutionValues()));
            List<RobustCutRow> duplicateFiltered = RobustCutSeparator.violatedRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(candidate),
                    List.of(candidate),
                    TOLERANCE);
            assertEquals("robust separator skips active candidate", 0, duplicateFiltered.size());
            List<RobustCutRow> semanticDuplicateFiltered = RobustCutSeparator.violatedRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(semanticDuplicate),
                    List.of(candidate),
                    TOLERANCE);
            assertEquals("robust separator skips active same row with custom name",
                    0,
                    semanticDuplicateFiltered.size());
            List<RobustCutRow> candidateDuplicateFiltered = RobustCutSeparator.violatedRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(),
                    List.of(candidate, semanticDuplicate),
                    TOLERANCE);
            assertEquals("robust separator skips duplicate candidate semantics",
                    1,
                    candidateDuplicateFiltered.size());
            List<RobustCutRow> noViolation = RobustCutSeparator.violatedRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(),
                    List.of(nonViolatedRobustShiftCutRow()),
                    TOLERANCE);
            assertEquals("robust separator skips non-violated candidate", 0, noViolation.size());
        }

        FiniteRoutePricingSolver noCutSolver = new FiniteRoutePricingSolver(routeUniverse);
        FiniteRoutePricingSolver separatedSolver = new FiniteRoutePricingSolver(routeUniverse);
        FiniteRoutePricingSolver semanticDuplicateSolver = new FiniteRoutePricingSolver(routeUniverse);
        RootCgResult noCut = new RootColumnGenerationRunner(10000.0, TOLERANCE, 6)
                .run(instance, "ROBUST_SEPARATION_NO_CUT", noCutSolver, List.of());
        RootCgResult separated = new RootColumnGenerationRunner(10000.0, TOLERANCE, 6)
                .runWithRobustCutSeparation(
                        instance,
                        "ROBUST_SEPARATION",
                        separatedSolver,
                        List.of(candidate));
        RootCgResult semanticDuplicateGuard = new RootColumnGenerationRunner(10000.0, TOLERANCE, 6)
                .runWithRobustCutSeparation(
                        instance,
                        "ROBUST_SEMANTIC_DUPLICATE_GUARD",
                        semanticDuplicateSolver,
                        List.of(semanticDuplicate),
                        List.of(candidate));

        if (noCutSolver.sawRobustContext || noCutSolver.sawNonZeroRobustDual
                || noCutSolver.sawRobustReducedCostShift) {
            throw new AssertionError("no-cut robust separation contrast must not observe robust pricing effects");
        }
        if (!separated.terminatedByExactNoNegative()) {
            throw new AssertionError("robust-separated root CG must terminate by exact no-negative pricing");
        }
        if (separated.finalCutsActive() != 1) {
            throw new AssertionError("robust-separated root CG should finish with one active cut, got "
                    + separated.finalCutsActive());
        }
        if (!separatedSolver.sawNonZeroRobustDual || !separatedSolver.sawRobustReducedCostShift) {
            throw new AssertionError("robust-separated root CG should pass a nonzero robust dual into pricing");
        }
        if (!semanticDuplicateGuard.terminatedByExactNoNegative()) {
            throw new AssertionError("robust semantic duplicate guard root CG must terminate exactly");
        }
        assertEquals("robust semantic duplicate guard final cuts", 1, semanticDuplicateGuard.finalCutsActive());
        for (RootCgResult.Iteration iteration : semanticDuplicateGuard.iterations()) {
            assertEquals("robust semantic duplicate guard iteration cuts", 1, iteration.cutsActive());
        }
        if (!semanticDuplicateSolver.sawRobustContext || !semanticDuplicateSolver.sawNonZeroRobustDual) {
            throw new AssertionError("robust semantic duplicate guard should price with the initial robust row");
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(instance.name(), noCut), 0, 0);
        assertRootTraceCutSequence(TraceCsv.rootCg(instance.name(), separated), 0, 1);
        assertRootTraceCutCount("robust semantic duplicate guard",
                TraceCsv.rootCg(instance.name(), semanticDuplicateGuard),
                semanticDuplicateGuard.iterationCount(),
                1);
    }

    private static void assertRoundedCapacityCandidateSeparationAddsViolatedCut() throws Exception {
        Instance instance = twoPathSeparationInstance();
        List<RouteColumn> columns = roundedCapacityPairColumns(instance);

        try (GurobiRmp rmp = new GurobiRmp(instance, 1000.0)) {
            for (RouteColumn column : columns) {
                rmp.addColumn(column);
            }
            GurobiRmp.SolveResult solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                throw new AssertionError("rounded-capacity generator LP should solve");
            }
            if (rmp.hasPositiveArtificial()) {
                throw new AssertionError("rounded-capacity generator fixture should be covered by real pair columns");
            }
            List<RobustCutRow> violated = RobustCutCandidateGenerator.violatedRoundedCapacityRequestSetRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(),
                    TOLERANCE);
            assertEquals("rounded-capacity generator finds one violated row", 1, violated.size());
            RobustCutRow row = violated.get(0);
            if (!"robust-rcap-R1-2-3".equals(row.name())) {
                throw new AssertionError("unexpected rounded-capacity row name: " + row.name());
            }
            if (row.sense() != MasterCutRow.Sense.GREATER_EQUAL) {
                throw new AssertionError("rounded-capacity row must be >= sense");
            }
            assertClose("rounded-capacity row RHS", 2.0, row.rhs());
            assertClose("rounded-capacity pickup-internal arc", 0.0, row.arcCoefficient(instance, 1, 2));
            assertClose("rounded-capacity pickup-exit arc", 1.0, row.arcCoefficient(instance, 1, 4));
            assertClose("rounded-capacity depot-entry arc", 0.0, row.arcCoefficient(instance, 0, 1));
            assertClose("rounded-capacity delivery arc", 0.0, row.arcCoefficient(instance, 4, 5));
            assertClose("rounded-capacity fractional activity",
                    1.5,
                    RobustCutSeparator.activity(instance, row, rmp.solutionValues()));
            for (RouteColumn column : columns) {
                assertClose("rounded-capacity pair route leaves pickup set once",
                        1.0,
                        row.routeCoefficient(instance, column));
            }
            List<RobustCutRow> duplicate = RobustCutCandidateGenerator.violatedRoundedCapacityRequestSetRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(row),
                    TOLERANCE);
            assertEquals("rounded-capacity generator skips active row", 0, duplicate.size());
            RobustCutRow semanticDuplicate =
                    RoundedCapacityCut.requestSetRow(instance, "custom-rcap-R1-2-3", List.of(3, 1, 2, 1));
            List<RobustCutRow> semanticDuplicateFiltered =
                    RobustCutCandidateGenerator.violatedRoundedCapacityRequestSetRows(
                            instance,
                            rmp.solutionValues(),
                            List.of(semanticDuplicate),
                            TOLERANCE);
            assertEquals("rounded-capacity generator skips active same row with custom name",
                    0,
                    semanticDuplicateFiltered.size());
        }

        FiniteRoutePricingSolver noCutSolver = new FiniteRoutePricingSolver(columns);
        RootCgResult noCut = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .run(instance, "ROUNDED_CAPACITY_NO_CUT", noCutSolver);
        if (noCut.finalCutsActive() != 0 || noCutSolver.sawRobustContext) {
            throw new AssertionError("rounded-capacity no-cut contrast should not observe robust cuts");
        }

        FiniteRoutePricingSolver separatedSolver = new FiniteRoutePricingSolver(columns);
        RootCgResult separated = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .runWithRobustRoundedCapacitySeparation(
                        instance,
                        "ROUNDED_CAPACITY_ROBUST",
                        separatedSolver);
        if (!separated.terminatedByExactNoNegative()) {
            throw new AssertionError("rounded-capacity robust root CG should terminate by exact no-negative pricing");
        }
        assertEquals("rounded-capacity robust final active cuts", 1, separated.finalCutsActive());
        if (!separatedSolver.sawRobustContext
                || !separatedSolver.sawNonZeroRobustDual
                || !separatedSolver.sawRobustReducedCostShift) {
            throw new AssertionError("rounded-capacity separation should pass nonzero robust pricing context");
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(instance.name(), separated), 0, 1);
    }

    private static void assertRobustTwoPathSeparationAddsGeneratedCut() throws Exception {
        Instance instance = twoPathSeparationInstance();
        List<RouteColumn> columns = twoPathPairColumns(instance);
        RouteChecker.Result fullRoute = new RouteChecker().check(
                instance,
                Route.of(0, 1, 4, 2, 5, 3, 6, 7));
        if (fullRoute.feasible() || !RouteChecker.TIME_WINDOW.equals(fullRoute.reason())) {
            throw new AssertionError("two-path fixture should make all three requests infeasible in one route"
                    + " reason=" + fullRoute.reason());
        }

        try (GurobiRmp rmp = new GurobiRmp(instance, 1000.0)) {
            for (RouteColumn column : columns) {
                rmp.addColumn(column);
            }
            GurobiRmp.SolveResult solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                throw new AssertionError("two-path generator LP should solve");
            }
            if (rmp.hasPositiveArtificial()) {
                throw new AssertionError("two-path generator fixture should be covered by real pair columns");
            }
            List<RobustCutRow> violated = RobustCutCandidateGenerator.violatedTwoPathRequestSetRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(),
                    TOLERANCE);
            assertEquals("two-path generator finds one violated row", 1, violated.size());
            RobustCutRow row = violated.get(0);
            if (!"robust-2path-R1-2-3".equals(row.name())) {
                throw new AssertionError("unexpected two-path row name: " + row.name());
            }
            if (row.sense() != MasterCutRow.Sense.GREATER_EQUAL) {
                throw new AssertionError("two-path row must be >= sense");
            }
            assertClose("two-path row RHS", 2.0, row.rhs());
            assertClose("two-path fractional activity",
                    1.5,
                    RobustCutSeparator.activity(instance, row, rmp.solutionValues()));
            for (RouteColumn column : columns) {
                assertClose("two-path pair route leaves request set once",
                        1.0,
                        row.routeCoefficient(instance, column));
            }
            List<RobustCutRow> duplicate = RobustCutCandidateGenerator.violatedTwoPathRequestSetRows(
                    instance,
                    rmp.solutionValues(),
                    List.of(row),
                    TOLERANCE);
            assertEquals("two-path generator skips active row", 0, duplicate.size());
        }

        FiniteRoutePricingSolver noCutSolver = new FiniteRoutePricingSolver(columns);
        RootCgResult noCut = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .run(instance, "TWO_PATH_NO_CUT", noCutSolver);
        if (noCut.finalCutsActive() != 0 || noCutSolver.sawRobustContext) {
            throw new AssertionError("two-path no-cut contrast should not observe robust cuts");
        }

        FiniteRoutePricingSolver separatedSolver = new FiniteRoutePricingSolver(columns);
        RootCgResult separated = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .runWithRobustTwoPathSeparation(instance, "TWO_PATH_ROBUST", separatedSolver);
        if (!separated.terminatedByExactNoNegative()) {
            throw new AssertionError("two-path robust root CG should terminate by exact no-negative pricing");
        }
        assertEquals("two-path robust final active cuts", 1, separated.finalCutsActive());
        if (!separatedSolver.sawRobustContext
                || !separatedSolver.sawNonZeroRobustDual
                || !separatedSolver.sawRobustReducedCostShift) {
            throw new AssertionError("two-path robust separation should pass nonzero robust pricing context");
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(instance.name(), separated), 0, 1);
    }

    private static void assertRobustGeneratorsSkipArtificialAndSatisfiedSolutions() throws Exception {
        Instance artificialInstance = twoPathSeparationInstance();
        NoNegativePricingSolver twoPathArtificialSolver = new NoNegativePricingSolver();
        RootCgResult twoPathArtificial = new RootColumnGenerationRunner(1000.0, TOLERANCE, 3)
                .runWithRobustTwoPathSeparation(
                        artificialInstance,
                        "TWO_PATH_ARTIFICIAL_GUARD",
                        twoPathArtificialSolver);
        if (!twoPathArtificial.hasPositiveArtificial()) {
            throw new AssertionError("two-path artificial guard should terminate with positive artificial columns");
        }
        assertEquals("two-path artificial guard final cuts", 0, twoPathArtificial.finalCutsActive());
        assertEquals("two-path artificial guard pricing calls", 1, twoPathArtificialSolver.calls);
        if (twoPathArtificialSolver.sawRobustContext) {
            throw new AssertionError("two-path generator must not add robust cuts while artificial columns are positive");
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(artificialInstance.name(), twoPathArtificial), 0);

        NoNegativePricingSolver roundedArtificialSolver = new NoNegativePricingSolver();
        RootCgResult roundedArtificial = new RootColumnGenerationRunner(1000.0, TOLERANCE, 3)
                .runWithRobustRoundedCapacitySeparation(
                        artificialInstance,
                        "ROUNDED_CAPACITY_ARTIFICIAL_GUARD",
                        roundedArtificialSolver);
        if (!roundedArtificial.hasPositiveArtificial()) {
            throw new AssertionError("rounded-capacity artificial guard should terminate with positive artificial columns");
        }
        assertEquals("rounded-capacity artificial guard final cuts", 0, roundedArtificial.finalCutsActive());
        assertEquals("rounded-capacity artificial guard pricing calls", 1, roundedArtificialSolver.calls);
        if (roundedArtificialSolver.sawRobustContext) {
            throw new AssertionError("rounded-capacity generator must not add robust cuts while artificial columns"
                    + " are positive");
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(artificialInstance.name(), roundedArtificial), 0);

        Instance satisfiedInstance = robustGeneratorSatisfiedInstance();
        List<RouteColumn> columns = robustGeneratorSatisfiedColumns(satisfiedInstance);
        try (GurobiRmp rmp = new GurobiRmp(satisfiedInstance, 1000.0)) {
            for (RouteColumn column : columns) {
                rmp.addColumn(column);
            }
            GurobiRmp.SolveResult solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                throw new AssertionError("robust generator satisfied LP should solve");
            }
            if (rmp.hasPositiveArtificial()) {
                throw new AssertionError("robust generator satisfied LP should be covered by real routes");
            }

            RobustCutRow twoPathRow = RobustCutCandidateGenerator.twoPathRequestSetRow(
                    satisfiedInstance,
                    List.of(1, 2, 3));
            assertClose("two-path satisfied-row activity",
                    3.0,
                    RobustCutSeparator.activity(satisfiedInstance, twoPathRow, rmp.solutionValues()));
            List<RobustCutRow> twoPathViolated = RobustCutCandidateGenerator.violatedTwoPathRequestSetRows(
                    satisfiedInstance,
                    rmp.solutionValues(),
                    List.of(),
                    TOLERANCE);
            assertEquals("two-path generator skips satisfied row", 0, twoPathViolated.size());

            RobustCutRow roundedRow = RoundedCapacityCut.requestSetRow(satisfiedInstance, List.of(1, 2, 3));
            assertClose("rounded-capacity satisfied-row activity",
                    3.0,
                    RobustCutSeparator.activity(satisfiedInstance, roundedRow, rmp.solutionValues()));
            List<RobustCutRow> roundedViolated =
                    RobustCutCandidateGenerator.violatedRoundedCapacityRequestSetRows(
                            satisfiedInstance,
                            rmp.solutionValues(),
                            List.of(),
                            TOLERANCE);
            assertEquals("rounded-capacity generator skips satisfied row", 0, roundedViolated.size());
        }
    }

    private static void assertSubsetRowSeparationAddsViolatedCut() throws Exception {
        Instance instance = srSeparationInstance();
        FiniteRoutePricingSolver solver = new FiniteRoutePricingSolver(List.of(
                RouteColumn.fromRoute("sr_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("sr_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("sr_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("sr_full_123", Route.of(0, 1, 2, 3, 4, 5, 6, 7), instance)));

        RootCgResult result = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .runWithSubsetRowSeparation(instance, "SR_SEPARATION", solver);

        if (!solver.sawSubsetRowContext) {
            throw new AssertionError("SR separation must add a cut row before final pricing");
        }
        if (!solver.sawNonZeroSubsetRowDual) {
            throw new AssertionError("SR separation should produce a nonzero SR pricing dual");
        }
        if (!solver.sawSubsetRowReducedCostShift) {
            throw new AssertionError("SR separation should change at least one route direct reduced cost");
        }
        if (!result.terminatedByExactNoNegative()) {
            throw new AssertionError("SR-separated root CG must terminate by exact no-negative pricing");
        }
        if (result.finalCutsActive() != 1) {
            throw new AssertionError("SR-separated root CG should finish with one active SR cut, got "
                    + result.finalCutsActive());
        }
        if (result.iterations().size() < 2
                || result.iterations().get(0).cutsActive() != 0
                || result.iterations().get(result.iterations().size() - 1).cutsActive() != 1) {
            throw new AssertionError("SR-separated root CG should add the violated cut after route columns exist");
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(instance.name(), result), 0, 1);
    }

    private static void assertRobustRowsCoexistWithSubsetRowSeparation() throws Exception {
        Instance instance = srSeparationInstance();
        FiniteRoutePricingSolver solver = new FiniteRoutePricingSolver(List.of(
                RouteColumn.fromRoute("mixed_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("mixed_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("mixed_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("mixed_full_123", Route.of(0, 1, 2, 3, 4, 5, 6, 7), instance)));

        RootCgResult result = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .runWithSubsetRowSeparation(
                        instance,
                        "MIXED_ROBUST_SR_SEPARATION",
                        solver,
                        List.of(srSeparationRobustProbeRow()));

        if (solver.calls < 2) {
            throw new AssertionError("mixed root CG should price before and after SR separation");
        }
        if (!solver.firstRobustWithoutSubsetRowContext) {
            throw new AssertionError("mixed root CG first pricing context should contain only the initial robust row");
        }
        if (!solver.lastRobustContext || !solver.lastSubsetRowContext) {
            throw new AssertionError("mixed root CG final pricing context should contain robust and SR cuts together");
        }
        if (!solver.sawNonZeroSubsetRowDual) {
            throw new AssertionError("mixed root CG should produce a nonzero SR pricing dual");
        }
        if (!solver.sawSubsetRowReducedCostShift) {
            throw new AssertionError("mixed root CG should keep SR pricing adjustments active");
        }
        if (result.finalCutsActive() != 2) {
            throw new AssertionError("mixed root CG should finish with robust + SR cuts active, got "
                    + result.finalCutsActive());
        }
        assertRootTraceCutSequence(TraceCsv.rootCg(instance.name(), result), 1, 2);
    }

    private static void assertGeneratedRoundedCapacityRowsCoexistWithSubsetRowSeparation() throws Exception {
        Instance roundedCapacityInstance = generatedRoundedCapacitySubsetRowSeparationInstance();
        FiniteRoutePricingSolver roundedCapacitySolver =
                new FiniteRoutePricingSolver(generatedRoundedCapacitySubsetRowColumns(roundedCapacityInstance));
        RootCgResult roundedCapacityResult = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .runWithRobustRoundedCapacityAndSubsetRowSeparation(
                        roundedCapacityInstance,
                        "GENERATED_RCC_SR",
                        roundedCapacitySolver);
        assertGeneratedRobustSubsetRowRootCg(
                "generated rounded-capacity robust + SR",
                roundedCapacityInstance,
                roundedCapacityResult,
                roundedCapacitySolver);
    }

    private static void assertGeneratedTwoPathRowsCoexistWithSubsetRowSeparation() throws Exception {
        Instance twoPathInstance = generatedTwoPathSubsetRowSeparationInstance();
        FiniteRoutePricingSolver twoPathSolver =
                new FiniteRoutePricingSolver(generatedTwoPathSubsetRowColumns(twoPathInstance));
        RootCgResult twoPathResult = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .runWithRobustTwoPathAndSubsetRowSeparation(
                        twoPathInstance,
                        "GENERATED_TWO_PATH_SR",
                        twoPathSolver);
        assertGeneratedRobustSubsetRowRootCg(
                "generated two-path robust + SR",
                twoPathInstance,
                twoPathResult,
                twoPathSolver);
    }

    private static void assertGeneratedRobustSubsetRowRootCg(
            String label,
            Instance instance,
            RootCgResult result,
            FiniteRoutePricingSolver solver) {
        if (!result.terminatedByExactNoNegative()) {
            throw new AssertionError(label + " root CG must terminate by exact no-negative pricing");
        }
        if (result.iterations().size() < 2) {
            throw new AssertionError(label + " should have a no-cut pricing iteration before generated cuts");
        }
        assertEquals(label + " first iteration cuts", 0, result.iterations().get(0).cutsActive());
        if (result.finalCutsActive() != 2) {
            throw new AssertionError(label + " final active cuts expected 2 but got "
                    + result.finalCutsActive()
                    + " lastRobustContext=" + solver.lastRobustContext
                    + " lastSubsetRowContext=" + solver.lastSubsetRowContext
                    + " sawRobustDual=" + solver.sawNonZeroRobustDual
                    + " sawSubsetDual=" + solver.sawNonZeroSubsetRowDual);
        }
        assertEquals(label + " final iteration cuts",
                2,
                result.iterations().get(result.iterations().size() - 1).cutsActive());
        if (!solver.lastRobustContext || !solver.lastSubsetRowContext) {
            throw new AssertionError(label + " final pricing context should contain generated robust and SR cuts");
        }
        if (!solver.sawNonZeroRobustDual || !solver.sawNonZeroSubsetRowDual) {
            throw new AssertionError(label + " should expose nonzero robust and SR duals");
        }
        if (!solver.sawRobustReducedCostShift || !solver.sawSubsetRowReducedCostShift) {
            throw new AssertionError(label + " should audit robust and SR direct-RC shifts");
        }
        String trace = TraceCsv.rootCg(instance.name(), result);
        String[] rows = trace.split("\\R", -1);
        String[] finalFields = rows[rows.length - 1].split(",", -1);
        if (!"2".equals(finalFields[8])) {
            throw new AssertionError(label + " trace final active cuts expected 2 but got " + finalFields[8]);
        }
    }

    private static void assertSubsetRowSeparatorSkipsDuplicatesAndNoViolation() throws Exception {
        Instance instance = srSeparationInstance();
        try (GurobiRmp rmp = new GurobiRmp(instance, 1000.0)) {
            rmp.addColumn(RouteColumn.fromRoute("sep_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance));
            rmp.addColumn(RouteColumn.fromRoute("sep_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance));
            rmp.addColumn(RouteColumn.fromRoute("sep_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance));
            GurobiRmp.SolveResult solve = rmp.solveLp();
            if (!solve.isOptimal()) {
                throw new AssertionError("SR separator duplicate guard LP should solve");
            }

            List<SubsetRowCutRow> violated = SubsetRowCutSeparator.violatedL2Triples(
                    instance,
                    rmp.solutionValues(),
                    List.of(),
                    TOLERANCE);
            assertEquals("SR separator finds one violated L2 triple", 1, violated.size());
            if (!"sr-U1-2-3-l2".equals(violated.get(0).name())) {
                throw new AssertionError("SR separator canonical cut name expected sr-U1-2-3-l2 but got "
                        + violated.get(0).name());
            }
            assertClose("SR separator fractional activity",
                    1.5,
                    SubsetRowCutSeparator.activity(instance, violated.get(0), rmp.solutionValues()));

            List<SubsetRowCutRow> duplicateFiltered = SubsetRowCutSeparator.violatedL2Triples(
                    instance,
                    rmp.solutionValues(),
                    List.of(SubsetRowCutRow.ofL2Triple("sr-U1-2-3-l2", 1, 2, 3)),
                    TOLERANCE);
            assertEquals("SR separator skips active canonical row", 0, duplicateFiltered.size());

            List<SubsetRowCutRow> semanticDuplicateFiltered = SubsetRowCutSeparator.violatedL2Triples(
                    instance,
                    rmp.solutionValues(),
                    List.of(SubsetRowCutRow.ofL2Triple("custom-name-same-U123", 1, 2, 3)),
                    TOLERANCE);
            assertEquals("SR separator skips active same triple with custom name",
                    0,
                    semanticDuplicateFiltered.size());
        }

        SubsetRowCutRow canonical = SubsetRowCutRow.ofL2Triple("sr-U1-2-3-l2", 1, 2, 3);
        try (GurobiRmp fullRouteOnly = new GurobiRmp(instance, 1000.0)) {
            fullRouteOnly.addColumn(RouteColumn.fromRoute(
                    "sep_full_123",
                    Route.of(0, 1, 2, 3, 4, 5, 6, 7),
                    instance));
            GurobiRmp.SolveResult solve = fullRouteOnly.solveLp();
            if (!solve.isOptimal()) {
                throw new AssertionError("SR separator full-route LP should solve");
            }
            assertClose("SR separator full-route activity equals RHS",
                    1.0,
                    SubsetRowCutSeparator.activity(instance, canonical, fullRouteOnly.solutionValues()));
            List<SubsetRowCutRow> violated = SubsetRowCutSeparator.violatedL2Triples(
                    instance,
                    fullRouteOnly.solutionValues(),
                    List.of(),
                    TOLERANCE);
            assertEquals("SR separator does not cut at activity equal to RHS", 0, violated.size());
        }

        try (GurobiRmp artificialOnly = new GurobiRmp(instance, 1000.0)) {
            GurobiRmp.SolveResult solve = artificialOnly.solveLp();
            if (!solve.isOptimal()) {
                throw new AssertionError("SR separator artificial-only LP should solve");
            }
            List<SubsetRowCutRow> violated = SubsetRowCutSeparator.violatedL2Triples(
                    instance,
                    artificialOnly.solutionValues(),
                    List.of(),
                    TOLERANCE);
            assertEquals("SR separator ignores artificial-only solution", 0, violated.size());
        }
    }

    private static void assertSubsetRowSeparationSkipsSemanticDuplicateInitialRow() throws Exception {
        Instance instance = srSeparationInstance();
        FiniteRoutePricingSolver solver = new FiniteRoutePricingSolver(List.of(
                RouteColumn.fromRoute("semantic_dup_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("semantic_dup_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("semantic_dup_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("semantic_dup_full_123", Route.of(0, 1, 2, 3, 4, 5, 6, 7), instance)));
        SubsetRowCutRow initialSameTriple = SubsetRowCutRow.ofL2Triple(
                "custom_initial_sr_U123_l2",
                1,
                2,
                3);

        RootCgResult result = new RootColumnGenerationRunner(1000.0, TOLERANCE, 6)
                .runWithSubsetRowSeparation(
                        instance,
                        "SR_SEMANTIC_DUPLICATE_GUARD",
                        solver,
                        List.of(initialSameTriple));

        if (!solver.sawSubsetRowContext) {
            throw new AssertionError("semantic duplicate SR root CG should price with the initial SR row");
        }
        assertEquals("semantic duplicate SR final active cuts", 1, result.finalCutsActive());
        for (RootCgResult.Iteration iteration : result.iterations()) {
            assertEquals("semantic duplicate SR iteration active cuts", 1, iteration.cutsActive());
        }
        if (!result.terminatedByExactNoNegative()) {
            throw new AssertionError("semantic duplicate SR root CG must still terminate exactly");
        }
    }

    private static RobustCutRow robustToyCutRow() {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 2), 1.0);
        coefficients.put(new RobustCut.Arc(3, 4), 1.0);
        return RobustCutRow.ofArcCoefficients(
                "root_cg_robust_toy",
                MasterCutRow.Sense.LESS_EQUAL,
                1.0,
                coefficients);
    }

    private static RobustCutRow robustShiftCutRow() {
        return robustShiftCutRow("root_cg_robust_shift_probe");
    }

    private static RobustCutRow robustShiftCutRow(String name) {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 2), 1.0);
        coefficients.put(new RobustCut.Arc(0, 2), -1.0);
        return RobustCutRow.ofArcCoefficients(
                name,
                MasterCutRow.Sense.LESS_EQUAL,
                0.5,
                coefficients);
    }

    private static RobustCutRow nonViolatedRobustShiftCutRow() {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 2), 1.0);
        coefficients.put(new RobustCut.Arc(0, 2), -1.0);
        return RobustCutRow.ofArcCoefficients(
                "root_cg_robust_shift_nonviolated",
                MasterCutRow.Sense.LESS_EQUAL,
                2.0,
                coefficients);
    }

    private static Instance robustShiftInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.DELIVERY, 1, 3.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(4, Vertex.Type.DELIVERY, 2, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DEPOT_END, 0, 5.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = denseMatrix(6, 10000.0);
        setRouteArcCosts(matrix, 2.0, 0, 1, 2, 3, 4, 5);
        matrix[0][2] = 1000.0;
        matrix[2][1] = 1000.0;
        matrix[1][3] = 496.0;
        return new Instance(
                "robust-shift-root-cg",
                2,
                2,
                1,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static Instance twoPathSeparationInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 6.0, 0.0, 0));
        double[][] matrix = denseMatrix(8, 1.0);
        return new Instance(
                "robust-two-path-test",
                3,
                2,
                2,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> twoPathPairColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("two_path_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("two_path_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("two_path_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance));
    }

    private static List<RouteColumn> roundedCapacityPairColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("rounded_capacity_pair_12", Route.of(0, 1, 2, 4, 5, 7), instance),
                RouteColumn.fromRoute("rounded_capacity_pair_13", Route.of(0, 1, 3, 4, 6, 7), instance),
                RouteColumn.fromRoute("rounded_capacity_pair_23", Route.of(0, 2, 3, 5, 6, 7), instance));
    }

    private static Instance robustGeneratorSatisfiedInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 6.0, 0.0, 0));
        double[][] matrix = denseMatrix(8, 1.0);
        return new Instance(
                "robust-generator-satisfied-test",
                3,
                2,
                3,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> robustGeneratorSatisfiedColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("satisfied_single_1", Route.of(0, 1, 4, 7), instance),
                RouteColumn.fromRoute("satisfied_single_2", Route.of(0, 2, 5, 7), instance),
                RouteColumn.fromRoute("satisfied_single_3", Route.of(0, 3, 6, 7), instance));
    }

    private static List<RouteColumn> robustShiftColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("robust_seed", Route.of(0, 1, 2, 3, 4, 5), instance),
                RouteColumn.fromRoute("robust_only_priced_with_cut", Route.of(0, 2, 1, 3, 4, 5), instance));
    }

    private static RobustCutRow srSeparationRobustProbeRow() {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(0, 1), 1.0);
        return RobustCutRow.ofArcCoefficients(
                "sr_separation_robust_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                2.0,
                coefficients);
    }

    private static Instance srSeparationInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 6.0, 0.0, 0));
        double[][] matrix = denseMatrix(8, 100.0);
        setRouteArcCosts(matrix, 2.0, 0, 1, 4, 2, 5, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1, 4, 3, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 2, 5, 3, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1);
        setRouteArcCosts(matrix, 14.0 / 6.0, 1, 2, 3, 4, 5, 6, 7);
        return new Instance(
                "sr-separation-test",
                3,
                3,
                3,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static Instance generatedRoundedCapacitySubsetRowSeparationInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = denseMatrix(8, 100.0);
        setRouteArcCosts(matrix, 1.0, 0, 1, 2, 4, 5, 7);
        setRouteArcCosts(matrix, 1.0, 0, 1, 3, 4, 6, 7);
        setRouteArcCosts(matrix, 1.0, 0, 2, 3, 5, 6, 7);
        matrix[0][3] = 50.0;
        return new Instance(
                "root-generated-rcc-sr",
                3,
                2,
                2,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static Instance generatedTwoPathSubsetRowSeparationInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 6.0, 0.0, 0));
        double[][] matrix = denseMatrix(8, 100.0);
        setRouteArcCosts(matrix, 1.0, 0, 1, 4, 2, 5, 7);
        setRouteArcCosts(matrix, 1.0, 0, 1, 4, 3, 6, 7);
        setRouteArcCosts(matrix, 1.0, 0, 2, 5, 3, 6, 7);
        matrix[0][3] = 50.0;
        return new Instance(
                "root-generated-two-path-sr",
                3,
                2,
                2,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> generatedTwoPathSubsetRowColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("generated_two_path_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("generated_two_path_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("generated_two_path_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("generated_two_path_single_3", Route.of(0, 3, 6, 7), instance));
    }

    private static List<RouteColumn> generatedRoundedCapacitySubsetRowColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("generated_rcc_pair_12", Route.of(0, 1, 2, 4, 5, 7), instance),
                RouteColumn.fromRoute("generated_rcc_pair_13", Route.of(0, 1, 3, 4, 6, 7), instance),
                RouteColumn.fromRoute("generated_rcc_pair_23", Route.of(0, 2, 3, 5, 6, 7), instance),
                RouteColumn.fromRoute("generated_rcc_single_3", Route.of(0, 3, 6, 7), instance));
    }

    private static double[][] denseMatrix(int size, double defaultValue) {
        double[][] matrix = new double[size][size];
        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                matrix[from][to] = from == to ? 0.0 : defaultValue;
            }
        }
        return matrix;
    }

    private static void setRouteArcCosts(double[][] matrix, double arcCost, int... route) {
        for (int index = 0; index + 1 < route.length; index++) {
            matrix[route[index]][route[index + 1]] = arcCost;
        }
    }

    private static void assertAllIterationsExact(String label, List<RootCgResult.Iteration> iterations) {
        for (RootCgResult.Iteration iteration : iterations) {
            if (!iteration.exactPricing()) {
                throw new AssertionError(label + " iteration " + iteration.index()
                        + " used inexact pricing status=" + iteration.pricingStatus());
            }
        }
    }

    private static void assertRootTraceCutCount(
            String label,
            String trace,
            int expectedIterations,
            int expectedCutsActive) {
        String[] rows = trace.split("\\R", -1);
        if (rows.length != expectedIterations + 1) {
            throw new AssertionError(label + " root trace row count expected "
                    + (expectedIterations + 1) + " but got " + rows.length);
        }
        if (!TraceCsv.ROOT_CG_HEADER.equals(rows[0])) {
            throw new AssertionError(label + " root trace header mismatch");
        }
        for (int index = 1; index < rows.length; index++) {
            String[] fields = rows[index].split(",", -1);
            if (fields.length != TraceCsv.ROOT_CG_HEADER.split(",", -1).length) {
                throw new AssertionError(label + " root trace row has wrong field count: " + rows[index]);
            }
            int cutsActive = Integer.parseInt(fields[8]);
            if (cutsActive != expectedCutsActive) {
                throw new AssertionError(label + " root trace cutsActive expected "
                        + expectedCutsActive + " but got " + cutsActive + " row=" + rows[index]);
            }
        }
    }

    private static void assertRootTraceCutSequence(String trace, int... expectedCutsActive) {
        String[] rows = trace.split("\\R", -1);
        if (!TraceCsv.ROOT_CG_HEADER.equals(rows[0])) {
            throw new AssertionError("root trace header mismatch");
        }
        if (rows.length != expectedCutsActive.length + 1) {
            throw new AssertionError("root trace cut sequence row count expected "
                    + expectedCutsActive.length + " but got " + (rows.length - 1)
                    + " trace=" + trace);
        }
        for (int index = 1; index < rows.length; index++) {
            String[] fields = rows[index].split(",", -1);
            int cutsActive = Integer.parseInt(fields[8]);
            int expected = expectedCutsActive[index - 1];
            if (cutsActive != expected) {
                throw new AssertionError("root trace cutsActive expected "
                        + expected + " but got " + cutsActive + " row=" + rows[index]);
            }
        }
    }

    private static Path referencePath(String name) {
        Path[] candidates = new Path[] {
                Path.of("..", "references", "tiny", name),
                Path.of("references", "tiny", name),
                Path.of("G:\\bid\\references\\tiny", name)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate tiny reference file: " + name);
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static double robustArcPriceSum(PricingContext context, RouteColumn column) {
        double sum = 0.0;
        List<Integer> vertexIds = column.vertexIds();
        for (int index = 0; index + 1 < vertexIds.size(); index++) {
            int from = vertexIds.get(index).intValue();
            int to = vertexIds.get(index + 1).intValue();
            for (RobustCut cut : context.robustCuts()) {
                sum += cut.arcPrice(context.instance(), from, to);
            }
        }
        return sum;
    }

    private static double subsetRowPriceSum(PricingContext context, RouteColumn column) {
        double sum = 0.0;
        for (SubsetRowCut cut : context.subsetRowCuts()) {
            sum += SRPricingAdjuster.routePricingAdjustment(cut, context.instance(), column.vertexIds());
        }
        return sum;
    }

    private static final class RecordingPricingSolver implements org.pdptw.pricing.PricingSolver {
        private final RouteColumn column;
        private int calls;
        private boolean sawRobustContext;
        private double firstRobustReducedCost = Double.NaN;

        private RecordingPricingSolver(RouteColumn column) {
            this.column = column;
        }

        @Override
        public PricingResult price(org.pdptw.pricing.ReducedCostMatrices matrices) {
            return price(PricingContext.noCuts(matrices));
        }

        @Override
        public PricingResult price(PricingContext context) {
            calls++;
            sawRobustContext |= context.hasRobustCuts();
            double reducedCost = context.directReducedCost(column.route());
            if (calls == 1) {
                firstRobustReducedCost = reducedCost;
            }
            if (reducedCost < -TOLERANCE) {
                return PricingResult.exact(List.of(column), reducedCost);
            }
            return PricingResult.noNegativeColumn(reducedCost);
        }
    }

    private static final class NoNegativePricingSolver implements org.pdptw.pricing.PricingSolver {
        private int calls;
        private boolean sawRobustContext;

        @Override
        public PricingResult price(org.pdptw.pricing.ReducedCostMatrices matrices) {
            return price(PricingContext.noCuts(matrices));
        }

        @Override
        public PricingResult price(PricingContext context) {
            calls++;
            sawRobustContext |= context.hasRobustCuts();
            return PricingResult.noNegativeColumn(0.0);
        }
    }

    private static final class FiniteRoutePricingSolver implements org.pdptw.pricing.PricingSolver {
        private final List<RouteColumn> routeUniverse;
        private int calls;
        private boolean sawRobustContext;
        private boolean sawNonZeroRobustDual;
        private boolean sawRobustReducedCostShift;
        private boolean sawRobustSeedPositiveShift;
        private boolean sawRobustOnlyNegativeShift;
        private boolean sawSubsetRowContext;
        private boolean sawNonZeroSubsetRowDual;
        private boolean sawSubsetRowReducedCostShift;
        private boolean firstRobustWithoutSubsetRowContext;
        private boolean lastRobustContext;
        private boolean lastSubsetRowContext;

        private FiniteRoutePricingSolver(List<RouteColumn> routeUniverse) {
            this.routeUniverse = List.copyOf(routeUniverse);
        }

        @Override
        public PricingResult price(org.pdptw.pricing.ReducedCostMatrices matrices) {
            return price(PricingContext.noCuts(matrices));
        }

        @Override
        public PricingResult price(PricingContext context) {
            calls++;
            if (calls == 1) {
                firstRobustWithoutSubsetRowContext = context.hasRobustCuts() && !context.hasSubsetRowCuts();
            }
            sawRobustContext |= context.hasRobustCuts();
            lastRobustContext = context.hasRobustCuts();
            lastSubsetRowContext = context.hasSubsetRowCuts();
            sawSubsetRowContext |= context.hasSubsetRowCuts();
            PricingContext noCuts = PricingContext.noCuts(context.matrices());
            PricingContext withoutSubsetRows = context.hasRobustCuts()
                    ? PricingContext.withCuts(context.matrices(), context.robustCuts(), List.of())
                    : noCuts;
            if (context.hasRobustCuts()) {
                for (RobustCut cut : context.robustCuts()) {
                    sawNonZeroRobustDual |= Math.abs(cut.dualValue()) > TOLERANCE;
                }
            }
            if (context.hasSubsetRowCuts()) {
                for (org.pdptw.cuts.SubsetRowCut cut : context.subsetRowCuts()) {
                    sawNonZeroSubsetRowDual |= Math.abs(cut.sigma()) > TOLERANCE;
                }
            }
            double bestReducedCost = Double.POSITIVE_INFINITY;
            java.util.ArrayList<RouteColumn> negativeColumns = new java.util.ArrayList<RouteColumn>();
            for (RouteColumn column : routeUniverse) {
                double reducedCost = context.directReducedCost(column.route());
                if (context.hasRobustCuts()) {
                    double robustOnlyReducedCost = withoutSubsetRows.directReducedCost(column.route());
                    double robustShift = robustOnlyReducedCost - noCuts.directReducedCost(column.route());
                    double expectedRobustShift = robustArcPriceSum(context, column);
                    if (Math.abs(robustShift - expectedRobustShift) > TOLERANCE) {
                        throw new AssertionError("robust direct RC shift should equal robust arc-price sum"
                                + " route=" + column.name()
                                + " shift=" + robustShift
                                + " expected=" + expectedRobustShift);
                    }
                    if (Math.abs(robustShift) > TOLERANCE) {
                        sawRobustReducedCostShift = true;
                    }
                    if ("robust_seed".equals(column.name()) && robustShift > TOLERANCE) {
                        sawRobustSeedPositiveShift = true;
                    }
                    if ("robust_only_priced_with_cut".equals(column.name()) && robustShift < -TOLERANCE) {
                        sawRobustOnlyNegativeShift = true;
                    }
                }
                if (context.hasSubsetRowCuts()) {
                    double subsetRowShift = reducedCost - withoutSubsetRows.directReducedCost(column.route());
                    double expectedSubsetRowShift = subsetRowPriceSum(context, column);
                    if (Math.abs(subsetRowShift - expectedSubsetRowShift) > TOLERANCE) {
                        throw new AssertionError("SR direct RC shift should equal SR route pricing adjustment"
                                + " route=" + column.name()
                                + " shift=" + subsetRowShift
                                + " expected=" + expectedSubsetRowShift);
                    }
                    if (Math.abs(subsetRowShift) > TOLERANCE) {
                        sawSubsetRowReducedCostShift = true;
                    }
                }
                if (reducedCost < bestReducedCost) {
                    bestReducedCost = reducedCost;
                }
                if (reducedCost < -TOLERANCE) {
                    negativeColumns.add(column);
                }
            }
            if (negativeColumns.isEmpty()) {
                return PricingResult.noNegativeColumn(bestReducedCost);
            }
            return PricingResult.exact(negativeColumns, bestReducedCost);
        }
    }
}
