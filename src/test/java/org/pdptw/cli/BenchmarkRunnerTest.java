package org.pdptw.cli;

import org.pdptw.branch.BranchAndPriceSolver;
import org.pdptw.branch.BranchConstraint;
import org.pdptw.branch.BranchMasterRow;
import org.pdptw.branch.SetOutflowBrancher;
import org.pdptw.branch.VehicleCountBrancher;
import org.pdptw.branch.VehicleCountConstraint;
import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.Vertex;
import org.pdptw.cuts.DtiPtiRepair;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.cuts.RobustCutCandidateGenerator;
import org.pdptw.cuts.RobustCut;
import org.pdptw.cuts.RobustCutRow;
import org.pdptw.cuts.SRPricingAdjuster;
import org.pdptw.cuts.SubsetRowCutRow;
import org.pdptw.master.DualSolution;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingContext;
import org.pdptw.pricing.PricingMode;
import org.pdptw.pricing.PricingResult;
import org.pdptw.pricing.PricingSolver;
import org.pdptw.pricing.ReducedCostMatrices;
import org.pdptw.validation.BruteForcePricingOracle;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class BenchmarkRunnerTest {
    private static final double TOLERANCE = 1.0e-7;

    private BenchmarkRunnerTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("BenchmarkRunnerTest OK");
    }

    public static void run() throws Exception {
        assertStableCsvHeader();
        assertPricingAuditAndComparePricingAgree();
        assertTinyCComparePricingFailsClearly();
        assertTinyDBcpFailsClearly();
        assertRootCgAndBcpRecords();
        assertPricingAuditRejectsUnsupportedCuts();
        assertComparePricingRejectsUnsupportedCuts();
        assertRunRootCgRejectsUnsupportedCuts();
        assertBcpRejectsUnsupportedCuts();
        assertRunBcpSubsetRowCliPath();
        assertRunBcpRobustCliPath();
        assertTinyRobustCliRepairAudit();
        assertRunBcpMixedCutCliPath();
        assertMainCliRoutesPublicCommands();
        assertRunRootCgPricingTraceCounters();
        assertBcpRunnerProgrammaticActiveCutsReachNodeTrace();
        assertBcpRunnerRejectsBranchRowsAsActiveCutRows();
        assertBcpRunnerRouteUniverseRejectsBranchRowsAsActiveCutRows();
        assertBcpRunnerWithRootLabelingUsesProgrammaticSolver();
        assertBcpRunnerPricingOptionUsesLabeling();
        assertRunBcpDefaultTraceUsesRouteUniverseCounters();
        assertRunBcpExplicitRouteUniverseAliasTraceUsesRouteUniverseCounters();
        assertRunBcpBenchmarkTextUsesLabelingAndSubsetRows();
        assertRunBcpBenchmarkTextRejectsTinyOnlyBackends();
        assertInstanceSmokeParsesBenchmarkSamples();
        assertRootLpPricingSmokeParsesBenchmarkSamples();
        assertRootLpPricingSmokeSingleRequestPool();
        assertRootLpPricingSmokeThreeRequestControlledPool();
        assertRootFiniteCgSmokeRestrictedPool();
        assertBcpRunnerRootLabelingProcessesForcedBranchRows();
        assertBcpRunnerRootLabelingProcessesSetOutflowDescendants();
        assertBcpRunnerRootLabelingCombinesActiveCutsAndBranchRows();
        assertBcpRunnerRootLabelingUsesNonZeroRobustCutDual();
        assertBcpRunnerRootLabelingCombinesRobustAndSubsetRowCuts();
        assertBcpRunnerRootLabelingSeparatesSubsetRowsProgrammatically();
        assertBcpRunnerSubsetRowSeparationSkipsSemanticDuplicateActiveRow();
        assertBcpRunnerGlobalSubsetRowsPropagateThroughQueuedNodes();
        assertBcpRunnerSubsetRowSeparationCombinesActiveRobustCut();
        assertBcpRunnerRootLabelingSeparatesRobustTwoPathRowsProgrammatically();
        assertBcpRunnerRootLabelingCombinesGeneratedRobustAndSubsetRows();
        assertBcpRunnerRootLabelingSeparatesRobustRoundedCapacityRowsProgrammatically();
        assertBcpRunnerRootLabelingCombinesGeneratedRoundedCapacityAndSubsetRows();
        assertBcpRejectsUnsupportedBranching();
    }

    private static void assertInstanceSmokeParsesBenchmarkSamples() throws Exception {
        String rc = captureStdout(() -> RunInstanceSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString()
        }));
        if (!rc.startsWith(RunInstanceSmoke.SmokeRow.HEADER + System.lineSeparator())
                || !rc.contains("instance-smoke,AA30,RC,rc-text,30,62,15,")) {
            throw new AssertionError("instance-smoke must parse RC AA30 metadata: " + rc);
        }
        String rcReverse = captureStdout(() -> RunInstanceSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30_reverse").toString()
        }));
        if (!rcReverse.contains("instance-smoke,AA30_reverse,RC_reverse,rc-text,30,62,15,")) {
            throw new AssertionError("instance-smoke must classify RC reverse samples: " + rcReverse);
        }
        String ll = captureStdout(() -> RunInstanceSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "LL", "lc101.txt").toString()
        }));
        if (!ll.contains("instance-smoke,lc101.txt,LL,ll-text,53,108,200,25,")) {
            throw new AssertionError("instance-smoke must parse LL lc101 metadata: " + ll);
        }
        String llLong = captureStdout(() -> RunInstanceSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "LL", "lc201.txt").toString()
        }));
        if (!llLong.contains("instance-smoke,lc201.txt,LL,ll-text,51,104,700,25,")) {
            throw new AssertionError("instance-smoke must parse LL lc201 metadata: " + llLong);
        }
    }

    private static void assertRootLpPricingSmokeParsesBenchmarkSamples() throws Exception {
        assertRootLpPricingSmokeOutput(
                "AA30",
                Path.of("..", "PDPTW_instances", "RC", "AA30"),
                "RC",
                "rc-text",
                "30",
                "62",
                "15",
                "30");
        assertRootLpPricingSmokeOutput(
                "AA30_reverse",
                Path.of("..", "PDPTW_instances", "RC", "AA30_reverse"),
                "RC_reverse",
                "rc-text",
                "30",
                "62",
                "15",
                "30");
        assertRootLpPricingSmokeOutput(
                "lc101.txt",
                Path.of("..", "PDPTW_instances", "LL", "lc101.txt"),
                "LL",
                "ll-text",
                "53",
                "108",
                "200",
                "25");
        assertRootLpPricingSmokeOutput(
                "lc201.txt",
                Path.of("..", "PDPTW_instances", "LL", "lc201.txt"),
                "LL",
                "ll-text",
                "51",
                "104",
                "700",
                "25");
    }

    private static void assertRootLpPricingSmokeOutput(
            String expectedInstance,
            Path path,
            String expectedGroup,
            String expectedFormat,
            String expectedRequests,
            String expectedVertices,
            String expectedCapacity,
            String expectedMaxVehicles) throws Exception {
        String output = captureStdout(() -> RunRootLpPricingSmoke.main(new String[] {
                "--instance",
                path.toString()
        }));
        String[] lines = output.strip().split("\\R");
        assertEquals("root-lp-pricing-smoke header " + expectedInstance,
                RunRootLpPricingSmoke.ResultRow.HEADER,
                lines[0]);
        String[] columns = lines[1].split(",", -1);
        assertEquals("root-lp-pricing-smoke column count " + expectedInstance, 18, columns.length);
        assertEquals("root-lp-pricing-smoke mode " + expectedInstance, "root-lp-pricing-smoke", columns[0]);
        assertEquals("root-lp-pricing-smoke instance " + expectedInstance, expectedInstance, columns[1]);
        assertEquals("root-lp-pricing-smoke group " + expectedInstance, expectedGroup, columns[2]);
        assertEquals("root-lp-pricing-smoke format " + expectedInstance, expectedFormat, columns[3]);
        assertEquals("root-lp-pricing-smoke requests " + expectedInstance, expectedRequests, columns[4]);
        assertEquals("root-lp-pricing-smoke vertices " + expectedInstance, expectedVertices, columns[5]);
        assertEquals("root-lp-pricing-smoke capacity " + expectedInstance, expectedCapacity, columns[6]);
        assertEquals("root-lp-pricing-smoke max vehicles " + expectedInstance, expectedMaxVehicles, columns[7]);
        if (Integer.parseInt(columns[8]) < Integer.parseInt(expectedRequests)) {
            throw new AssertionError("root-lp-pricing-smoke seed columns should cover at least all requests: "
                    + output);
        }
        assertEquals("root-lp-pricing-smoke LP status " + expectedInstance, "optimal", columns[9]);
        if (!"restricted_lp_candidate_negative".equals(columns[10])
                && !"restricted_lp_no_candidate_negative".equals(columns[10])) {
            throw new AssertionError("root-lp-pricing-smoke status must stay restricted/non-exact: "
                    + output);
        }
        double objective = Double.parseDouble(columns[11]);
        if (!Double.isFinite(objective)) {
            throw new AssertionError("root-lp-pricing-smoke LP objective must be finite: " + output);
        }
        if (!"true".equals(columns[12]) && !"false".equals(columns[12])) {
            throw new AssertionError("root-lp-pricing-smoke positiveArtificial must be boolean: " + output);
        }
        int candidateRoutes = Integer.parseInt(columns[13]);
        int negativeCandidates = Integer.parseInt(columns[14]);
        if (candidateRoutes < Integer.parseInt(expectedRequests)) {
            throw new AssertionError("root-lp-pricing-smoke candidate pool should include all seed singles: "
                    + output);
        }
        if (negativeCandidates < 0 || negativeCandidates > candidateRoutes) {
            throw new AssertionError("root-lp-pricing-smoke negative candidate count must fit the pool: "
                    + output);
        }
        double bestReducedCost = Double.parseDouble(columns[15]);
        if (!Double.isFinite(bestReducedCost)) {
            throw new AssertionError("root-lp-pricing-smoke best reduced cost must be finite: " + output);
        }
        if ("NA".equals(columns[16])) {
            throw new AssertionError("root-lp-pricing-smoke should report a best candidate route: " + output);
        }
        if (Long.parseLong(columns[17]) < 0L) {
            throw new AssertionError("root-lp-pricing-smoke total time must be non-negative: " + output);
        }
    }

    private static void assertRootLpPricingSmokeSingleRequestPool() throws Exception {
        String output = captureStdout(() -> RunRootLpPricingSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-route-requests",
                "1"
        }));
        String[] columns = output.strip().split("\\R")[1].split(",", -1);
        assertEquals("root-lp-pricing-smoke single-request candidate count", "30", columns[13]);
        if (!columns[16].startsWith("0->") || !columns[16].endsWith("->61")) {
            throw new AssertionError("single-request pool should still report a depot-to-depot route: " + output);
        }
    }

    private static void assertRootLpPricingSmokeThreeRequestControlledPool() throws Exception {
        String output = captureStdout(() -> RunRootLpPricingSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-route-requests",
                "3",
                "--max-three-request-routes",
                "600"
        }));
        String[] columns = output.strip().split("\\R")[1].split(",", -1);
        assertEquals("root-lp-pricing-smoke controlled three-request candidate count", "1121", columns[13]);
        if (Integer.parseInt(columns[14]) <= 0) {
            throw new AssertionError("controlled three-request pool should find negative candidates: " + output);
        }
        if (columns[16].split("->", -1).length < 8) {
            throw new AssertionError("controlled three-request best route should be able to use three requests: "
                    + output);
        }
    }

    private static void assertRootFiniteCgSmokeRestrictedPool() throws Exception {
        String output = captureStdout(() -> RunRootFiniteCgSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString()
        }));
        assertRootFiniteCgSmokeRow(output, "AA30", "RC", "rc-text", "30", "62", "15", "30", true);

        String singleRequestOutput = captureStdout(() -> RunRootFiniteCgSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-route-requests",
                "1"
        }));
        String[] single = assertRootFiniteCgSmokeRow(
                singleRequestOutput,
                "AA30",
                "RC",
                "rc-text",
                "30",
                "62",
                "15",
                "30",
                false);
        assertEquals("root-finite-cg single-request candidate count", "30", single[14]);
        assertEquals("root-finite-cg single-request iterations", "1", single[15]);
        assertEquals("root-finite-cg single-request added columns", "0", single[16]);

        String threeRequestOutput = captureStdout(() -> RunRootFiniteCgSmoke.main(new String[] {
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-route-requests",
                "3",
                "--max-three-request-routes",
                "600"
        }));
        String[] threeRequest = assertRootFiniteCgSmokeRow(
                threeRequestOutput,
                "AA30",
                "RC",
                "rc-text",
                "30",
                "62",
                "15",
                "30",
                true);
        assertEquals("root-finite-cg controlled three-request candidate count", "1121", threeRequest[14]);
        if (Integer.parseInt(threeRequest[16]) <= 0) {
            throw new AssertionError("controlled three-request finite CG should add columns: "
                    + threeRequestOutput);
        }
    }

    private static String[] assertRootFiniteCgSmokeRow(
            String output,
            String expectedInstance,
            String expectedGroup,
            String expectedFormat,
            String expectedRequests,
            String expectedVertices,
            String expectedCapacity,
            String expectedMaxVehicles,
            boolean requireAddedColumns) {
        String[] lines = output.strip().split("\\R");
        assertEquals("root-finite-cg-smoke header " + expectedInstance,
                RunRootFiniteCgSmoke.ResultRow.HEADER,
                lines[0]);
        String[] columns = lines[1].split(",", -1);
        assertEquals("root-finite-cg-smoke column count " + expectedInstance, 21, columns.length);
        assertEquals("root-finite-cg-smoke mode " + expectedInstance, "root-finite-cg-smoke", columns[0]);
        assertEquals("root-finite-cg-smoke instance " + expectedInstance, expectedInstance, columns[1]);
        assertEquals("root-finite-cg-smoke group " + expectedInstance, expectedGroup, columns[2]);
        assertEquals("root-finite-cg-smoke format " + expectedInstance, expectedFormat, columns[3]);
        assertEquals("root-finite-cg-smoke requests " + expectedInstance, expectedRequests, columns[4]);
        assertEquals("root-finite-cg-smoke vertices " + expectedInstance, expectedVertices, columns[5]);
        assertEquals("root-finite-cg-smoke capacity " + expectedInstance, expectedCapacity, columns[6]);
        assertEquals("root-finite-cg-smoke max vehicles " + expectedInstance, expectedMaxVehicles, columns[7]);
        int requests = Integer.parseInt(expectedRequests);
        int seedColumns = Integer.parseInt(columns[8]);
        int finalColumns = Integer.parseInt(columns[9]);
        int candidateRoutes = Integer.parseInt(columns[14]);
        int iterations = Integer.parseInt(columns[15]);
        int addedColumns = Integer.parseInt(columns[16]);
        int remainingNegative = Integer.parseInt(columns[17]);
        if (seedColumns < requests || finalColumns < seedColumns || candidateRoutes < requests) {
            throw new AssertionError("root-finite-cg-smoke column counts should be internally consistent: "
                    + output);
        }
        assertEquals("root-finite-cg-smoke LP status " + expectedInstance, "optimal", columns[10]);
        assertEquals("root-finite-cg-smoke status " + expectedInstance,
                "restricted_finite_cg_pool_no_negative",
                columns[11]);
        double objective = Double.parseDouble(columns[12]);
        double bestReducedCost = Double.parseDouble(columns[18]);
        if (!Double.isFinite(objective) || !Double.isFinite(bestReducedCost)) {
            throw new AssertionError("root-finite-cg-smoke objective and best RC must be finite: " + output);
        }
        if (!"true".equals(columns[13]) && !"false".equals(columns[13])) {
            throw new AssertionError("root-finite-cg-smoke positiveArtificial must be boolean: " + output);
        }
        if (iterations < 1 || remainingNegative != 0) {
            throw new AssertionError("root-finite-cg-smoke should terminate with no remaining pool negatives: "
                    + output);
        }
        if (requireAddedColumns && addedColumns < 1) {
            throw new AssertionError("root-finite-cg-smoke default pool should add at least one finite-pool column: "
                    + output);
        }
        if ("NA".equals(columns[19]) || Long.parseLong(columns[20]) < 0L) {
            throw new AssertionError("root-finite-cg-smoke should report route text and non-negative time: "
                    + output);
        }
        return columns;
    }

    private static void assertStableCsvHeader() throws Exception {
        String expected = "instance,mode,status,rootLb,integerUb,gap,nodes,columns,cuts,"
                + "pricingCalls,forwardLabels,backwardLabels,dominatedLabels,pricingTimeMs,totalTimeMs";
        String rootLpPricingSmokeHeader = "mode,instance,paperGroup,format,requests,vertices,capacity,maxVehicles,"
                + "seedColumns,lpSolveStatus,status,lpObjective,positiveArtificial,candidateRoutes,"
                + "negativeCandidates,bestCandidateRc,bestCandidateRoute,totalTimeMs";
        String rootFiniteCgSmokeHeader = "mode,instance,paperGroup,format,requests,vertices,capacity,maxVehicles,"
                + "seedColumns,finalColumns,lpSolveStatus,status,lpObjective,positiveArtificial,candidateRoutes,"
                + "iterations,addedColumns,remainingNegativeCandidates,bestCandidateRc,bestCandidateRoute,totalTimeMs";
        assertEquals("benchmark CSV header", expected, BenchmarkCsv.HEADER);
        assertEquals("root LP pricing smoke header", rootLpPricingSmokeHeader,
                RunRootLpPricingSmoke.ResultRow.HEADER);
        assertEquals("root finite CG smoke header", rootFiniteCgSmokeHeader,
                RunRootFiniteCgSmoke.ResultRow.HEADER);
        assertEquals("root CG trace header",
                "traceType,instance,mode,iteration,lpObjective,bestReducedCost,addedColumns,columnCount,cutsActive,"
                        + "forwardLabels,backwardLabels,dominatedLabels,pricingTimeMs,exactPricing,pricingStatus",
                TraceCsv.ROOT_CG_HEADER);
        assertEquals("BCP node trace header",
                "traceType,instance,nodeId,depth,constraints,activeCutCount,lowerBound,incumbentUpperBound,"
                        + "bestReducedCost,pricingCalls,pricedColumns,generatedColumns,"
                        + "forwardLabels,backwardLabels,dominatedLabels,pricingTimeMs,pruneReason,branchType",
                TraceCsv.BCP_NODE_HEADER);

        assertSchemaHeader("benchmark-results.csv", expected);
        assertSchemaHeader("root-cg-trace.csv", TraceCsv.ROOT_CG_HEADER);
        assertSchemaHeader("bcp-node-trace.csv", TraceCsv.BCP_NODE_HEADER);
        assertSchemaHeader("root-lp-pricing-smoke.csv", RunRootLpPricingSmoke.ResultRow.HEADER);
        assertSchemaHeader("root-finite-cg-smoke.csv", RunRootFiniteCgSmoke.ResultRow.HEADER);

        Path report = Path.of("docs", "reports", "reproduction-status.md");
        if (!Files.exists(report)) {
            report = Path.of("G:\\bid\\pdptw-bcp-java-gurobi\\docs\\reports\\reproduction-status.md");
        }
        if (!Files.exists(report)) {
            throw new AssertionError("missing public reproduction report: " + report);
        }
        String text = Files.readString(report);
        assertReportContains(text, "# PDPTW BCP Reproduction Status");
        assertReportContains(text, "Status date:");
        assertReportContains(text, "## Implemented");
        assertReportContains(text, "## Not Implemented");
        assertReportContains(text, "pricing-audit");
        assertReportContains(text, "root-cg");
        assertReportContains(text, "bcp");
        assertReportContains(text, "compare-pricing");
        assertReportContains(text, "benchmark");
        assertReportContains(text, "Tiny smoke/report only");
        assertReportContains(text, "not a paper-table reproduction");
        assertReportContains(text, "LL/RC");
        assertReportContains(text,
                "CLI --cuts sr|robust|robust,sr open only on tiny explicit-labeling non-route-universe pricing");
        assertReportContains(text, "benchmark-results.csv");
        assertReportContains(text, "root-cg-trace.csv");
        assertReportContains(text, "bcp-node-trace.csv");
    }

    private static void assertReportContains(String report, String expectedText) {
        if (!report.contains(expectedText)) {
            throw new AssertionError("public reproduction report must contain: " + expectedText);
        }
    }

    private static void assertPricingAuditAndComparePricingAgree() throws Exception {
        Instance tinyA = tinyA();
        double expected = RunPricingAudit.audit(tinyA, PricingMode.FORWARD).rootLb();

        List<BenchmarkCsv.Row> records = BenchmarkRunner.comparePricing(tinyA);
        assertEquals("compare-pricing row count", PricingMode.values().length, records.size());
        String csv = BenchmarkCsv.rowsToCsv(records);
        if (!csv.startsWith(BenchmarkCsv.HEADER + System.lineSeparator())) {
            throw new AssertionError("compare-pricing CSV must start with the stable header");
        }
        for (BenchmarkCsv.Row row : records) {
            assertClose("compare-pricing best RC " + row.mode(),
                    expected,
                    row.rootLb());
        }
    }

    private static void assertTinyCComparePricingFailsClearly() throws Exception {
        Path tinyC = CliSupport.referencePath("tiny-c-subset-row.json");
        try {
            BenchmarkRunner.main(new String[] {
                    "--compare-pricing",
                    "--instance",
                    tinyC.toString()
            });
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null
                    || !message.contains("not a pricing instance")
                    || !message.contains("missing array 'travelCost'")) {
                throw new AssertionError("Tiny-C compare-pricing should fail with a clear fixture message", expected);
            }
            return;
        }
        throw new AssertionError("Tiny-C compare-pricing should not run as a pricing fixture");
    }

    private static void assertTinyDBcpFailsClearly() throws Exception {
        Path tinyD = CliSupport.referencePath("tiny-d-branching.json");
        try {
            RunBcp.main(new String[] {
                    "--instance",
                    tinyD.toString(),
                    "--cuts",
                    "none",
                    "--branching",
                    "timo"
            });
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null
                    || !message.contains("not a pricing instance")
                    || !message.contains("missing number 'nRequests'")
                    || !message.contains("Tiny-D")) {
                throw new AssertionError("Tiny-D BCP should fail with a clear fixture message", expected);
            }
            return;
        }
        throw new AssertionError("Tiny-D BCP should not run as a pricing fixture");
    }

    private static void assertRootCgAndBcpRecords() throws Exception {
        Instance tinyA = tinyA();
        RootCgResult root = new RootColumnGenerationRunner(1000.0, TOLERANCE, 20)
                .run(tinyA, PricingMode.BIDIR_DYNAMIC);
        assertClose("root CG tiny-A bound", 8.0, root.finalObjectiveValue());
        if (!root.terminatedByExactNoNegative()) {
            throw new AssertionError("root CG must terminate by exact no-negative pricing");
        }
        BenchmarkCsv.Row rootSummary = RunRootCg.summaryRow(tinyA, PricingMode.BIDIR_DYNAMIC, root, 0);
        assertEquals("root CG cuts", 0, rootSummary.cuts());
        if (rootSummary.forwardLabels() <= 0
                || rootSummary.backwardLabels() <= 0
                || rootSummary.dominatedLabels() < 0
                || rootSummary.pricingTimeMs() < 0) {
            throw new AssertionError("root CG summary should expose non-NA pricing counters");
        }

        BcpRunner.RunResult bcpResult = new BcpRunner().runDetailed(tinyA, "none", "timo");
        BenchmarkCsv.Row bcp = bcpResult.row();
        assertEquals("BcpRunner instance", tinyA.name(), bcp.instance());
        assertEquals("BcpRunner mode", "bcp", bcp.mode());
        assertEquals("BcpRunner status", "optimal_tiny_branch_tree", bcp.status());
        assertClose("BcpRunner root LB", 8.0, bcp.rootLb());
        if (bcp.pricingCalls() <= 1) {
            throw new AssertionError("BcpRunner must expose real node pricing call count");
        }
        assertEquals("BcpRunner summary pricing calls",
                bcpResult.solverResult().totalPricingCalls(),
                bcp.pricingCalls());
        if (bcp.pricingTimeMs() < 0) {
            throw new AssertionError("BcpRunner must expose measured node pricing time");
        }
        if (bcp.pricingTimeMs() != bcpResult.solverResult().totalPricingTimeMs()) {
            throw new AssertionError("BcpRunner summary pricing time must match node records");
        }
        if (bcp.pricingTimeMs() > bcp.totalTimeMs()) {
            throw new AssertionError("BcpRunner pricing time must not exceed total runtime");
        }
        assertEquals("default BCP forward labels unknown", -1, bcp.forwardLabels());
        assertEquals("default BCP backward labels unknown", -1, bcp.backwardLabels());
        assertEquals("default BCP dominated labels unknown", -1, bcp.dominatedLabels());
        String[] bcpSummaryColumns = bcp.toCsv().split(",", -1);
        assertEquals("default BCP summary forward labels CSV unknown", "NA", bcpSummaryColumns[10]);
        assertEquals("default BCP summary backward labels CSV unknown", "NA", bcpSummaryColumns[11]);
        assertEquals("default BCP summary dominated labels CSV unknown", "NA", bcpSummaryColumns[12]);

        String rootTrace = TraceCsv.rootCg(tinyA.name(), root);
        if (!rootTrace.startsWith(TraceCsv.ROOT_CG_HEADER + System.lineSeparator())
                || !rootTrace.contains("root-cg," + tinyA.name())
                || rootTrace.contains(",NA,")) {
            throw new AssertionError("Root CG trace must expose iteration records");
        }
        String bcpTrace = TraceCsv.bcpNodes(tinyA.name(), bcpResult.solverResult());
        if (!bcpTrace.startsWith(TraceCsv.BCP_NODE_HEADER + System.lineSeparator())
                || !bcpTrace.contains("bcp-node," + tinyA.name())
                || !bcpTrace.contains("bcp-node," + tinyA.name() + ",root,0,,0,")) {
            throw new AssertionError("BCP trace must expose node records");
        }
        String[] rootNodeTraceColumns = bcpTrace.split(System.lineSeparator())[1].split(",", -1);
        assertEquals("BCP root node trace column count",
                TraceCsv.BCP_NODE_HEADER.split(",", -1).length,
                rootNodeTraceColumns.length);
        int tracePricingCalls = 0;
        int tracePricedColumns = 0;
        int traceGeneratedColumns = 0;
        String[] bcpTraceLines = bcpTrace.split(System.lineSeparator());
        for (int i = 1; i < bcpTraceLines.length; i++) {
            String[] columns = bcpTraceLines[i].split(",", -1);
            assertEquals("BCP node trace column count",
                    TraceCsv.BCP_NODE_HEADER.split(",", -1).length,
                    columns.length);
            tracePricingCalls += Integer.parseInt(columns[9]);
            tracePricedColumns += Integer.parseInt(columns[10]);
            traceGeneratedColumns += Integer.parseInt(columns[11]);
            assertEquals("default BCP node forward labels unknown", "NA", columns[12]);
            assertEquals("default BCP node backward labels unknown", "NA", columns[13]);
            assertEquals("default BCP node dominated labels unknown", "NA", columns[14]);
        }
        assertEquals("BCP node trace pricing calls",
                bcpResult.solverResult().totalPricingCalls(),
                tracePricingCalls);
        assertEquals("BCP node trace priced columns",
                bcpResult.solverResult().totalPricedColumns(),
                tracePricedColumns);
        assertEquals("BCP node trace generated columns",
                bcpResult.solverResult().totalGeneratedColumns(),
                traceGeneratedColumns);
        int rootNodePricedColumns = Integer.parseInt(rootNodeTraceColumns[10]);
        if (rootNodePricedColumns <= 0) {
            throw new AssertionError("BCP node trace must expose priced route evaluations");
        }
        assertEquals("default BCP node forward labels unknown", "NA", rootNodeTraceColumns[12]);
        assertEquals("default BCP node backward labels unknown", "NA", rootNodeTraceColumns[13]);
        assertEquals("default BCP node dominated labels unknown", "NA", rootNodeTraceColumns[14]);
        long rootNodePricingTimeMs = Long.parseLong(rootNodeTraceColumns[15]);
        if (rootNodePricingTimeMs < 0L) {
            throw new AssertionError("BCP node trace must expose non-negative pricing time");
        }
        if (!BcpRunner.acceptsTimoBranching("timo")) {
            throw new AssertionError("RunBcp must accept --branching timo");
        }
        if (!BcpRunner.acceptsTimoBranching("TIMO")) {
            throw new AssertionError("RunBcp must accept --branching TIMO");
        }
        if (!BcpRunner.acceptsTimoBranching(null) || !BcpRunner.acceptsTimoBranching(" ")) {
            throw new AssertionError("RunBcp must treat missing --branching as timo");
        }
        if (BcpRunner.acceptsTimoBranching("none")) {
            throw new AssertionError("RunBcp must not accept --branching none until it is implemented");
        }
        if (BcpRunner.acceptsTimoBranching("NONE") || BcpRunner.acceptsTimoBranching("arc")) {
            throw new AssertionError("RunBcp must reject unsupported branching modes");
        }
    }

    private static Instance tinyA() throws Exception {
        return CliSupport.readInstance(java.util.Map.of(), "tiny-a-wide.json");
    }

    private static void assertBcpRejectsUnsupportedCuts() throws Exception {
        boolean runnerRejected = false;
        try {
            new BcpRunner().run(tinyA(), "robust,sr", "timo");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null
                    || !message.contains("does not separate cuts from --cuts yet")
                    || !message.contains("programmatic BcpRunner APIs")) {
                throw new AssertionError("BcpRunner should reject unsupported active cuts clearly", expected);
            }
            runnerRejected = true;
        }
        if (!runnerRejected) {
            throw new AssertionError("BcpRunner must not silently count unsupported BCP cuts");
        }
        assertRunBcpSubsetRowRouteUniverseRejected(null);
        assertRunBcpSubsetRowRouteUniverseRejected("route-universe-exact");
        assertRunBcpSubsetRowRouteUniverseRejected("route_universe_exact");
        assertRunBcpRobustRouteUniverseRejected(null);
        assertRunBcpRobustRouteUniverseRejected("route-universe-exact");
        assertRunBcpRobustRouteUniverseRejected("route_universe_exact");
        assertRunBcpMixedRouteUniverseRejected(null);
        assertRunBcpMixedRouteUniverseRejected("route-universe-exact");
        assertRunBcpMixedRouteUniverseRejected("route_universe_exact");
    }

    private static void assertRunBcpCutsRejected(String cuts) throws Exception {
        try {
            RunBcp.main(new String[] {
                    "--pricing",
                    "bidir-dynamic",
                    "--cuts",
                    cuts,
                    "--branching",
                    "timo"
            });
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("does not separate mixed cuts from --cuts yet")) {
                throw new AssertionError("RunBcp should keep CLI cuts closed for --cuts " + cuts, expected);
            }
            return;
        }
        throw new AssertionError("RunBcp must reject --cuts " + cuts);
    }

    private static void assertRunBcpSubsetRowRouteUniverseRejected(String pricing) throws Exception {
        assertRunBcpRouteUniverseCutRejected("sr", pricing, "requires an explicit labeling --pricing mode");
    }

    private static void assertRunBcpRobustRouteUniverseRejected(String pricing) throws Exception {
        assertRunBcpRouteUniverseCutRejected("robust", pricing, "requires an explicit labeling --pricing mode");
    }

    private static void assertRunBcpMixedRouteUniverseRejected(String pricing) throws Exception {
        assertRunBcpRouteUniverseCutRejected("robust,sr", pricing, "requires an explicit labeling --pricing mode");
    }

    private static void assertRunBcpRouteUniverseCutRejected(
            String cuts,
            String pricing,
            String expectedMessage) throws Exception {
        String[] args;
        if (pricing == null) {
            args = new String[] {
                    "--cuts",
                    cuts,
                    "--branching",
                    "timo"
            };
        } else {
            args = new String[] {
                    "--pricing",
                    pricing,
                    "--cuts",
                    cuts,
                    "--branching",
                    "timo"
            };
        }
        try {
            RunBcp.main(args);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains(expectedMessage)) {
                throw new AssertionError("RunBcp should reject route-universe --cuts " + cuts + " clearly", expected);
            }
            return;
        }
        throw new AssertionError("RunBcp must reject route-universe --cuts " + cuts);
    }

    private static void assertRunBcpSubsetRowCliPath() throws Exception {
        String cliOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-sr-cli.json").toString(),
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "sr",
                "--branching",
                "timo",
                "--trace"
        }));
        assertBcpSubsetRowCliOutput("RunBcp --cuts sr explicit-labeling", cliOutput, "tiny-sr-cli");
    }

    private static void assertRunBcpRobustCliPath() throws Exception {
        String cliOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-robust-cli.json").toString(),
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "robust",
                "--branching",
                "timo",
                "--trace"
        }));
        assertBcpCutCliOutput("RunBcp --cuts robust explicit-labeling", cliOutput, "tiny-robust-cli");

        String defaultFixtureOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "robust",
                "--branching",
                "timo",
                "--trace"
        }));
        assertBcpCutCliOutput("RunBcp --cuts robust default tiny fixture",
                defaultFixtureOutput,
                "tiny-robust-cli");
    }

    private static void assertRunBcpMixedCutCliPath() throws Exception {
        String cliOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-robust-sr-cli.json").toString(),
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "robust,sr",
                "--branching",
                "timo",
                "--trace"
        }));
        assertBcpCutCliOutput("RunBcp --cuts robust,sr explicit-labeling",
                cliOutput,
                "tiny-robust-sr-cli",
                2);

        String reversedOrderOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-robust-sr-cli.json").toString(),
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "sr,robust",
                "--branching",
                "timo",
                "--trace"
        }));
        assertBcpCutCliOutput("RunBcp --cuts sr,robust explicit-labeling",
                reversedOrderOutput,
                "tiny-robust-sr-cli",
                2);
    }

    private static void assertTinyRobustCliRepairAudit() throws Exception {
        Instance instance = CliSupport.readInstance(
                Map.of("instance", CliSupport.referencePath("tiny-robust-cli.json").toString()),
                "tiny-robust-cli.json");
        double[] requestDuals = new double[] {0.0, 0.0, 1.0, 0.0};
        ReducedCostMatrices matrices = ReducedCostMatrices.fromDualSolution(
                instance,
                DualSolution.ofOneIndexed(requestDuals, 0.0));
        RobustCutRow robustRow = RobustCutCandidateGenerator.twoPathRequestSetRow(instance, List.of(1, 2, 3));
        List<RobustCut> cuts = List.of(robustRow.toPricingCut(-1.0));

        DtiPtiRepair.RepairResult forwardRepair = DtiPtiRepair.repairForwardDti(
                instance,
                DtiPtiRepair.forwardMatrixWithRobustCuts(matrices, cuts));
        DtiPtiRepair.RepairResult backwardRepair = DtiPtiRepair.repairBackwardPti(
                instance,
                DtiPtiRepair.backwardMatrixWithRobustCuts(matrices, cuts));

        if (!DtiPtiRepair.satisfiesForwardDti(instance, forwardRepair.matrix())) {
            throw new AssertionError("tiny robust CLI forward repair must certify DTI");
        }
        if (!DtiPtiRepair.satisfiesBackwardPti(instance, backwardRepair.matrix())) {
            throw new AssertionError("tiny robust CLI backward repair must certify PTI");
        }

        BruteForcePricingOracle.Result oracle = new BruteForcePricingOracle().solve(instance);
        int checked = 0;
        for (BruteForcePricingOracle.RouteEvaluation route : oracle.routes()) {
            List<Integer> vertexIds = route.vertexIds();
            double directRobust = DtiPtiRepair.robustDirectReducedCost(matrices, cuts, vertexIds);
            assertClose("tiny robust CLI forward repaired route " + vertexIds,
                    directRobust,
                    forwardRepair.arcReducedCostSum(vertexIds));
            assertClose("tiny robust CLI backward repaired route " + vertexIds,
                    directRobust,
                    backwardRepair.arcReducedCostSum(vertexIds));
            checked++;
        }
        if (checked == 0) {
            throw new AssertionError("tiny robust CLI repair audit must check at least one feasible route");
        }

        List<Integer> fullRoute = List.of(0, 1, 4, 2, 5, 7);
        List<Integer> forwardPrefix = List.of(0, 1, 4, 2);
        List<Integer> backwardSuffix = List.of(2, 5, 7);
        double directRobust = DtiPtiRepair.robustDirectReducedCost(matrices, cuts, fullRoute);
        assertClose("tiny robust CLI route coefficient", 1.0, robustRow.routeCoefficient(instance, fullRoute));

        double merged = DtiPtiRepair.robustMergedReducedCost(
                forwardRepair.arcReducedCostSum(forwardPrefix),
                backwardRepair.arcReducedCostSum(backwardSuffix),
                matrices,
                BitSetOps.add(0L, 2),
                0L,
                forwardRepair,
                backwardRepair);
        assertClose("tiny robust CLI merged RC", directRobust, merged);
        if (Math.abs(forwardRepair.arcReducedCostSum(forwardPrefix)
                + backwardRepair.arcReducedCostSum(backwardSuffix)
                - merged) < TOLERANCE) {
            throw new AssertionError("tiny robust CLI merge audit should require correction");
        }
    }

    private static void assertBcpSubsetRowCliOutput(String label, String cliOutput, String expectedInstance) {
        assertBcpCutCliOutput(label, cliOutput, expectedInstance, 1);
    }

    private static void assertBcpCutCliOutput(String label, String cliOutput, String expectedInstance) {
        assertBcpCutCliOutput(label, cliOutput, expectedInstance, 1);
    }

    private static void assertBcpCutCliOutput(
            String label,
            String cliOutput,
            String expectedInstance,
            int expectedCuts) {
        String[] cliLines = cliOutput.strip().split("\\R");
        if (cliLines.length < 4) {
            throw new AssertionError(label + " trace should include summary and at least one node row: " + cliOutput);
        }
        assertEquals(label + " summary header", BenchmarkCsv.HEADER, cliLines[0]);
        assertEquals(label + " trace header", TraceCsv.BCP_NODE_HEADER, cliLines[2]);

        String[] summary = cliLines[1].split(",", -1);
        assertEquals(label + " summary column count",
                BenchmarkCsv.HEADER.split(",", -1).length,
                summary.length);
        assertEquals(label + " instance", expectedInstance, summary[0]);
        assertEquals(label + " mode", "bcp", summary[1]);
        assertEquals(label + " status", "optimal_tiny_branch_tree", summary[2]);
        assertEquals(label + " cut count", String.valueOf(expectedCuts), summary[8]);
        assertRunBcpLabelCounter(label + " summary forward labels", summary[10], true);
        assertRunBcpLabelCounter(label + " summary backward labels", summary[11], true);
        if ("NA".equals(summary[12])) {
            throw new AssertionError(label + " summary dominated labels must not be NA: " + cliLines[1]);
        }
        Integer.parseInt(summary[12]);
        if (Integer.parseInt(summary[9]) <= 0) {
            throw new AssertionError(label + " summary pricingCalls must be positive: " + cliLines[1]);
        }

        StringBuilder trace = new StringBuilder(cliLines[2]);
        for (int index = 3; index < cliLines.length; index++) {
            trace.append(System.lineSeparator()).append(cliLines[index]);
        }
        String traceText = trace.toString();
        assertBcpTraceActiveCutCount(label + " trace", traceText, expectedCuts);
        assertPricedTraceRowsHaveLabelCounters(label + " trace", traceText);
    }

    private static void assertMainCliRoutesPublicCommands() throws Exception {
        String tinyAPath = CliSupport.referencePath("tiny-a-wide.json").toString();
        CapturedCommand help = captureCommand(() -> Main.run(new String[0]));
        assertEquals("Main help exit", 0, help.exitCode());
        if (!help.stdout().contains("Commands: pricing-audit, root-cg, bcp, compare-pricing, benchmark")) {
            throw new AssertionError("Main help should list public commands: " + help.stdout());
        }
        if (!help.stdout().contains("benchmark is a Tiny smoke/report runner only")) {
            throw new AssertionError("Main help should label benchmark as tiny smoke/report only: "
                    + help.stdout());
        }
        if (!help.stdout().contains("root-lp-pricing-smoke solves a restricted root LP and scans a finite candidate pool only")) {
            throw new AssertionError("Main help should describe the restricted LP/pricing smoke command: "
                    + help.stdout());
        }
        if (!help.stdout().contains("root-finite-cg-smoke repeats LP solves over that finite candidate pool only")) {
            throw new AssertionError("Main help should describe the restricted finite-CG smoke command: "
                    + help.stdout());
        }
        if (!help.stdout().contains("Default bcp uses route-universe exact pricing")) {
            throw new AssertionError("Main help should explain default BCP label-counter boundary: "
                    + help.stdout());
        }
        if (!help.stdout().contains("CLI --cuts sr|robust|robust,sr open only on tiny explicit-labeling non-route-universe pricing")) {
            throw new AssertionError("Main help should keep the guarded cut paths visible: " + help.stdout());
        }
        assertEquals("Main help stderr", "", help.stderr());

        CapturedCommand pricingAudit = captureCommand(() -> Main.run(new String[] {
                "pricing-audit"
        }));
        assertEquals("Main pricing-audit exit", 0, pricingAudit.exitCode());
        assertEquals("Main pricing-audit stderr", "", pricingAudit.stderr());
        String[] pricingAuditLines = pricingAudit.stdout().strip().split("\\R");
        assertEquals("Main pricing-audit summary header", BenchmarkCsv.HEADER, pricingAuditLines[0]);
        assertEquals("Main pricing-audit row count", 5, pricingAuditLines.length);
        assertEquals("Main pricing-audit first mode", "forward", pricingAuditLines[1].split(",", -1)[1]);
        assertEquals("Main pricing-audit last mode", "bidir-dynamic", pricingAuditLines[4].split(",", -1)[1]);
        CapturedCommand pricingAuditPositionals = captureCommand(() -> Main.run(new String[] {
                "pricing-audit",
                tinyAPath,
                "bidir-dynamic"
        }));
        assertEquals("Main pricing-audit positional exit", 0, pricingAuditPositionals.exitCode());
        assertEquals("Main pricing-audit positional stderr", "", pricingAuditPositionals.stderr());
        String[] pricingAuditPositionalLines = pricingAuditPositionals.stdout().strip().split("\\R");
        assertEquals("Main pricing-audit positional row count", 2, pricingAuditPositionalLines.length);
        assertEquals("Main pricing-audit positional mode",
                "bidir-dynamic",
                pricingAuditPositionalLines[1].split(",", -1)[1]);
        CapturedCommand pricingAuditNamedInstancePositionalMode = captureCommand(() -> Main.run(new String[] {
                "pricing-audit",
                "--instance",
                tinyAPath,
                "bidir-dynamic"
        }));
        assertEquals("Main pricing-audit named-instance positional-mode exit",
                0,
                pricingAuditNamedInstancePositionalMode.exitCode());
        assertEquals("Main pricing-audit named-instance positional-mode stderr",
                "",
                pricingAuditNamedInstancePositionalMode.stderr());

        CapturedCommand pricingAuditCuts = captureCommand(() -> Main.run(new String[] {
                "pricing-audit",
                "--cuts",
                "robust"
        }));
        assertEquals("Main pricing-audit cuts exit", 2, pricingAuditCuts.exitCode());
        assertEquals("Main pricing-audit cuts stdout", "", pricingAuditCuts.stdout());
        if (!pricingAuditCuts.stderr().contains("ERROR: ")
                || !pricingAuditCuts.stderr().contains("no-cut pricing audits only")
                || !pricingAuditCuts.stderr().contains("programmatic pricing tests")) {
            throw new AssertionError("Main pricing-audit cuts rejection should explain no-cut boundary: "
                    + pricingAuditCuts.stderr());
        }
        CapturedCommand pricingAuditTypo = captureCommand(() -> Main.run(new String[] {
                "pricing-audit",
                "--prcing",
                "bidir-dynamic"
        }));
        assertUnknownOption(
                "Main pricing-audit unknown option",
                pricingAuditTypo,
                "pricing-audit",
                "prcing",
                "pricing");
        CapturedCommand pricingAuditExtraArg = captureCommand(() -> Main.run(new String[] {
                "pricing-audit",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "bidir-dynamic",
                "extra"
        }));
        assertUnexpectedPositional(
                "Main pricing-audit extra positional",
                pricingAuditExtraArg,
                "pricing-audit",
                "extra",
                "[instance] [pricing-mode]");

        CapturedCommand rootCgTrace = captureCommand(() -> Main.run(new String[] {
                "root-cg",
                "--pricing",
                "bidir-dynamic",
                "--trace"
        }));
        assertEquals("Main root-cg exit", 0, rootCgTrace.exitCode());
        assertEquals("Main root-cg stderr", "", rootCgTrace.stderr());
        String[] rootCgLines = rootCgTrace.stdout().strip().split("\\R");
        assertEquals("Main root-cg summary header", BenchmarkCsv.HEADER, rootCgLines[0]);
        assertEquals("Main root-cg trace header", TraceCsv.ROOT_CG_HEADER, rootCgLines[2]);
        String[] rootCgSummary = rootCgLines[1].split(",", -1);
        assertEquals("Main root-cg mode", "root-cg-bidir-dynamic", rootCgSummary[1]);
        assertRunBcpLabelCounter("Main root-cg summary forward labels", rootCgSummary[10], true);
        assertRunBcpLabelCounter("Main root-cg summary backward labels", rootCgSummary[11], true);
        CapturedCommand rootCgPositionals = captureCommand(() -> Main.run(new String[] {
                "root-cg",
                tinyAPath,
                "bidir-dynamic"
        }));
        assertEquals("Main root-cg positional exit", 0, rootCgPositionals.exitCode());
        assertEquals("Main root-cg positional stderr", "", rootCgPositionals.stderr());
        String[] rootCgPositionalLines = rootCgPositionals.stdout().strip().split("\\R");
        assertEquals("Main root-cg positional mode",
                "root-cg-bidir-dynamic",
                rootCgPositionalLines[1].split(",", -1)[1]);
        CapturedCommand rootCgNamedInstancePositionalMode = captureCommand(() -> Main.run(new String[] {
                "root-cg",
                "--instance",
                tinyAPath,
                "bidir-dynamic"
        }));
        assertEquals("Main root-cg named-instance positional-mode exit",
                0,
                rootCgNamedInstancePositionalMode.exitCode());
        assertEquals("Main root-cg named-instance positional-mode stderr",
                "",
                rootCgNamedInstancePositionalMode.stderr());

        CapturedCommand rootCgCuts = captureCommand(() -> Main.run(new String[] {
                "root-cg",
                "--cuts",
                "robust,sr"
        }));
        assertEquals("Main root-cg cuts exit", 2, rootCgCuts.exitCode());
        assertEquals("Main root-cg cuts stdout", "", rootCgCuts.stdout());
        if (!rootCgCuts.stderr().contains("ERROR: ")
                || !rootCgCuts.stderr().contains("no-cut root CG only")
                || !rootCgCuts.stderr().contains("programmatic RootColumnGenerationRunner cutRows")) {
            throw new AssertionError("Main root-cg cuts rejection should explain no-cut boundary: "
                    + rootCgCuts.stderr());
        }
        CapturedCommand rootCgTypo = captureCommand(() -> Main.run(new String[] {
                "root-cg",
                "--trce"
        }));
        assertUnknownOption(
                "Main root-cg unknown option",
                rootCgTypo,
                "root-cg",
                "trce",
                "trace");
        CapturedCommand rootCgExtraArg = captureCommand(() -> Main.run(new String[] {
                "root-cg",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "bidir-dynamic",
                "extra"
        }));
        assertUnexpectedPositional(
                "Main root-cg extra positional",
                rootCgExtraArg,
                "root-cg",
                "extra",
                "[instance] [pricing-mode]");

        CapturedCommand rootLpPricingSmoke = captureCommand(() -> Main.run(new String[] {
                "root-lp-pricing-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString()
        }));
        assertEquals("Main root-lp-pricing-smoke exit", 0, rootLpPricingSmoke.exitCode());
        assertEquals("Main root-lp-pricing-smoke stderr", "", rootLpPricingSmoke.stderr());
        String[] rootLpLines = rootLpPricingSmoke.stdout().strip().split("\\R");
        assertEquals("Main root-lp-pricing-smoke header", RunRootLpPricingSmoke.ResultRow.HEADER, rootLpLines[0]);
        String[] rootLpSummary = rootLpLines[1].split(",", -1);
        assertEquals("Main root-lp-pricing-smoke mode", "root-lp-pricing-smoke", rootLpSummary[0]);
        assertEquals("Main root-lp-pricing-smoke instance", "AA30", rootLpSummary[1]);
        assertEquals("Main root-lp-pricing-smoke LP status", "optimal", rootLpSummary[9]);
        if (!rootLpSummary[10].startsWith("restricted_lp_")) {
            throw new AssertionError("Main root-lp-pricing-smoke should keep restricted status labels: "
                    + rootLpPricingSmoke.stdout());
        }

        CapturedCommand rootFiniteCgSmoke = captureCommand(() -> Main.run(new String[] {
                "root-finite-cg-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-route-requests",
                "1"
        }));
        assertEquals("Main root-finite-cg-smoke exit", 0, rootFiniteCgSmoke.exitCode());
        assertEquals("Main root-finite-cg-smoke stderr", "", rootFiniteCgSmoke.stderr());
        String[] rootFiniteCgLines = rootFiniteCgSmoke.stdout().strip().split("\\R");
        assertEquals("Main root-finite-cg-smoke header",
                RunRootFiniteCgSmoke.ResultRow.HEADER,
                rootFiniteCgLines[0]);
        String[] rootFiniteCgSummary = rootFiniteCgLines[1].split(",", -1);
        assertEquals("Main root-finite-cg-smoke mode", "root-finite-cg-smoke", rootFiniteCgSummary[0]);
        assertEquals("Main root-finite-cg-smoke instance", "AA30", rootFiniteCgSummary[1]);
        assertEquals("Main root-finite-cg-smoke LP status", "optimal", rootFiniteCgSummary[10]);
        assertEquals("Main root-finite-cg-smoke restricted status",
                "restricted_finite_cg_pool_no_negative",
                rootFiniteCgSummary[11]);

        CapturedCommand rootLpSmokeUnknownOption = captureCommand(() -> Main.run(new String[] {
                "root-lp-pricing-smoke",
                "--pricing",
                "bidir-dynamic"
        }));
        assertUnknownOption(
                "Main root-lp-pricing-smoke unknown option",
                rootLpSmokeUnknownOption,
                "root-lp-pricing-smoke",
                "pricing",
                "instance");
        CapturedCommand rootLpSmokeExtraArg = captureCommand(() -> Main.run(new String[] {
                "root-lp-pricing-smoke",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "extra"
        }));
        assertUnexpectedPositional(
                "Main root-lp-pricing-smoke extra positional",
                rootLpSmokeExtraArg,
                "root-lp-pricing-smoke",
                "extra",
                "[instance]");
        CapturedCommand rootLpSmokeBadPool = captureCommand(() -> Main.run(new String[] {
                "root-lp-pricing-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-route-requests",
                "4"
        }));
        assertCommandErrorContains(
                "Main root-lp-pricing-smoke bad pool size",
                rootLpSmokeBadPool,
                "max-route-requests must be 1, 2, or 3");
        CapturedCommand rootLpSmokeBadCandidateLimit = captureCommand(() -> Main.run(new String[] {
                "root-lp-pricing-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-three-request-routes",
                "0"
        }));
        assertCommandErrorContains(
                "Main root-lp-pricing-smoke bad candidate limit",
                rootLpSmokeBadCandidateLimit,
                "max-three-request-routes must be positive");

        CapturedCommand rootFiniteCgUnknownOption = captureCommand(() -> Main.run(new String[] {
                "root-finite-cg-smoke",
                "--pricing",
                "bidir-dynamic"
        }));
        assertUnknownOption(
                "Main root-finite-cg-smoke unknown option",
                rootFiniteCgUnknownOption,
                "root-finite-cg-smoke",
                "pricing",
                "instance");
        CapturedCommand rootFiniteCgBadIterations = captureCommand(() -> Main.run(new String[] {
                "root-finite-cg-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-iterations",
                "0"
        }));
        assertCommandErrorContains(
                "Main root-finite-cg-smoke bad iterations",
                rootFiniteCgBadIterations,
                "max-iterations must be positive");
        CapturedCommand rootFiniteCgBadCandidateLimit = captureCommand(() -> Main.run(new String[] {
                "root-finite-cg-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-three-request-routes",
                "0"
        }));
        assertCommandErrorContains(
                "Main root-finite-cg-smoke bad candidate limit",
                rootFiniteCgBadCandidateLimit,
                "max-three-request-routes must be positive");
        CapturedCommand rootFiniteCgBadBatch = captureCommand(() -> Main.run(new String[] {
                "root-finite-cg-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--max-columns-per-iteration",
                "0"
        }));
        assertCommandErrorContains(
                "Main root-finite-cg-smoke bad batch size",
                rootFiniteCgBadBatch,
                "max-columns-per-iteration must be positive");
        CapturedCommand rootFiniteCgBadTolerance = captureCommand(() -> Main.run(new String[] {
                "root-finite-cg-smoke",
                "--instance",
                Path.of("..", "PDPTW_instances", "RC", "AA30").toString(),
                "--tolerance",
                "NaN"
        }));
        assertCommandErrorContains(
                "Main root-finite-cg-smoke bad tolerance",
                rootFiniteCgBadTolerance,
                "tolerance must be finite and non-negative");

        CapturedCommand comparePricing = captureCommand(() -> Main.run(new String[] {
                "compare-pricing"
        }));
        assertEquals("Main compare-pricing exit", 0, comparePricing.exitCode());
        assertEquals("Main compare-pricing stderr", "", comparePricing.stderr());
        String[] compareLines = comparePricing.stdout().strip().split("\\R");
        assertEquals("Main compare-pricing summary header", BenchmarkCsv.HEADER, compareLines[0]);
        assertEquals("Main compare-pricing row count", 5, compareLines.length);
        String expectedBestReducedCost = compareLines[1].split(",", -1)[3];
        for (int row = 2; row < compareLines.length; row++) {
            assertEquals("Main compare-pricing best RC agreement row " + row,
                    expectedBestReducedCost,
                    compareLines[row].split(",", -1)[3]);
        }
        CapturedCommand comparePricingPositional = captureCommand(() -> Main.run(new String[] {
                "compare-pricing",
                tinyAPath
        }));
        assertEquals("Main compare-pricing positional exit", 0, comparePricingPositional.exitCode());
        assertEquals("Main compare-pricing positional stderr", "", comparePricingPositional.stderr());
        assertEquals("Main compare-pricing positional header",
                BenchmarkCsv.HEADER,
                comparePricingPositional.stdout().strip().split("\\R")[0]);
        CapturedCommand compareTinyC = captureCommand(() -> Main.run(new String[] {
                "compare-pricing",
                "--instance",
                CliSupport.referencePath("tiny-c-subset-row.json").toString()
        }));
        assertEquals("Main compare-pricing Tiny-C exit", 2, compareTinyC.exitCode());
        assertEquals("Main compare-pricing Tiny-C stdout", "", compareTinyC.stdout());
        if (!compareTinyC.stderr().contains("not a pricing instance")
                || !compareTinyC.stderr().contains("missing array 'travelCost'")
                || !compareTinyC.stderr().contains("metadata-only fixtures such as Tiny-C or Tiny-D")) {
            throw new AssertionError("Main compare-pricing Tiny-C rejection should explain fixture boundary: "
                    + compareTinyC.stderr());
        }
        CapturedCommand compareTypo = captureCommand(() -> Main.run(new String[] {
                "compare-pricing",
                "--prcing",
                "bidir-dynamic"
        }));
        assertUnknownOption(
                "Main compare-pricing unknown option",
                compareTypo,
                "compare-pricing",
                "prcing",
                "instance");
        CapturedCommand comparePricingOption = captureCommand(() -> Main.run(new String[] {
                "compare-pricing",
                "--pricing",
                "bidir-dynamic"
        }));
        assertUnknownOption(
                "Main compare-pricing rejects unused pricing option",
                comparePricingOption,
                "compare-pricing",
                "pricing",
                "instance");
        CapturedCommand compareBranchingOption = captureCommand(() -> Main.run(new String[] {
                "compare-pricing",
                "--branching",
                "none"
        }));
        assertUnknownOption(
                "Main compare-pricing rejects unused branching option",
                compareBranchingOption,
                "compare-pricing",
                "branching",
                "instance");
        CapturedCommand compareExtraArg = captureCommand(() -> Main.run(new String[] {
                "compare-pricing",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "extra"
        }));
        assertUnexpectedPositional(
                "Main compare-pricing extra positional",
                compareExtraArg,
                "compare-pricing",
                "extra",
                "[instance]");

        CapturedCommand defaultBcp = captureCommand(() -> Main.run(new String[] {
                "bcp"
        }));
        assertEquals("Main bcp exit", 0, defaultBcp.exitCode());
        assertEquals("Main bcp stderr", "", defaultBcp.stderr());
        String[] defaultLines = defaultBcp.stdout().strip().split("\\R");
        assertEquals("Main bcp summary header", BenchmarkCsv.HEADER, defaultLines[0]);
        String[] defaultSummary = defaultLines[1].split(",", -1);
        assertEquals("Main bcp mode", "bcp", defaultSummary[1]);
        assertEquals("Main bcp status", "optimal_tiny_branch_tree", defaultSummary[2]);
        assertEquals("Main bcp default forward labels unknown", "NA", defaultSummary[10]);
        assertEquals("Main bcp default backward labels unknown", "NA", defaultSummary[11]);
        CapturedCommand bcpPositional = captureCommand(() -> Main.run(new String[] {
                "bcp",
                tinyAPath
        }));
        assertEquals("Main bcp positional exit", 0, bcpPositional.exitCode());
        assertEquals("Main bcp positional stderr", "", bcpPositional.stderr());
        assertEquals("Main bcp positional header", BenchmarkCsv.HEADER, bcpPositional.stdout().strip().split("\\R")[0]);

        CapturedCommand dynamicTrace = captureCommand(() -> Main.run(new String[] {
                "bcp",
                "--pricing",
                "bidir-dynamic",
                "--trace"
        }));
        assertEquals("Main bcp dynamic exit", 0, dynamicTrace.exitCode());
        assertEquals("Main bcp dynamic stderr", "", dynamicTrace.stderr());
        String[] dynamicLines = dynamicTrace.stdout().strip().split("\\R");
        assertEquals("Main bcp dynamic summary header", BenchmarkCsv.HEADER, dynamicLines[0]);
        assertEquals("Main bcp dynamic trace header", TraceCsv.BCP_NODE_HEADER, dynamicLines[2]);
        String[] dynamicSummary = dynamicLines[1].split(",", -1);
        String[] dynamicTraceRow = dynamicLines[3].split(",", -1);
        assertRunBcpLabelCounter("Main bcp dynamic summary forward labels", dynamicSummary[10], true);
        assertRunBcpLabelCounter("Main bcp dynamic summary backward labels", dynamicSummary[11], true);
        assertRunBcpLabelCounter("Main bcp dynamic trace forward labels", dynamicTraceRow[12], true);
        assertRunBcpLabelCounter("Main bcp dynamic trace backward labels", dynamicTraceRow[13], true);

        String tinySrPath = CliSupport.referencePath("tiny-sr-cli.json").toString();
        CapturedCommand bcpSubsetRow = captureCommand(() -> Main.run(new String[] {
                "bcp",
                "--instance",
                tinySrPath,
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "sr",
                "--branching",
                "timo",
                "--trace"
        }));
        assertEquals("Main bcp sr exit", 0, bcpSubsetRow.exitCode());
        assertEquals("Main bcp sr stderr", "", bcpSubsetRow.stderr());
        assertBcpSubsetRowCliOutput("Main bcp sr", bcpSubsetRow.stdout(), "tiny-sr-cli");

        String tinyRobustPath = CliSupport.referencePath("tiny-robust-cli.json").toString();
        CapturedCommand bcpRobust = captureCommand(() -> Main.run(new String[] {
                "bcp",
                "--instance",
                tinyRobustPath,
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "robust",
                "--branching",
                "timo",
                "--trace"
        }));
        assertEquals("Main bcp robust exit", 0, bcpRobust.exitCode());
        assertEquals("Main bcp robust stderr", "", bcpRobust.stderr());
        assertBcpCutCliOutput("Main bcp robust", bcpRobust.stdout(), "tiny-robust-cli");

        String tinyRobustSrPath = CliSupport.referencePath("tiny-robust-sr-cli.json").toString();
        CapturedCommand bcpMixed = captureCommand(() -> Main.run(new String[] {
                "bcp",
                "--instance",
                tinyRobustSrPath,
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "robust,sr",
                "--branching",
                "timo",
                "--trace"
        }));
        assertEquals("Main bcp mixed exit", 0, bcpMixed.exitCode());
        assertEquals("Main bcp mixed stderr", "", bcpMixed.stderr());
        assertBcpCutCliOutput("Main bcp mixed", bcpMixed.stdout(), "tiny-robust-sr-cli", 2);

        CapturedCommand bcpCuts = captureCommand(() -> Main.run(new String[] {
                "bcp",
                "--pricing",
                "route-universe-exact",
                "--cuts",
                "robust,sr"
        }));
        assertEquals("Main bcp mixed route-universe exit", 2, bcpCuts.exitCode());
        assertEquals("Main bcp mixed route-universe stdout", "", bcpCuts.stdout());
        if (!bcpCuts.stderr().contains("ERROR: ")
                || !bcpCuts.stderr().contains("requires an explicit labeling --pricing mode")) {
            throw new AssertionError("Main bcp mixed route-universe rejection should explain explicit labeling boundary: "
                    + bcpCuts.stderr());
        }
        CapturedCommand bcpTypo = captureCommand(() -> Main.run(new String[] {
                "bcp",
                "--prcing",
                "bidir-dynamic"
        }));
        assertUnknownOption(
                "Main bcp unknown option",
                bcpTypo,
                "bcp",
                "prcing",
                "pricing");
        CapturedCommand bcpExtraArg = captureCommand(() -> Main.run(new String[] {
                "bcp",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "extra"
        }));
        assertUnexpectedPositional(
                "Main bcp extra positional",
                bcpExtraArg,
                "bcp",
                "extra",
                "[instance]");

        CapturedCommand compareCuts = captureCommand(() -> Main.run(new String[] {
                "compare-pricing",
                "--cuts",
                "robust"
        }));
        assertEquals("Main compare-pricing cuts exit", 2, compareCuts.exitCode());
        assertEquals("Main compare-pricing cuts stdout", "", compareCuts.stdout());
        if (!compareCuts.stderr().contains("ERROR: ")
                || !compareCuts.stderr().contains("no-cut pricing audits only")
                || !compareCuts.stderr().contains("programmatic pricing tests")) {
            throw new AssertionError("Main compare-pricing cuts rejection should explain no-cut boundary: "
                    + compareCuts.stderr());
        }

        CapturedCommand benchmark = captureCommand(() -> Main.run(new String[] {
                "benchmark"
        }));
        assertEquals("Main benchmark exit", 0, benchmark.exitCode());
        assertEquals("Main benchmark stderr", "", benchmark.stderr());
        String[] benchmarkLines = benchmark.stdout().strip().split("\\R");
        assertEquals("Main benchmark summary header", BenchmarkCsv.HEADER, benchmarkLines[0]);
        assertEquals("Main benchmark row count", 2, benchmarkLines.length);
        String[] benchmarkSummary = benchmarkLines[1].split(",", -1);
        assertEquals("Main benchmark mode", "bcp", benchmarkSummary[1]);
        assertEquals("Main benchmark status", "optimal_tiny_branch_tree", benchmarkSummary[2]);
        assertEquals("Main benchmark forward labels unknown", "NA", benchmarkSummary[10]);
        assertEquals("Main benchmark backward labels unknown", "NA", benchmarkSummary[11]);
        CapturedCommand benchmarkPositional = captureCommand(() -> Main.run(new String[] {
                "benchmark",
                tinyAPath
        }));
        assertEquals("Main benchmark positional exit", 0, benchmarkPositional.exitCode());
        assertEquals("Main benchmark positional stderr", "", benchmarkPositional.stderr());
        assertEquals("Main benchmark positional header",
                BenchmarkCsv.HEADER,
                benchmarkPositional.stdout().strip().split("\\R")[0]);

        CapturedCommand benchmarkPricing = captureCommand(() -> Main.run(new String[] {
                "benchmark",
                "--pricing",
                "bidir-dynamic"
        }));
        assertEquals("Main benchmark pricing exit", 0, benchmarkPricing.exitCode());
        assertEquals("Main benchmark pricing stderr", "", benchmarkPricing.stderr());
        String[] benchmarkPricingLines = benchmarkPricing.stdout().strip().split("\\R");
        assertEquals("Main benchmark pricing summary header", BenchmarkCsv.HEADER, benchmarkPricingLines[0]);
        String[] benchmarkPricingSummary = benchmarkPricingLines[1].split(",", -1);
        assertEquals("Main benchmark pricing mode", "bcp", benchmarkPricingSummary[1]);
        assertRunBcpLabelCounter("Main benchmark pricing summary forward labels", benchmarkPricingSummary[10], true);
        assertRunBcpLabelCounter("Main benchmark pricing summary backward labels", benchmarkPricingSummary[11], true);

        CapturedCommand benchmarkCuts = captureCommand(() -> Main.run(new String[] {
                "benchmark",
                "--cuts",
                "robust"
        }));
        assertEquals("Main benchmark cuts exit", 2, benchmarkCuts.exitCode());
        assertEquals("Main benchmark cuts stdout", "", benchmarkCuts.stdout());
        if (!benchmarkCuts.stderr().contains("ERROR: ")
                || !benchmarkCuts.stderr().contains("does not separate cuts from --cuts yet")) {
            throw new AssertionError("Main benchmark cuts rejection should explain closed cut separation: "
                    + benchmarkCuts.stderr());
        }

        CapturedCommand benchmarkBranching = captureCommand(() -> Main.run(new String[] {
                "benchmark",
                "--branching",
                "none"
        }));
        assertEquals("Main benchmark branching exit", 2, benchmarkBranching.exitCode());
        assertEquals("Main benchmark branching stdout", "", benchmarkBranching.stdout());
        if (!benchmarkBranching.stderr().contains("ERROR: ")
                || !benchmarkBranching.stderr().contains("Only --branching timo is supported")) {
            throw new AssertionError("Main benchmark branching rejection should explain timo-only branching: "
                    + benchmarkBranching.stderr());
        }
        CapturedCommand benchmarkTypo = captureCommand(() -> Main.run(new String[] {
                "benchmark",
                "--prcing",
                "bidir-dynamic"
        }));
        assertUnknownOption(
                "Main benchmark unknown option",
                benchmarkTypo,
                "benchmark",
                "prcing",
                "pricing");
        CapturedCommand benchmarkExtraArg = captureCommand(() -> Main.run(new String[] {
                "benchmark",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "extra"
        }));
        assertUnexpectedPositional(
                "Main benchmark extra positional",
                benchmarkExtraArg,
                "benchmark",
                "extra",
                "[instance]");

        CapturedCommand unknown = captureCommand(() -> Main.run(new String[] {
                "not-a-command"
        }));
        assertEquals("Main unknown command exit", 2, unknown.exitCode());
        assertEquals("Main unknown command stdout", "", unknown.stdout());
        if (!unknown.stderr().contains("ERROR: ")
                || !unknown.stderr().contains("Unknown command: not-a-command")) {
            throw new AssertionError("Main unknown command should explain the invalid command: "
                    + unknown.stderr());
        }
    }

    private static void assertBcpRunnerProgrammaticActiveCutsReachNodeTrace() throws Exception {
        Instance tinyA = tinyA();
        RobustCutRow row = RobustCutRow.ofArcCoefficients(
                "bcp_runner_active_cut_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                100.0,
                Map.of(new RobustCut.Arc(0, 1), 1.0));

        BcpRunner.RunResult result = new BcpRunner()
                .runDetailedWithActiveCutRows(tinyA, "timo", List.of(row));

        assertEquals("programmatic BCP active cut summary", 1, result.row().cuts());
        String trace = TraceCsv.bcpNodes(tinyA.name(), result.solverResult());
        if (!trace.contains("bcp-node," + tinyA.name() + ",root,0,,1,")) {
            throw new AssertionError("programmatic active cut row should reach BCP node trace");
        }
    }

    private static void assertBcpRunnerRejectsBranchRowsAsActiveCutRows() throws Exception {
        Instance tinyA = tinyA();
        try {
            new BcpRunner().runDetailedWithActiveCutRows(
                    tinyA,
                    "timo",
                    List.of(BranchMasterRow.of(VehicleCountConstraint.lessOrEqual(1))));
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("activeCutRows must not contain inherited branch rows")) {
                throw new AssertionError("BcpRunner activeCutRows should reject branch rows clearly", expected);
            }
            return;
        }
        throw new AssertionError("BcpRunner activeCutRows must reject inherited branch rows");
    }

    private static void assertBcpRunnerRouteUniverseRejectsBranchRowsAsActiveCutRows() throws Exception {
        Instance tinyA = tinyA();
        List<RouteColumn> routeUniverse = List.of(RouteColumn.fromRoute(
                "branch_row_rejection_route",
                Route.of(0, 1, 2, 3, 4, 5),
                tinyA));
        try {
            new BcpRunner().runDetailedWithActiveCutRowsAndRouteUniverse(
                    tinyA,
                    "timo",
                    routeUniverse,
                    List.of(BranchMasterRow.of(VehicleCountConstraint.lessOrEqual(1))));
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("activeCutRows must not contain inherited branch rows")) {
                throw new AssertionError(
                        "BcpRunner route-universe activeCutRows should reject branch rows clearly",
                        expected);
            }
            return;
        }
        throw new AssertionError("BcpRunner route-universe activeCutRows must reject inherited branch rows");
    }

    private static void assertBcpRunnerWithRootLabelingUsesProgrammaticSolver() throws Exception {
        Instance tinyA = tinyA();
        RouteColumn fullRoute = RouteColumn.fromRoute(
                "root_labeling_full_route",
                Route.of(0, 1, 2, 3, 4, 5),
                tinyA);
        CountingContextPricingSolver solver = new CountingContextPricingSolver(fullRoute);

        BcpRunner.RunResult result = BcpRunner.withRootLabeling(solver)
                .runDetailed(tinyA, "none", "timo");

        assertEquals("BcpRunner root-labeling status", "optimal_tiny_branch_tree", result.row().status());
        assertClose("BcpRunner root-labeling bound", 8.0, result.row().rootLb());
        if (solver.contextCalls() <= 0) {
            throw new AssertionError("BcpRunner.withRootLabeling must call the supplied context pricing solver");
        }
        assertEquals("BcpRunner root-labeling matrix calls", 0, solver.matrixCalls());
        if (result.solverResult().totalGeneratedColumns() <= 0
                || result.solverResult().totalPricedColumns() <= 0) {
            throw new AssertionError("BcpRunner.withRootLabeling should add and audit a priced route"
                    + " generated=" + result.solverResult().totalGeneratedColumns()
                    + " priced=" + result.solverResult().totalPricedColumns());
        }
        int expectedForwardLabels = solver.contextCalls() * 3;
        int expectedBackwardLabels = solver.contextCalls() * 4;
        int expectedDominatedLabels = solver.contextCalls() * 2;
        assertEquals("BcpRunner root-labeling summary forward labels",
                expectedForwardLabels, result.row().forwardLabels());
        assertEquals("BcpRunner root-labeling summary backward labels",
                expectedBackwardLabels, result.row().backwardLabels());
        assertEquals("BcpRunner root-labeling summary dominated labels",
                expectedDominatedLabels, result.row().dominatedLabels());
        assertEquals("BcpRunner root-labeling result forward labels",
                result.row().forwardLabels(), result.solverResult().totalForwardLabels());
        assertEquals("BcpRunner root-labeling result backward labels",
                result.row().backwardLabels(), result.solverResult().totalBackwardLabels());
        assertEquals("BcpRunner root-labeling result dominated labels",
                result.row().dominatedLabels(), result.solverResult().totalDominatedLabels());
        String trace = TraceCsv.bcpNodes(tinyA.name(), result.solverResult());
        String[] traceLines = trace.split(System.lineSeparator());
        int traceForwardLabels = 0;
        int traceBackwardLabels = 0;
        int traceDominatedLabels = 0;
        for (int i = 1; i < traceLines.length; i++) {
            String[] columns = traceLines[i].split(",", -1);
            assertEquals("root-labeling BCP node trace column count",
                    TraceCsv.BCP_NODE_HEADER.split(",", -1).length,
                    columns.length);
            traceForwardLabels += Integer.parseInt(columns[12]);
            traceBackwardLabels += Integer.parseInt(columns[13]);
            traceDominatedLabels += Integer.parseInt(columns[14]);
        }
        assertEquals("BcpRunner root-labeling trace forward labels",
                result.row().forwardLabels(), traceForwardLabels);
        assertEquals("BcpRunner root-labeling trace backward labels",
                result.row().backwardLabels(), traceBackwardLabels);
        assertEquals("BcpRunner root-labeling trace dominated labels",
                result.row().dominatedLabels(), traceDominatedLabels);
    }

    private static void assertBcpRunnerPricingOptionUsesLabeling() throws Exception {
        Instance tinyA = tinyA();
        if (!BcpRunner.usesRouteUniversePricing(null)
                || !BcpRunner.usesRouteUniversePricing("route-universe")
                || !BcpRunner.usesRouteUniversePricing(" route-universe-exact ")
                || !BcpRunner.usesRouteUniversePricing("route_universe_exact")) {
            throw new AssertionError("route-universe pricing aliases should keep the default backend");
        }
        BcpRunner.RunResult defaultResult = BcpRunner.fromPricingOption("route-universe")
                .runDetailed(tinyA, "none", "timo");
        assertEquals("route-universe BCP forward labels unknown", -1, defaultResult.row().forwardLabels());

        BcpRunner.RunResult labelingResult = BcpRunner.fromPricingOption("bidir-dynamic")
                .runDetailed(tinyA, "none", "timo");
        if (labelingResult.row().forwardLabels() <= 0 || labelingResult.row().backwardLabels() <= 0) {
            throw new AssertionError("--pricing bidir-dynamic should expose labeling counters in BCP summary");
        }
        assertEquals("pricing-option BCP forward labels",
                labelingResult.row().forwardLabels(), labelingResult.solverResult().totalForwardLabels());
        assertEquals("pricing-option BCP backward labels",
                labelingResult.row().backwardLabels(), labelingResult.solverResult().totalBackwardLabels());
        assertEquals("pricing-option BCP dominated labels",
                labelingResult.row().dominatedLabels(), labelingResult.solverResult().totalDominatedLabels());

        assertRunBcpPricingTraceCounters("forward", true, false);
        assertRunBcpPricingTraceCounters("backward", false, true);
        assertRunBcpPricingTraceCounters("bidir-static", true, true);
        assertRunBcpPricingTraceCounters(" bidir-dynamic ", true, true);

        try {
            BcpRunner.fromPricingOption("not-a-pricing-mode");
        } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null
                    || !expected.getMessage().contains("Unsupported BCP --pricing option")) {
                throw new AssertionError("invalid BCP pricing option should fail clearly", expected);
            }
            return;
        }
        throw new AssertionError("invalid BCP pricing option should be rejected");
    }

    private static void assertRunRootCgPricingTraceCounters() throws Exception {
        assertRunRootCgPricingTraceCounters("forward", true, false);
        assertRunRootCgPricingTraceCounters("backward", false, true);
        assertRunRootCgPricingTraceCounters("bidir-static", true, true);
        assertRunRootCgPricingTraceCounters("bidir-dynamic", true, true);
    }

    private static void assertRunRootCgPricingTraceCounters(
            String pricing,
            boolean expectForwardLabels,
            boolean expectBackwardLabels) throws Exception {
        String cliOutput = captureStdout(() -> RunRootCg.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "--pricing",
                pricing,
                "--trace"
        }));
        String[] cliLines = cliOutput.strip().split("\\R");
        assertEquals("RunRootCg pricing summary header " + pricing, BenchmarkCsv.HEADER, cliLines[0]);
        assertEquals("RunRootCg pricing trace header " + pricing, TraceCsv.ROOT_CG_HEADER, cliLines[2]);
        String[] summaryColumns = cliLines[1].split(",", -1);
        assertEquals("RunRootCg pricing summary column count " + pricing,
                BenchmarkCsv.HEADER.split(",", -1).length,
                summaryColumns.length);
        assertEquals("RunRootCg pricing mode " + pricing, "root-cg-" + pricing, summaryColumns[1]);
        assertEquals("RunRootCg pricing status " + pricing, "exact_no_negative", summaryColumns[2]);
        assertEquals("RunRootCg pricing cut count " + pricing, "0", summaryColumns[8]);
        int pricingCalls = Integer.parseInt(summaryColumns[9]);
        assertEquals("RunRootCg pricing trace row count " + pricing, pricingCalls, cliLines.length - 3);
        assertRunBcpLabelCounter(
                "RunRootCg --pricing " + pricing + " summary forward labels",
                summaryColumns[10],
                expectForwardLabels);
        assertRunBcpLabelCounter(
                "RunRootCg --pricing " + pricing + " summary backward labels",
                summaryColumns[11],
                expectBackwardLabels);
        if ("NA".equals(summaryColumns[12]) || Integer.parseInt(summaryColumns[12]) < 0) {
            throw new AssertionError("RunRootCg --pricing " + pricing
                    + " must expose dominated-label counters, got summary="
                    + cliLines[1]);
        }

        int traceForwardLabels = 0;
        int traceBackwardLabels = 0;
        int traceDominatedLabels = 0;
        int tracePricingTimeMs = 0;
        int traceColumns = TraceCsv.ROOT_CG_HEADER.split(",", -1).length;
        for (int line = 3; line < cliLines.length; line++) {
            String[] fields = cliLines[line].split(",", -1);
            assertEquals("RunRootCg trace column count " + pricing, traceColumns, fields.length);
            assertEquals("RunRootCg trace type " + pricing, "root-cg", fields[0]);
            assertEquals("RunRootCg trace mode " + pricing, pricing.toUpperCase().replace('-', '_'), fields[2]);
            assertEquals("RunRootCg trace cut count " + pricing, "0", fields[8]);
            assertRunBcpLabelCounter(
                    "RunRootCg --pricing " + pricing + " trace forward labels",
                    fields[9],
                    expectForwardLabels);
            assertRunBcpLabelCounter(
                    "RunRootCg --pricing " + pricing + " trace backward labels",
                    fields[10],
                    expectBackwardLabels);
            if ("NA".equals(fields[11]) || Integer.parseInt(fields[11]) < 0) {
                throw new AssertionError("RunRootCg --pricing " + pricing
                        + " trace must expose dominated-label counters: "
                        + cliLines[line]);
            }
            traceForwardLabels += Integer.parseInt(fields[9]);
            traceBackwardLabels += Integer.parseInt(fields[10]);
            traceDominatedLabels += Integer.parseInt(fields[11]);
            tracePricingTimeMs += Integer.parseInt(fields[12]);
        }
        assertEquals("RunRootCg " + pricing + " trace forward-label total",
                Integer.parseInt(summaryColumns[10]),
                traceForwardLabels);
        assertEquals("RunRootCg " + pricing + " trace backward-label total",
                Integer.parseInt(summaryColumns[11]),
                traceBackwardLabels);
        assertEquals("RunRootCg " + pricing + " trace dominated-label total",
                Integer.parseInt(summaryColumns[12]),
                traceDominatedLabels);
        assertEquals("RunRootCg " + pricing + " trace pricing-time total",
                Integer.parseInt(summaryColumns[13]),
                tracePricingTimeMs);
    }

    private static void assertRunBcpPricingTraceCounters(
            String pricing,
            boolean expectForwardLabels,
            boolean expectBackwardLabels) throws Exception {
        String cliOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "--cuts",
                "none",
                "--branching",
                "timo",
                "--pricing",
                pricing,
                "--trace"
        }));
        String[] cliLines = cliOutput.strip().split("\\R");
        assertEquals("RunBcp pricing summary header " + pricing, BenchmarkCsv.HEADER, cliLines[0]);
        assertEquals("RunBcp pricing trace header " + pricing, TraceCsv.BCP_NODE_HEADER, cliLines[2]);
        String[] summaryColumns = cliLines[1].split(",", -1);
        String[] traceColumns = cliLines[3].split(",", -1);
        assertRunBcpLabelCounter(
                "RunBcp --pricing " + pricing + " summary forward labels",
                summaryColumns[10],
                expectForwardLabels);
        assertRunBcpLabelCounter(
                "RunBcp --pricing " + pricing + " summary backward labels",
                summaryColumns[11],
                expectBackwardLabels);
        assertRunBcpLabelCounter(
                "RunBcp --pricing " + pricing + " trace forward labels",
                traceColumns[12],
                expectForwardLabels);
        assertRunBcpLabelCounter(
                "RunBcp --pricing " + pricing + " trace backward labels",
                traceColumns[13],
                expectBackwardLabels);
        if ("NA".equals(summaryColumns[12]) || "NA".equals(traceColumns[14])) {
            throw new AssertionError("RunBcp --pricing " + pricing
                    + " must expose dominated-label counters, got summary="
                    + cliLines[1] + " trace=" + cliLines[3]);
        }
    }

    private static void assertRunBcpLabelCounter(String label, String value, boolean expectPositive) {
        if ("NA".equals(value)) {
            throw new AssertionError(label + " must not be NA");
        }
        int parsed = Integer.parseInt(value);
        if (expectPositive) {
            if (parsed <= 0) {
                throw new AssertionError(label + " must be positive, got " + value);
            }
        } else if (parsed != 0) {
            throw new AssertionError(label + " must be zero, got " + value);
        }
    }

    private static void assertRunBcpDefaultTraceUsesRouteUniverseCounters() throws Exception {
        String cliOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "--cuts",
                "none",
                "--branching",
                "timo",
                "--trace"
        }));
        assertRunBcpRouteUniverseTraceUsesRouteUniverseCounters("RunBcp default", cliOutput);
    }

    private static void assertRunBcpExplicitRouteUniverseAliasTraceUsesRouteUniverseCounters() throws Exception {
        assertRunBcpExplicitRouteUniverseAliasTraceUsesRouteUniverseCounters("route-universe-exact");
        assertRunBcpExplicitRouteUniverseAliasTraceUsesRouteUniverseCounters("route_universe_exact");
    }

    private static void assertRunBcpExplicitRouteUniverseAliasTraceUsesRouteUniverseCounters(String pricing)
            throws Exception {
        String cliOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                CliSupport.referencePath("tiny-a-wide.json").toString(),
                "--cuts",
                "none",
                "--branching",
                "timo",
                "--pricing",
                pricing,
                "--trace"
        }));
        assertRunBcpRouteUniverseTraceUsesRouteUniverseCounters("RunBcp --pricing " + pricing, cliOutput);
    }

    private static void assertRunBcpBenchmarkTextUsesLabelingAndSubsetRows() throws Exception {
        Path subinstance = benchmarkSubinstance(
                Path.of("..", "PDPTW_instances", "RC", "AA30"),
                "aa30-benchmark-bcp-n6-");
        String cliOutput = captureStdout(() -> RunBcp.main(new String[] {
                "--instance",
                subinstance.toString(),
                "--pricing",
                "bidir-dynamic",
                "--cuts",
                "sr",
                "--branching",
                "timo",
                "--max-nodes",
                "20",
                "--max-cg-iterations",
                "100",
                "--max-set-branch-size",
                "3",
                "--max-route-requests",
                "3",
                "--max-three-request-routes",
                "600",
                "--trace"
        }));
        String[] lines = cliOutput.strip().split("\\R");
        assertEquals("benchmark BCP summary header", BenchmarkCsv.HEADER, lines[0]);
        assertEquals("benchmark BCP trace header", TraceCsv.BCP_NODE_HEADER, lines[2]);
        String[] summary = lines[1].split(",", -1);
        assertEquals("benchmark BCP mode", "bcp", summary[1]);
        assertEquals("benchmark BCP status", "optimal_benchmark_branch_tree", summary[2]);
        if ("NA".equals(summary[10]) || "NA".equals(summary[11]) || "NA".equals(summary[12])) {
            throw new AssertionError("benchmark BCP must expose labeling counters, not route-universe counters: "
                    + cliOutput);
        }
        if (Integer.parseInt(summary[9]) <= 0 || Integer.parseInt(summary[10]) <= 0
                || Integer.parseInt(summary[11]) <= 0) {
            throw new AssertionError("benchmark BCP should run exact labeling pricing: " + cliOutput);
        }
        String[] trace = lines[3].split(",", -1);
        assertEquals("benchmark BCP trace type", "bcp-node", trace[0]);
        assertEquals("benchmark BCP trace status counters forward", false, "NA".equals(trace[12]));
        assertEquals("benchmark BCP trace status counters backward", false, "NA".equals(trace[13]));
    }

    private static void assertRunBcpBenchmarkTextRejectsTinyOnlyBackends() throws Exception {
        Path rcSample = Path.of("..", "PDPTW_instances", "RC", "AA30");
        assertRunBcpBenchmarkRejected(
                new String[] {
                        "--instance",
                        rcSample.toString(),
                        "--branching",
                        "timo"
                },
                "route-universe exact pricing is tiny-only");
        assertRunBcpBenchmarkRejected(
                new String[] {
                        "--instance",
                        rcSample.toString(),
                        "--pricing",
                        "route-universe-exact",
                        "--branching",
                        "timo"
                },
                "route-universe exact pricing is tiny-only");
        assertRunBcpBenchmarkRejected(
                new String[] {
                        "--instance",
                        rcSample.toString(),
                        "--pricing",
                        "bidir-dynamic",
                        "--cuts",
                        "robust",
                        "--branching",
                        "timo"
                },
                "robust cut candidate generator is tiny-only");
        assertRunBcpBenchmarkRejected(
                new String[] {
                        "--instance",
                        rcSample.toString(),
                        "--pricing",
                        "bidir-dynamic",
                        "--cuts",
                        "robust,sr",
                        "--branching",
                        "timo"
                },
                "robust cut candidate generator is tiny-only");
    }

    private static Path benchmarkSubinstance(Path source, String prefix) throws Exception {
        Path output = Files.createTempFile(Path.of("logs"), prefix, ".txt");
        captureStdout(() -> RunBenchmarkSubinstance.main(new String[] {
                "--instance",
                source.toString(),
                "--requests",
                "6",
                "--output",
                output.toString()
        }));
        return output;
    }

    private static void assertRunBcpBenchmarkRejected(String[] args, String expectedMessage) throws Exception {
        try {
            RunBcp.main(args);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains(expectedMessage)) {
                throw new AssertionError("RunBcp benchmark rejection should mention: " + expectedMessage, expected);
            }
            return;
        }
        throw new AssertionError("RunBcp benchmark path should reject unsupported configuration");
    }

    private static void assertRunBcpRouteUniverseTraceUsesRouteUniverseCounters(String label, String cliOutput) {
        String[] cliLines = cliOutput.strip().split("\\R");
        if (cliLines.length < 4) {
            throw new AssertionError(label + " trace should include summary and at least one node row: " + cliOutput);
        }
        assertEquals(label + " summary header", BenchmarkCsv.HEADER, cliLines[0]);
        assertEquals(label + " trace header", TraceCsv.BCP_NODE_HEADER, cliLines[2]);

        String[] summary = cliLines[1].split(",", -1);
        assertEquals(label + " summary column count",
                BenchmarkCsv.HEADER.split(",", -1).length,
                summary.length);
        assertEquals(label + " mode", "bcp", summary[1]);
        assertEquals(label + " status", "optimal_tiny_branch_tree", summary[2]);
        assertEquals(label + " cut count", "0", summary[8]);
        assertEquals(label + " summary forward labels", "NA", summary[10]);
        assertEquals(label + " summary backward labels", "NA", summary[11]);
        assertEquals(label + " summary dominated labels", "NA", summary[12]);

        int expectedTraceColumns = TraceCsv.BCP_NODE_HEADER.split(",", -1).length;
        int tracePricingCalls = 0;
        int tracePricedColumns = 0;
        int traceGeneratedColumns = 0;
        int tracePricingTimeMs = 0;
        for (int line = 3; line < cliLines.length; line++) {
            String[] fields = cliLines[line].split(",", -1);
            assertEquals(label + " trace column count", expectedTraceColumns, fields.length);
            assertEquals(label + " trace type", "bcp-node", fields[0]);
            assertEquals(label + " trace instance", "tiny-a-wide", fields[1]);
            assertEquals(label + " active cuts", "0", fields[5]);
            assertEquals(label + " trace forward labels", "NA", fields[12]);
            assertEquals(label + " trace backward labels", "NA", fields[13]);
            assertEquals(label + " trace dominated labels", "NA", fields[14]);
            tracePricingCalls += Integer.parseInt(fields[9]);
            tracePricedColumns += Integer.parseInt(fields[10]);
            traceGeneratedColumns += Integer.parseInt(fields[11]);
            tracePricingTimeMs += Integer.parseInt(fields[15]);
        }
        assertEquals(label + " trace node count",
                Integer.parseInt(summary[6]),
                cliLines.length - 3);
        assertEquals(label + " pricing-call aggregate",
                Integer.parseInt(summary[9]),
                tracePricingCalls);
        assertEquals(label + " pricing-time aggregate",
                Integer.parseInt(summary[13]),
                tracePricingTimeMs);
        if (Integer.parseInt(summary[13]) > Integer.parseInt(summary[14])) {
            throw new AssertionError(label + " pricing time must not exceed total time: " + cliLines[1]);
        }
        if (tracePricingCalls <= 0 || tracePricedColumns <= 0 || traceGeneratedColumns <= 0) {
            throw new AssertionError(label + " route-universe trace should expose real pricing counters"
                    + " pricingCalls=" + tracePricingCalls
                    + " pricedColumns=" + tracePricedColumns
                    + " generatedColumns=" + traceGeneratedColumns);
        }
    }

    private static void assertBcpRunnerRootLabelingProcessesForcedBranchRows() throws Exception {
        Instance instance = forcedBranchingInstance();
        List<RouteColumn> routeUniverse = forcedBranchingColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);
        BcpRunner.RunResult result = BcpRunner.withRootLabeling(solver)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        assertEquals("forced root-labeling BCP status", "optimal_tiny_branch_tree", result.row().status());
        if (result.solverResult().createdNodes() <= 1 || result.solverResult().processedNodes() <= 1) {
            throw new AssertionError("forced root-labeling BCP must process child branch nodes"
                    + " created=" + result.solverResult().createdNodes()
                    + " processed=" + result.solverResult().processedNodes());
        }
        assertEquals("forced root-labeling BCP summary nodes",
                result.solverResult().processedNodes(), result.row().nodes());
        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("forced root-labeling branches on vehicle count",
                BranchConstraint.Type.VEHICLE_COUNT,
                root.branchType().orElseThrow(() -> new AssertionError("root should branch")));
        if (result.row().forwardLabels() <= 0 || result.row().backwardLabels() <= 0) {
            throw new AssertionError("forced root-labeling BCP must expose labeling counters");
        }
        if (solver.contextCalls() <= 1 || solver.matrixCalls() != 0) {
            throw new AssertionError("forced root-labeling BCP should price child nodes through PricingContext"
                    + " contextCalls=" + solver.contextCalls()
                    + " matrixCalls=" + solver.matrixCalls());
        }

        boolean sawVehicleLe = false;
        boolean sawVehicleGe = false;
        for (BranchAndPriceSolver.NodeRecord record : result.solverResult().nodeRecords()) {
            if (record.depth() == 0) {
                continue;
            }
            if (record.forwardLabels() <= 0 || record.backwardLabels() <= 0) {
                throw new AssertionError("child branch nodes should price through labeling backend"
                        + " node=" + record.nodeId()
                        + " forwardLabels=" + record.forwardLabels()
                        + " backwardLabels=" + record.backwardLabels());
            }
            sawVehicleLe |= record.constraints().contains("sum_lambda <= 1");
            sawVehicleGe |= record.constraints().contains("sum_lambda >= 2");
        }
        if (!sawVehicleLe || !sawVehicleGe) {
            throw new AssertionError("forced root-labeling BCP must process both vehicle-count children");
        }
    }

    private static void assertBcpRunnerRootLabelingProcessesSetOutflowDescendants() throws Exception {
        Instance instance = comboBranchPricingInstance();
        List<RouteColumn> routeUniverse = comboBranchPricingColumns(instance);
        RobustCutRow activeCut = comboBranchPricingCutRow();
        BcpRunner.RunResult routeUniverseBaseline = new BcpRunner(new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                8))
                .runDetailedWithActiveCutRowsAndRouteUniverse(
                        instance,
                        "timo",
                        routeUniverse,
                        List.of(activeCut));
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);
        BcpRunner.RunResult labeling = BcpRunner.withRootLabeling(solver, 1000.0, 8)
                .runDetailedWithActiveCutRowsAndRouteUniverse(
                        instance,
                        "timo",
                        routeUniverse,
                        List.of(activeCut));

        assertEquals("set-outflow labeling status", routeUniverseBaseline.row().status(), labeling.row().status());
        assertClose("set-outflow labeling root bound", routeUniverseBaseline.row().rootLb(), labeling.row().rootLb());
        assertClose("set-outflow labeling incumbent",
                routeUniverseBaseline.row().integerUb(), labeling.row().integerUb());
        if (labeling.solverResult().createdNodes() < 5 || labeling.solverResult().processedNodes() < 5) {
            throw new AssertionError("explicit-labeling BCP should process the set-outflow child tree"
                    + " created=" + labeling.solverResult().createdNodes()
                    + " processed=" + labeling.solverResult().processedNodes()
                    + " baselineCreated=" + routeUniverseBaseline.solverResult().createdNodes()
                    + " baselineProcessed=" + routeUniverseBaseline.solverResult().processedNodes());
        }
        if (solver.setOutflowContextCalls() <= 0 || solver.matrixCalls() != 0) {
            throw new AssertionError("set-outflow descendants must price through PricingContext"
                    + " setOutflowContextCalls=" + solver.setOutflowContextCalls()
                    + " matrixCalls=" + solver.matrixCalls());
        }

        BranchAndPriceSolver.NodeRecord vehicleGe = nodeWithConstraint(labeling.solverResult(), "sum_lambda >= 2");
        assertEquals("set-outflow child branch type",
                BranchConstraint.Type.SET_OUTFLOW,
                vehicleGe.branchType().orElseThrow(() -> new AssertionError("vehicle >= child should branch")));
        BranchAndPriceSolver.NodeRecord descendant =
                nodeWithConstraintPrefix(labeling.solverResult(), "sum_lambda >= 2", "x(delta+(");
        if (descendant.forwardLabels() <= 0 || descendant.backwardLabels() <= 0) {
            throw new AssertionError("set-outflow descendant must expose labeling counters"
                    + " node=" + descendant.nodeId()
                    + " forwardLabels=" + descendant.forwardLabels()
                    + " backwardLabels=" + descendant.backwardLabels());
        }
        if (!descendant.constraints().contains("sum_lambda >= 2")) {
            throw new AssertionError("set-outflow descendant must inherit vehicle-count branch row");
        }
        assertEquals("set-outflow summary forward labels",
                labeling.solverResult().totalForwardLabels(), labeling.row().forwardLabels());
        assertEquals("set-outflow summary backward labels",
                labeling.solverResult().totalBackwardLabels(), labeling.row().backwardLabels());
        assertEquals("set-outflow summary dominated labels",
                labeling.solverResult().totalDominatedLabels(), labeling.row().dominatedLabels());
        String trace = TraceCsv.bcpNodes(instance.name(), labeling.solverResult());
        if (!trace.contains("x(delta+(")) {
            throw new AssertionError("set-outflow explicit-labeling trace must expose branch rows");
        }
        assertPricedTraceRowsHaveLabelCounters("set-outflow explicit-labeling trace", trace);
        assertBcpTraceLabelCounterTotals("set-outflow explicit-labeling trace", trace, labeling.row());
    }

    private static void assertBcpRunnerRootLabelingCombinesActiveCutsAndBranchRows() throws Exception {
        Instance instance = forcedBranchingInstance();
        List<RouteColumn> routeUniverse = forcedBranchingColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);
        RobustCutRow activeCut = RobustCutRow.ofArcCoefficients(
                "forced_runner_active_cut_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                100.0,
                Map.of(new RobustCut.Arc(0, 1), 1.0));

        BcpRunner.RunResult result = BcpRunner.withRootLabeling(solver)
                .runDetailedWithActiveCutRowsAndRouteUniverse(
                        instance,
                        "timo",
                        routeUniverse,
                        List.of(activeCut));

        assertEquals("forced active-cut root-labeling BCP cuts", 1, result.row().cuts());
        if (result.solverResult().createdNodes() <= 1 || result.solverResult().processedNodes() <= 1) {
            throw new AssertionError("forced active-cut root-labeling BCP must process child branch nodes");
        }
        if (solver.robustContextCalls() <= 0) {
            throw new AssertionError("active robust cut rows must reach the labeling pricing context");
        }
        boolean sawChild = false;
        for (BranchAndPriceSolver.NodeRecord record : result.solverResult().nodeRecords()) {
            assertEquals("forced active-cut inherited count " + record.nodeId(), 1, record.activeCutCount());
            if (record.depth() > 0) {
                sawChild = true;
                if (record.forwardLabels() <= 0 || record.backwardLabels() <= 0) {
                    throw new AssertionError("active-cut child branch nodes should price through labeling backend"
                            + " node=" + record.nodeId());
                }
            }
        }
        if (!sawChild) {
            throw new AssertionError("forced active-cut root-labeling BCP must observe child nodes");
        }
    }

    private static void assertBcpRunnerRootLabelingUsesNonZeroRobustCutDual() throws Exception {
        Instance instance = robustCutPricingInstance();
        List<RouteColumn> routeUniverse = robustCutPricingColumns(instance);
        FiniteContextPricingSolver noCutSolver = new FiniteContextPricingSolver(routeUniverse);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.withRootLabeling(noCutSolver)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        BcpRunner.RunResult result = BcpRunner.withRootLabeling(solver)
                .runDetailedWithActiveCutRowsAndRouteUniverse(
                        instance,
                        "timo",
                        routeUniverse,
                        List.of(robustNodePricingCutRow()));

        if (noCutSolver.contextCalls() <= 0 || solver.contextCalls() <= 0) {
            throw new AssertionError("robust numerical audit should exercise both no-cut and active-cut pricing contexts");
        }
        assertEquals("no-cut matrix pricing calls", 0, noCutSolver.matrixCalls());
        assertEquals("active-cut matrix pricing calls", 0, solver.matrixCalls());
        assertEquals("no-cut robust context count", 0, noCutSolver.robustContextCalls());
        assertEquals("no-cut nonzero robust dual count", 0, noCutSolver.nonZeroRobustDualCalls());
        assertEquals("no-cut robust RC shift count", 0, noCutSolver.robustReducedCostShiftCalls());
        assertEquals("robust numerical BCP active cut count", 1, result.row().cuts());
        if (solver.nonZeroRobustDualCalls() <= 0) {
            throw new AssertionError("root-labeling BCP should observe a nonzero robust cut pricing dual");
        }
        if (solver.robustReducedCostShiftCalls() <= 0) {
            throw new AssertionError("root-labeling BCP should observe robust cut changing route direct RC");
        }
        if (solver.robustSeedPositiveShiftCalls() <= 0) {
            throw new AssertionError("robust cut pricing should increase RC for the positive-coefficient route");
        }
        if (solver.robustOnlyNegativeShiftCalls() <= 0) {
            throw new AssertionError("robust cut pricing should decrease RC for the negative-coefficient route");
        }
    }

    private static void assertBcpRunnerRootLabelingCombinesRobustAndSubsetRowCuts() throws Exception {
        Instance instance = forcedBranchingInstance();
        List<RouteColumn> routeUniverse = forcedBranchingColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);
        RobustCutRow robustRow = RobustCutRow.ofArcCoefficients(
                "forced_runner_mixed_robust_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                100.0,
                Map.of(new RobustCut.Arc(0, 1), 1.0));
        SubsetRowCutRow subsetRow = SubsetRowCutRow.ofL2Triple(
                "forced_runner_mixed_sr_probe",
                1,
                2,
                3);

        BcpRunner.RunResult result = BcpRunner.withRootLabeling(solver)
                .runDetailedWithActiveCutRowsAndRouteUniverse(
                        instance,
                        "timo",
                        routeUniverse,
                        List.of(robustRow, subsetRow));

        assertEquals("mixed active-cut BCP cuts", 2, result.row().cuts());
        if (solver.bothRobustAndSubsetRowContextCalls() <= 0) {
            throw new AssertionError("mixed active-cut BCP should price with robust and SR cuts together");
        }
        if (solver.nonZeroSubsetRowDualCalls() <= 0) {
            throw new AssertionError("mixed active-cut BCP should observe a nonzero SR pricing dual");
        }
        if (solver.subsetRowReducedCostShiftCalls() <= 0) {
            throw new AssertionError("mixed active-cut BCP should observe SR direct RC shifts");
        }
        for (BranchAndPriceSolver.NodeRecord record : result.solverResult().nodeRecords()) {
            assertEquals("mixed active-cut inherited count " + record.nodeId(), 2, record.activeCutCount());
        }
        assertBcpTraceActiveCutCount(
                "mixed active-cut BCP trace",
                TraceCsv.bcpNodes(instance.name(), result.solverResult()),
                2);
    }

    private static void assertBcpRunnerRootLabelingSeparatesSubsetRowsProgrammatically() throws Exception {
        Instance instance = subsetRowSeparationInstance();
        List<RouteColumn> routeUniverse = subsetRowSeparationColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.RunResult result = BcpRunner.withRootLabelingAndSubsetRowSeparation(solver, 1)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        assertEquals("programmatic SR-separation status", "optimal_tiny_branch_tree", result.row().status());
        assertEquals("programmatic SR-separation summary cuts", 1, result.row().cuts());
        if (solver.contextCalls() < 2) {
            throw new AssertionError("programmatic SR separation should price before and after adding the SR row");
        }
        if (solver.subsetRowContextCalls() <= 0) {
            throw new AssertionError("programmatic SR separation should pass separated SR rows into pricing");
        }
        if (solver.nonZeroSubsetRowDualCalls() <= 0) {
            throw new AssertionError("programmatic SR separation should expose nonzero SR duals");
        }
        if (solver.subsetRowReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic SR separation should change direct route reduced costs");
        }

        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic SR-separation root active cuts", 1, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertBcpTraceActiveCutCount("programmatic SR-separation trace", trace, 1);
        assertPricedTraceRowsHaveLabelCounters("programmatic SR-separation trace", trace);

        try {
            BcpRunner.withRootLabelingAndSubsetRowSeparation(new FiniteContextPricingSolver(routeUniverse), 1)
                    .runDetailedWithRouteUniverse(instance, "sr", "timo", routeUniverse);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("does not separate cuts from --cuts yet")) {
                throw new AssertionError("programmatic SR factory must not open BCP CLI cuts", expected);
            }
            return;
        }
        throw new AssertionError("programmatic SR factory must still reject --cuts sr");
    }

    private static void assertBcpRunnerSubsetRowSeparationCombinesActiveRobustCut() throws Exception {
        Instance instance = subsetRowSeparationInstance();
        List<RouteColumn> routeUniverse = subsetRowSeparationColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.RunResult result = BcpRunner.withRootLabelingAndSubsetRowSeparation(solver, 1)
                .runDetailedWithActiveCutRowsAndRouteUniverse(
                        instance,
                        "timo",
                        routeUniverse,
                        List.of(subsetRowSeparationRobustCutRow()));

        if (result.solverResult().processedNodes() <= 0) {
            throw new AssertionError("programmatic mixed path should process at least the root node");
        }
        assertEquals("programmatic mixed robust+automatic-SR summary cuts", 2, result.row().cuts());
        if (solver.bothRobustAndSubsetRowContextCalls() <= 0) {
            throw new AssertionError("programmatic mixed path should price with robust and automatic SR rows together");
        }
        if (solver.nonZeroRobustDualCalls() <= 0 || solver.nonZeroSubsetRowDualCalls() <= 0) {
            throw new AssertionError("programmatic mixed path should expose nonzero robust and SR duals");
        }
        if (solver.robustReducedCostShiftCalls() <= 0 || solver.subsetRowReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic mixed path should audit robust and SR direct-RC shifts");
        }

        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic mixed robust+automatic-SR active cuts", 2, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertBcpTraceActiveCutCount("programmatic mixed robust+automatic-SR trace", trace, 2);
        assertPricedTraceRowsHaveLabelCounters("programmatic mixed robust+automatic-SR trace", trace);
    }

    private static void assertBcpRunnerSubsetRowSeparationSkipsSemanticDuplicateActiveRow() throws Exception {
        Instance instance = subsetRowSeparationInstance();
        List<RouteColumn> routeUniverse = subsetRowSeparationColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);
        SubsetRowCutRow active = SubsetRowCutRow.ofL2Triple(
                "custom_runner_initial_sr",
                1,
                2,
                3);

        BcpRunner.RunResult result = BcpRunner.withRootLabelingAndSubsetRowSeparation(solver, 1)
                .runDetailedWithActiveCutRowsAndRouteUniverse(
                        instance,
                        "timo",
                        routeUniverse,
                        List.of(active));

        if (result.solverResult().processedNodes() <= 0) {
            throw new AssertionError("programmatic SR duplicate guard should process at least the root node");
        }
        assertEquals("programmatic SR duplicate guard status", "optimal_tiny_branch_tree", result.row().status());
        assertEquals("programmatic SR duplicate guard summary cuts", 1, result.row().cuts());
        if (solver.subsetRowContextCalls() <= 0) {
            throw new AssertionError("programmatic SR duplicate guard should price with the initial SR row");
        }
        if (solver.nonZeroSubsetRowDualCalls() <= 0) {
            throw new AssertionError("programmatic SR duplicate guard should expose nonzero SR duals");
        }
        if (solver.subsetRowReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic SR duplicate guard should preserve SR direct-RC pricing shifts");
        }

        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic SR duplicate guard root active cuts", 1, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertBcpTraceActiveCutCount("programmatic SR duplicate guard trace", trace, 1);
        assertPricedTraceRowsHaveLabelCounters("programmatic SR duplicate guard trace", trace);
    }

    private static void assertBcpRunnerGlobalSubsetRowsPropagateThroughQueuedNodes() throws Exception {
        Instance instance = queuedChildSrSeparationInstance();
        List<RouteColumn> routeUniverse = queuedChildSrSeparationColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.RunResult result = BcpRunner.withRootLabelingAndGlobalSubsetRowSeparation(solver, 10)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        assertEquals("programmatic global SR status", "optimal_tiny_branch_tree", result.row().status());
        if (result.solverResult().processedNodes() < 3 || result.solverResult().createdNodes() < 3) {
            throw new AssertionError("programmatic global SR should process queued child nodes"
                    + " processed=" + result.solverResult().processedNodes()
                    + " created=" + result.solverResult().createdNodes());
        }
        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic global SR root active cuts", 0, root.activeCutCount());

        BranchAndPriceSolver.NodeRecord geChild = nodeWithConstraint(result.solverResult(), "sum_lambda >= 3");
        if (geChild.activeCutCount() <= 0) {
            throw new AssertionError("programmatic global SR child should separate and publish an SR row");
        }
        BranchAndPriceSolver.NodeRecord geGrandchild =
                nodeWithConstraintPrefix(result.solverResult(), "sum_lambda >= 3", "x(delta+(");
        if (geGrandchild.activeCutCount() <= 0) {
            throw new AssertionError("programmatic global SR descendant should inherit the published SR row");
        }
        int maxActiveCuts = 0;
        for (BranchAndPriceSolver.NodeRecord record : result.solverResult().nodeRecords()) {
            maxActiveCuts = Math.max(maxActiveCuts, record.activeCutCount());
        }
        assertEquals("programmatic global SR summary cuts", maxActiveCuts, result.row().cuts());
        if (solver.subsetRowContextCalls() <= 0) {
            throw new AssertionError("programmatic global SR should price with SR context");
        }
        if (solver.nonZeroSubsetRowDualCalls() <= 0) {
            throw new AssertionError("programmatic global SR should expose nonzero SR duals");
        }
        if (solver.subsetRowReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic global SR should preserve SR direct-RC pricing shifts");
        }

        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertPricedTraceRowsHaveLabelCounters("programmatic global SR trace", trace);
        assertTraceActiveCutCount(
                "programmatic global SR child trace",
                traceRowWithConstraint(trace, "sum_lambda >= 3"),
                geChild.activeCutCount());
        assertTraceActiveCutCount(
                "programmatic global SR descendant trace",
                traceRowWithConstraintPrefix(trace, "sum_lambda >= 3", "x(delta+("),
                geGrandchild.activeCutCount());
    }

    private static void assertBcpRunnerRootLabelingSeparatesRobustTwoPathRowsProgrammatically() throws Exception {
        Instance instance = robustTwoPathSeparationInstance();
        List<RouteColumn> routeUniverse = robustTwoPathPairColumns(instance);
        FiniteContextPricingSolver noCutSolver = new FiniteContextPricingSolver(routeUniverse);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.RunResult noCut = BcpRunner.withRootLabeling(noCutSolver, 1000.0, 1)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);
        assertEquals("programmatic robust two-path no-cut summary cuts", 0, noCut.row().cuts());
        assertEquals("programmatic robust two-path no-cut root active cuts",
                0,
                noCut.solverResult().nodeRecords().get(0).activeCutCount());
        assertEquals("programmatic robust two-path no-cut robust context", 0, noCutSolver.robustContextCalls());

        BcpRunner.RunResult result = BcpRunner.withRootLabelingAndRobustTwoPathSeparation(solver, 1)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        assertEquals("programmatic robust two-path status", "optimal_tiny_branch_tree", result.row().status());
        assertEquals("programmatic robust two-path summary cuts", 1, result.row().cuts());
        if (solver.robustContextCalls() <= 0) {
            throw new AssertionError("programmatic robust two-path separation should pass robust rows into pricing");
        }
        if (solver.nonZeroRobustDualCalls() <= 0) {
            throw new AssertionError("programmatic robust two-path separation should expose nonzero robust duals");
        }
        if (solver.robustReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic robust two-path separation should change direct route RCs");
        }

        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic robust two-path root active cuts", 1, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertBcpTraceActiveCutCount("programmatic robust two-path trace", trace, 1);
        assertPricedTraceRowsHaveLabelCounters("programmatic robust two-path trace", trace);

        try {
            BcpRunner.withRootLabelingAndRobustTwoPathSeparation(new FiniteContextPricingSolver(routeUniverse), 1)
                    .runDetailedWithRouteUniverse(instance, "robust", "timo", routeUniverse);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("does not separate cuts from --cuts yet")) {
                throw new AssertionError("programmatic robust factory must not open BCP CLI cuts", expected);
            }
            return;
        }
        throw new AssertionError("programmatic robust factory must still reject --cuts robust");
    }

    private static void assertBcpRunnerRootLabelingCombinesGeneratedRobustAndSubsetRows() throws Exception {
        Instance instance = robustTwoPathSubsetRowSeparationInstance();
        List<RouteColumn> routeUniverse = robustTwoPathSubsetRowColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.RunResult result = BcpRunner
                .withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(solver, 1)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        assertEquals("programmatic generated robust+SR status", "optimal_tiny_branch_tree", result.row().status());
        assertEquals("programmatic generated robust+SR summary cuts", 2, result.row().cuts());
        if (solver.bothRobustAndSubsetRowContextCalls() <= 0) {
            throw new AssertionError("programmatic generated robust+SR path should price with both cut types");
        }
        if (solver.nonZeroRobustDualCalls() <= 0 || solver.nonZeroSubsetRowDualCalls() <= 0) {
            throw new AssertionError("programmatic generated robust+SR path should expose nonzero robust and SR duals");
        }
        if (solver.robustReducedCostShiftCalls() <= 0 || solver.subsetRowReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic generated robust+SR path should audit both direct-RC shifts");
        }

        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic generated robust+SR root active cuts", 2, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertBcpTraceActiveCutCount("programmatic generated robust+SR trace", trace, 2);
        assertPricedTraceRowsHaveLabelCounters("programmatic generated robust+SR trace", trace);

        try {
            BcpRunner.withRootLabelingAndRobustTwoPathAndSubsetRowSeparation(
                            new FiniteContextPricingSolver(routeUniverse),
                            1)
                    .runDetailedWithRouteUniverse(instance, "robust,sr", "timo", routeUniverse);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("does not separate cuts from --cuts yet")) {
                throw new AssertionError("programmatic generated robust+SR factory must not open CLI cuts", expected);
            }
            return;
        }
        throw new AssertionError("programmatic generated robust+SR factory must still reject CLI cuts");
    }

    private static void assertBcpRunnerRootLabelingSeparatesRobustRoundedCapacityRowsProgrammatically()
            throws Exception {
        Instance instance = robustTwoPathSeparationInstance();
        List<RouteColumn> routeUniverse = robustRoundedCapacityPairColumns(instance);
        FiniteContextPricingSolver noCutSolver = new FiniteContextPricingSolver(routeUniverse);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.RunResult noCut = BcpRunner.withRootLabeling(noCutSolver, 1000.0, 1)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);
        assertEquals("programmatic robust RCC no-cut summary cuts", 0, noCut.row().cuts());
        assertEquals("programmatic robust RCC no-cut root active cuts",
                0,
                noCut.solverResult().nodeRecords().get(0).activeCutCount());
        assertEquals("programmatic robust RCC no-cut robust context", 0, noCutSolver.robustContextCalls());

        BcpRunner.RunResult result = BcpRunner.withRootLabelingAndRobustRoundedCapacitySeparation(solver, 1)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        assertEquals("programmatic robust RCC status", "optimal_tiny_branch_tree", result.row().status());
        assertEquals("programmatic robust RCC summary cuts", 1, result.row().cuts());
        if (solver.robustContextCalls() <= 0) {
            throw new AssertionError("programmatic robust RCC separation should pass robust rows into pricing");
        }
        if (solver.nonZeroRobustDualCalls() <= 0) {
            throw new AssertionError("programmatic robust RCC separation should expose nonzero robust duals");
        }
        if (solver.robustReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic robust RCC separation should change direct route RCs");
        }

        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic robust RCC root active cuts", 1, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertBcpTraceActiveCutCount("programmatic robust RCC trace", trace, 1);
        assertPricedTraceRowsHaveLabelCounters("programmatic robust RCC trace", trace);

        try {
            BcpRunner.withRootLabelingAndRobustRoundedCapacitySeparation(
                            new FiniteContextPricingSolver(routeUniverse),
                            1)
                    .runDetailedWithRouteUniverse(instance, "robust", "timo", routeUniverse);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("does not separate cuts from --cuts yet")) {
                throw new AssertionError("programmatic robust RCC factory must not open BCP CLI cuts", expected);
            }
            return;
        }
        throw new AssertionError("programmatic robust RCC factory must still reject --cuts robust");
    }

    private static void assertBcpRunnerRootLabelingCombinesGeneratedRoundedCapacityAndSubsetRows()
            throws Exception {
        Instance instance = robustRoundedCapacitySubsetRowSeparationInstance();
        List<RouteColumn> routeUniverse = robustRoundedCapacitySubsetRowColumns(instance);
        FiniteContextPricingSolver solver = new FiniteContextPricingSolver(routeUniverse);

        BcpRunner.RunResult result = BcpRunner
                .withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(solver, 1)
                .runDetailedWithRouteUniverse(
                        instance,
                        "none",
                        "timo",
                        routeUniverse);

        assertEquals("programmatic generated RCC+SR status", "optimal_tiny_branch_tree", result.row().status());
        assertEquals("programmatic generated RCC+SR summary cuts", 2, result.row().cuts());
        if (solver.bothRobustAndSubsetRowContextCalls() <= 0) {
            throw new AssertionError("programmatic generated RCC+SR path should price with both cut types");
        }
        if (solver.nonZeroRobustDualCalls() <= 0 || solver.nonZeroSubsetRowDualCalls() <= 0) {
            throw new AssertionError("programmatic generated RCC+SR path should expose nonzero robust and SR duals");
        }
        if (solver.robustReducedCostShiftCalls() <= 0 || solver.subsetRowReducedCostShiftCalls() <= 0) {
            throw new AssertionError("programmatic generated RCC+SR path should audit both direct-RC shifts");
        }

        BranchAndPriceSolver.NodeRecord root = result.solverResult().nodeRecords().get(0);
        assertEquals("programmatic generated RCC+SR root active cuts", 2, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result.solverResult());
        assertBcpTraceActiveCutCount("programmatic generated RCC+SR trace", trace, 2);
        assertPricedTraceRowsHaveLabelCounters("programmatic generated RCC+SR trace", trace);

        try {
            BcpRunner.withRootLabelingAndRobustRoundedCapacityAndSubsetRowSeparation(
                            new FiniteContextPricingSolver(routeUniverse),
                            1)
                    .runDetailedWithRouteUniverse(instance, "robust,sr", "timo", routeUniverse);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("does not separate cuts from --cuts yet")) {
                throw new AssertionError("programmatic generated RCC+SR factory must not open CLI cuts", expected);
            }
            return;
        }
        throw new AssertionError("programmatic generated RCC+SR factory must still reject CLI cuts");
    }

    private static void assertRunRootCgRejectsUnsupportedCuts() throws Exception {
        assertRunRootCgCutsRejected("robust");
        assertRunRootCgCutsRejected("sr");
        assertRunRootCgCutsRejected("robust,sr");
    }

    private static void assertRunRootCgCutsRejected(String cuts) throws Exception {
        try {
            RunRootCg.main(new String[] {
                    "--cuts",
                    cuts
            });
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null
                    || !message.contains("no-cut root CG only")
                    || !message.contains("programmatic RootColumnGenerationRunner cutRows")) {
                throw new AssertionError("RunRootCg should reject unsupported CLI cuts clearly: " + cuts, expected);
            }
            return;
        }
        throw new AssertionError("RunRootCg must not silently ignore unsupported CLI cuts: " + cuts);
    }

    private static void assertPricingAuditRejectsUnsupportedCuts() throws Exception {
        assertPricingAuditCutsRejected("robust");
        assertPricingAuditCutsRejected("sr");
        assertPricingAuditCutsRejected("robust,sr");
    }

    private static void assertPricingAuditCutsRejected(String cuts) throws Exception {
        try {
            RunPricingAudit.run(new String[] {
                    "--cuts",
                    cuts
            });
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null
                    || !message.contains("no-cut pricing audits only")
                    || !message.contains("programmatic pricing tests")) {
                throw new AssertionError("RunPricingAudit should reject unsupported CLI cuts clearly: " + cuts,
                        expected);
            }
            return;
        }
        throw new AssertionError("RunPricingAudit must not silently ignore unsupported CLI cuts: " + cuts);
    }

    private static void assertComparePricingRejectsUnsupportedCuts() throws Exception {
        assertComparePricingCutsRejected("robust");
        assertComparePricingCutsRejected("sr");
        assertComparePricingCutsRejected("robust,sr");
    }

    private static void assertComparePricingCutsRejected(String cuts) throws Exception {
        try {
            BenchmarkRunner.main(new String[] {
                    "--compare-pricing",
                    "--cuts",
                    cuts
            });
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null
                    || !message.contains("no-cut pricing audits only")
                    || !message.contains("programmatic pricing tests")) {
                throw new AssertionError("compare-pricing should reject unsupported CLI cuts clearly: " + cuts,
                        expected);
            }
            return;
        }
        throw new AssertionError("compare-pricing must not silently ignore unsupported CLI cuts: " + cuts);
    }

    private static void assertUnknownOption(
            String label,
            CapturedCommand command,
            String commandName,
            String unknownOption,
            String supportedOption) {
        assertEquals(label + " exit", 2, command.exitCode());
        assertEquals(label + " stdout", "", command.stdout());
        if (!command.stderr().contains("ERROR: ")
                || !command.stderr().contains("Unknown option for " + commandName + ": --" + unknownOption)
                || !command.stderr().contains("--" + supportedOption)) {
            throw new AssertionError(label + " should explain the unknown option and supported options: "
                    + command.stderr());
        }
    }

    private static void assertUnexpectedPositional(
            String label,
            CapturedCommand command,
            String commandName,
            String unexpectedValue,
            String usage) {
        assertEquals(label + " exit", 2, command.exitCode());
        assertEquals(label + " stdout", "", command.stdout());
        if (!command.stderr().contains("ERROR: ")
                || !command.stderr().contains("Unexpected positional argument for " + commandName + ": "
                        + unexpectedValue)
                || !command.stderr().contains("Positional usage: " + usage)) {
            throw new AssertionError(label + " should explain the unexpected positional argument and usage: "
                    + command.stderr());
        }
    }

    private static void assertCommandErrorContains(
            String label,
            CapturedCommand command,
            String expectedMessagePart) {
        assertEquals(label + " exit", 2, command.exitCode());
        assertEquals(label + " stdout", "", command.stdout());
        if (!command.stderr().contains("ERROR: ") || !command.stderr().contains(expectedMessagePart)) {
            throw new AssertionError(label + " should explain the command error: " + command.stderr());
        }
    }

    private static void assertBcpRejectsUnsupportedBranching() throws Exception {
        assertUnsupportedBranching("BcpRunner", () -> new BcpRunner().runDetailed(tinyA(), "none", "none"));
        assertUnsupportedBranching("RunBcp", () -> RunBcp.main(new String[] {
                "--branching",
                "none"
        }));
        assertUnsupportedBranching("BenchmarkRunner", () -> BenchmarkRunner.main(new String[] {
                "--branching",
                "none"
        }));
    }

    private static void assertUnsupportedBranching(String label, ThrowingRunnable command) throws Exception {
        try {
            command.run();
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("Only --branching timo is supported")) {
                throw new AssertionError(label + " should reject unsupported branching clearly", expected);
            }
            return;
        }
        throw new AssertionError(label + " must not silently run unsupported branching mode");
    }

    private static void assertSchemaHeader(String fileName, String expected) throws Exception {
        Path schema = Path.of("logs", "schema", fileName);
        if (!Files.exists(schema)) {
            schema = Path.of("G:\\bid\\pdptw-bcp-java-gurobi\\logs\\schema", fileName);
        }
        if (!Files.exists(schema)) {
            throw new AssertionError("missing trace schema file: " + fileName);
        }
        List<String> rows = Files.readAllLines(schema);
        if (rows.isEmpty()) {
            throw new AssertionError("trace schema must contain a header: " + fileName);
        }
        assertEquals("trace schema header " + fileName, expected, rows.get(0));
    }

    private static void assertBcpTraceActiveCutCount(String label, String trace, int expectedActiveCutCount) {
        String[] rows = trace.split("\\R", -1);
        if (!TraceCsv.BCP_NODE_HEADER.equals(rows[0])) {
            throw new AssertionError(label + " header mismatch");
        }
        if (rows.length < 2) {
            throw new AssertionError(label + " should contain at least one node row");
        }
        int expectedColumns = splitCsvLine(TraceCsv.BCP_NODE_HEADER).size();
        for (int index = 1; index < rows.length; index++) {
            List<String> fields = splitCsvLine(rows[index]);
            if (fields.size() != expectedColumns) {
                throw new AssertionError(label + " row has wrong field count: " + rows[index]);
            }
            int activeCutCount = Integer.parseInt(fields.get(5));
            if (activeCutCount != expectedActiveCutCount) {
                throw new AssertionError(label + " activeCutCount expected "
                        + expectedActiveCutCount + " but got " + activeCutCount
                        + " row=" + rows[index]);
            }
        }
    }

    private static BranchAndPriceSolver.NodeRecord nodeWithConstraint(
            BranchAndPriceSolver.Result result,
            String constraint) {
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            if (record.constraints().contains(constraint)) {
                return record;
            }
        }
        throw new AssertionError("missing node with constraint: " + constraint);
    }

    private static BranchAndPriceSolver.NodeRecord nodeWithConstraintPrefix(
            BranchAndPriceSolver.Result result,
            String requiredConstraint,
            String prefix) {
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            if (!record.constraints().contains(requiredConstraint)) {
                continue;
            }
            for (String constraint : record.constraints()) {
                if (constraint.startsWith(prefix)) {
                    return record;
                }
            }
        }
        throw new AssertionError("missing node with constraint " + requiredConstraint + " and prefix " + prefix);
    }

    private static List<String> traceRowWithConstraint(String trace, String constraint) {
        String[] rows = trace.split("\\R", -1);
        for (int index = 1; index < rows.length; index++) {
            List<String> fields = splitCsvLine(rows[index]);
            if (fields.size() > 5 && fields.get(4).equals(constraint)) {
                return fields;
            }
        }
        throw new AssertionError("missing trace row with constraint: " + constraint);
    }

    private static List<String> traceRowWithConstraintPrefix(String trace, String requiredConstraint, String prefix) {
        String[] rows = trace.split("\\R", -1);
        for (int index = 1; index < rows.length; index++) {
            List<String> fields = splitCsvLine(rows[index]);
            if (fields.size() <= 5) {
                continue;
            }
            String constraints = fields.get(4);
            if (constraints.contains(requiredConstraint) && constraints.contains(prefix)) {
                return fields;
            }
        }
        throw new AssertionError("missing trace row with constraint " + requiredConstraint + " and prefix " + prefix);
    }

    private static void assertTraceActiveCutCount(
            String label,
            List<String> fields,
            int expectedActiveCutCount) {
        assertEquals(label + " activeCutCount", Integer.toString(expectedActiveCutCount), fields.get(5));
    }

    private static void assertPricedTraceRowsHaveLabelCounters(String label, String trace) {
        String[] rows = trace.split("\\R", -1);
        int expectedColumns = splitCsvLine(TraceCsv.BCP_NODE_HEADER).size();
        for (int index = 1; index < rows.length; index++) {
            List<String> fields = splitCsvLine(rows[index]);
            if (fields.size() != expectedColumns) {
                throw new AssertionError(label + " row has wrong field count: " + rows[index]);
            }
            int pricingCalls = Integer.parseInt(fields.get(9));
            if (pricingCalls <= 0) {
                continue;
            }
            if ("NA".equals(fields.get(12))
                    || "NA".equals(fields.get(13))
                    || "NA".equals(fields.get(14))
                    || Integer.parseInt(fields.get(12)) <= 0
                    || Integer.parseInt(fields.get(13)) <= 0
                    || Integer.parseInt(fields.get(14)) < 0) {
                throw new AssertionError(label + " priced row must expose labeling counters: " + rows[index]);
            }
        }
    }

    private static void assertBcpTraceLabelCounterTotals(
            String label,
            String trace,
            BenchmarkCsv.Row summary) {
        String[] rows = trace.split("\\R", -1);
        int expectedColumns = splitCsvLine(TraceCsv.BCP_NODE_HEADER).size();
        int forwardLabels = 0;
        int backwardLabels = 0;
        int dominatedLabels = 0;
        boolean sawPricedRow = false;
        for (int index = 1; index < rows.length; index++) {
            List<String> fields = splitCsvLine(rows[index]);
            if (fields.size() != expectedColumns) {
                throw new AssertionError(label + " row has wrong field count: " + rows[index]);
            }
            int pricingCalls = Integer.parseInt(fields.get(9));
            if (pricingCalls <= 0) {
                continue;
            }
            sawPricedRow = true;
            if ("NA".equals(fields.get(12)) || "NA".equals(fields.get(13)) || "NA".equals(fields.get(14))) {
                throw new AssertionError(label + " priced row must not contain NA label counters: " + rows[index]);
            }
            forwardLabels += Integer.parseInt(fields.get(12));
            backwardLabels += Integer.parseInt(fields.get(13));
            dominatedLabels += Integer.parseInt(fields.get(14));
        }
        if (!sawPricedRow) {
            throw new AssertionError(label + " must contain at least one priced node row");
        }
        assertEquals(label + " forward-label trace total", summary.forwardLabels(), forwardLabels);
        assertEquals(label + " backward-label trace total", summary.backwardLabels(), backwardLabels);
        assertEquals(label + " dominated-label trace total", summary.dominatedLabels(), dominatedLabels);
    }

    private static List<String> splitCsvLine(String row) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < row.length(); index++) {
            char ch = row.charAt(index);
            if (quoted) {
                if (ch == '"') {
                    if (index + 1 < row.length() && row.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private static Instance forcedBranchingInstance() {
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
        setRouteArcCosts(matrix, 2.0, 0, 1, 2, 4, 5, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1, 3, 4, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 2, 3, 5, 6, 7);
        return new Instance(
                "forced-branching",
                3,
                3,
                2,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> forcedBranchingColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("forced_0", Route.of(0, 1, 2, 4, 5, 7), instance),
                RouteColumn.fromRoute("forced_1", Route.of(0, 1, 3, 4, 6, 7), instance),
                RouteColumn.fromRoute("forced_2", Route.of(0, 2, 3, 5, 6, 7), instance),
                RouteColumn.fromRoute("forced_3", Route.of(0, 3, 2, 1, 4, 5, 6, 7), instance));
    }

    private static Instance comboBranchPricingInstance() {
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
        setRouteArcCosts(matrix, 2.0, 0, 1, 2, 4, 5, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1, 3, 4, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 2, 3, 5, 6, 7);
        matrix[0][3] = 749.0;
        matrix[3][6] = 749.0;
        return new Instance(
                "combo-branch-pricing",
                3,
                3,
                2,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static Instance subsetRowSeparationInstance() {
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
        setRouteArcCosts(matrix, 2.0, 0, 1, 4, 2, 5, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1, 4, 3, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 2, 5, 3, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1);
        setRouteArcCosts(matrix, 14.0 / 6.0, 1, 2, 3, 4, 5, 6, 7);
        return new Instance(
                "runner-sr-separation",
                3,
                3,
                3,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> subsetRowSeparationColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("sr_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("sr_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("sr_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("sr_full_123", Route.of(0, 1, 2, 3, 4, 5, 6, 7), instance));
    }

    private static RobustCutRow subsetRowSeparationRobustCutRow() {
        Map<RobustCut.Arc, Double> coefficients = new java.util.LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(0, 1), 1.0);
        coefficients.put(new RobustCut.Arc(0, 2), -1.0);
        return RobustCutRow.ofArcCoefficients(
                "runner_sr_mixed_robust_candidate",
                MasterCutRow.Sense.LESS_EQUAL,
                0.25,
                coefficients);
    }

    private static Instance queuedChildSrSeparationInstance() {
        java.util.ArrayList<Vertex> vertices = new java.util.ArrayList<Vertex>();
        vertices.add(new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0));
        for (int requestId = 1; requestId <= 5; requestId++) {
            vertices.add(new Vertex(
                    requestId,
                    Vertex.Type.PICKUP,
                    requestId,
                    requestId,
                    0.0,
                    0.0,
                    100.0,
                    0.0,
                    1));
        }
        for (int requestId = 1; requestId <= 5; requestId++) {
            vertices.add(new Vertex(
                    requestId + 5,
                    Vertex.Type.DELIVERY,
                    requestId,
                    requestId + 5,
                    0.0,
                    0.0,
                    100.0,
                    0.0,
                    -1));
        }
        vertices.add(new Vertex(11, Vertex.Type.DEPOT_END, 0, 11.0, 0.0, 0.0, 100.0, 0.0, 0));

        double[][] matrix = denseMatrix(12, 1000.0);
        setRouteArcCosts(matrix, 100.0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        int[][] triples = new int[][] {
                new int[] {1, 2, 3},
                new int[] {2, 3, 4},
                new int[] {3, 4, 5},
                new int[] {4, 5, 1},
                new int[] {5, 1, 2}
        };
        for (int[] triple : triples) {
            setRouteArcCosts(matrix, 6.0,
                    0,
                    triple[0],
                    triple[1],
                    triple[2],
                    triple[0] + 5,
                    triple[1] + 5,
                    triple[2] + 5,
                    11);
        }
        for (int requestId = 1; requestId <= 5; requestId++) {
            setRouteArcCosts(matrix, 50.0, 0, requestId, requestId + 5, 11);
        }
        for (int[] pair : queuedChildOddCyclePairs()) {
            setRouteArcCosts(matrix, 2.0, 0, pair[0], pair[1], pair[0] + 5, pair[1] + 5, 11);
        }
        return new Instance(
                "runner-global-sr-propagation",
                5,
                5,
                3,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> queuedChildSrSeparationColumns(Instance instance) {
        java.util.ArrayList<RouteColumn> columns = new java.util.ArrayList<RouteColumn>();
        int index = 0;
        for (int[] pair : queuedChildOddCyclePairs()) {
            columns.add(RouteColumn.fromRoute(
                    "odd_pair_" + index,
                    Route.of(0, pair[0], pair[1], pair[0] + 5, pair[1] + 5, 11),
                    instance));
            index++;
        }
        for (int requestId = 1; requestId <= 5; requestId++) {
            columns.add(RouteColumn.fromRoute(
                    "odd_single_" + requestId,
                    Route.of(0, requestId, requestId + 5, 11),
                    instance));
        }
        int[][] triples = new int[][] {
                new int[] {1, 2, 3},
                new int[] {2, 3, 4},
                new int[] {3, 4, 5},
                new int[] {4, 5, 1},
                new int[] {5, 1, 2}
        };
        index = 0;
        for (int[] triple : triples) {
            columns.add(RouteColumn.fromRoute(
                    "odd_triple_" + index,
                    Route.of(0,
                            triple[0],
                            triple[1],
                            triple[2],
                            triple[0] + 5,
                            triple[1] + 5,
                            triple[2] + 5,
                            11),
                    instance));
            index++;
        }
        columns.add(RouteColumn.fromRoute(
                "odd_full",
                Route.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
                instance));
        return List.copyOf(columns);
    }

    private static int[][] queuedChildOddCyclePairs() {
        return new int[][] {
                new int[] {1, 2},
                new int[] {2, 3},
                new int[] {3, 4},
                new int[] {4, 5},
                new int[] {5, 1}
        };
    }

    private static Instance robustTwoPathSeparationInstance() {
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
                "runner-robust-two-path",
                3,
                2,
                2,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> robustTwoPathPairColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("two_path_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("two_path_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("two_path_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance));
    }

    private static Instance robustTwoPathSubsetRowSeparationInstance() {
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
                "runner-generated-robust-sr",
                3,
                2,
                2,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> robustTwoPathSubsetRowColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("generated_mixed_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("generated_mixed_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("generated_mixed_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("generated_mixed_single_3", Route.of(0, 3, 6, 7), instance));
    }

    private static Instance robustRoundedCapacitySubsetRowSeparationInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] cost = denseMatrix(8, 100.0);
        setRouteArcCosts(cost, 1.0, 0, 1, 2, 4, 5, 7);
        setRouteArcCosts(cost, 1.0, 0, 1, 3, 4, 6, 7);
        setRouteArcCosts(cost, 1.0, 0, 2, 3, 5, 6, 7);
        setRouteArcCosts(cost, 6.0, 0, 3, 6, 7);
        double[][] time = denseMatrix(8, 100.0);
        setRouteArcCosts(time, 1.0, 0, 1, 2, 4, 5, 7);
        setRouteArcCosts(time, 1.0, 0, 1, 3, 4, 6, 7);
        setRouteArcCosts(time, 1.0, 0, 2, 3, 5, 6, 7);
        setRouteArcCosts(time, 1.0, 0, 3, 6, 7);
        return new Instance(
                "runner-generated-rcc-sr",
                3,
                2,
                2,
                vertices,
                cost,
                time,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> robustRoundedCapacitySubsetRowColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("generated_rcc_mixed_pair_12", Route.of(0, 1, 2, 4, 5, 7), instance),
                RouteColumn.fromRoute("generated_rcc_mixed_pair_13", Route.of(0, 1, 3, 4, 6, 7), instance),
                RouteColumn.fromRoute("generated_rcc_mixed_pair_23", Route.of(0, 2, 3, 5, 6, 7), instance),
                RouteColumn.fromRoute("generated_rcc_mixed_single_3", Route.of(0, 3, 6, 7), instance));
    }

    private static List<RouteColumn> robustRoundedCapacityPairColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("rounded_capacity_pair_12", Route.of(0, 1, 2, 4, 5, 7), instance),
                RouteColumn.fromRoute("rounded_capacity_pair_13", Route.of(0, 1, 3, 4, 6, 7), instance),
                RouteColumn.fromRoute("rounded_capacity_pair_23", Route.of(0, 2, 3, 5, 6, 7), instance));
    }

    private static List<RouteColumn> comboBranchPricingColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("A", Route.of(0, 1, 2, 4, 5, 7), instance),
                RouteColumn.fromRoute("B", Route.of(0, 1, 3, 4, 6, 7), instance),
                RouteColumn.fromRoute("C", Route.of(0, 2, 3, 5, 6, 7), instance),
                RouteColumn.fromRoute("D_all", Route.of(0, 3, 2, 1, 4, 5, 6, 7), instance),
                RouteColumn.fromRoute("E_candidate", Route.of(0, 3, 6, 7), instance));
    }

    private static RobustCutRow comboBranchPricingCutRow() {
        Map<RobustCut.Arc, Double> coefficients = new java.util.LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 3), 1.0);
        coefficients.put(new RobustCut.Arc(3, 6), -1.0);
        return RobustCutRow.ofArcCoefficients(
                "combo_runner_active_cut_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                0.5,
                coefficients);
    }

    private static Instance robustCutPricingInstance() {
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
                "robust-cut-runner-pricing",
                2,
                2,
                1,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> robustCutPricingColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("robust_seed", Route.of(0, 1, 2, 3, 4, 5), instance),
                RouteColumn.fromRoute("robust_only_priced_with_cut", Route.of(0, 2, 1, 3, 4, 5), instance));
    }

    private static RobustCutRow robustNodePricingCutRow() {
        Map<RobustCut.Arc, Double> coefficients = new java.util.LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 2), 1.0);
        coefficients.put(new RobustCut.Arc(0, 2), -1.0);
        return RobustCutRow.ofArcCoefficients(
                "robust_runner_pricing_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                0.5,
                coefficients);
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
        for (org.pdptw.cuts.SubsetRowCut cut : context.subsetRowCuts()) {
            sum += SRPricingAdjuster.routePricingAdjustment(cut, context.instance(), column.vertexIds());
        }
        return sum;
    }

    private static String captureStdout(ThrowingRunnable command) throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            command.run();
        } finally {
            System.setOut(originalOut);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static CapturedCommand captureCommand(ThrowingIntCommand command) throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8);
                PrintStream capturedErr = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            int exitCode = command.run();
            return new CapturedCommand(
                    exitCode,
                    output.toString(StandardCharsets.UTF_8),
                    error.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingIntCommand {
        int run() throws Exception;
    }

    private record CapturedCommand(int exitCode, String stdout, String stderr) {
    }

    private static final class CountingContextPricingSolver implements PricingSolver {
        private final RouteColumn route;
        private int contextCalls;
        private int matrixCalls;

        private CountingContextPricingSolver(RouteColumn route) {
            this.route = route;
        }

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            matrixCalls++;
            throw new AssertionError("BcpRunner.withRootLabeling should pass PricingContext to pricing");
        }

        @Override
        public PricingResult price(PricingContext context) {
            contextCalls++;
            double reducedCost = context.directReducedCost(route.vertexIds());
            PricingResult.Stats stats = PricingResult.Stats.of(3, 4, 0, 0, 0, 1, 2);
            if (reducedCost < -TOLERANCE) {
                return PricingResult.exact(List.of(route), reducedCost, stats);
            }
            return PricingResult.noNegativeColumn(reducedCost, stats);
        }

        private int contextCalls() {
            return contextCalls;
        }

        private int matrixCalls() {
            return matrixCalls;
        }
    }

    private static final class FiniteContextPricingSolver implements PricingSolver {
        private final List<RouteColumn> routeUniverse;
        private int contextCalls;
        private int matrixCalls;
        private int robustContextCalls;
        private int nonZeroRobustDualCalls;
        private int robustReducedCostShiftCalls;
        private int robustSeedPositiveShiftCalls;
        private int robustOnlyNegativeShiftCalls;
        private int subsetRowContextCalls;
        private int nonZeroSubsetRowDualCalls;
        private int subsetRowReducedCostShiftCalls;
        private int bothRobustAndSubsetRowContextCalls;
        private int setOutflowContextCalls;

        private FiniteContextPricingSolver(List<RouteColumn> routeUniverse) {
            this.routeUniverse = List.copyOf(routeUniverse);
        }

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            matrixCalls++;
            throw new AssertionError("finite context solver should receive PricingContext");
        }

        @Override
        public PricingResult price(PricingContext context) {
            contextCalls++;
            PricingContext branchOnly = context.hasSetOutflowPricingRules()
                    ? PricingContext.withSetOutflowPricing(
                            PricingContext.noCuts(context.matrices()),
                            context.setOutflowPricingRules())
                    : PricingContext.noCuts(context.matrices());
            PricingContext withoutSubsetRows = context.hasRobustCuts()
                    ? PricingContext.withCutsAndSetOutflowPricing(
                            context.matrices(),
                            context.robustCuts(),
                            List.of(),
                            context.setOutflowPricingRules())
                    : branchOnly;
            if (context.hasRobustCuts()) {
                robustContextCalls++;
                for (RobustCut cut : context.robustCuts()) {
                    if (Math.abs(cut.dualValue()) > TOLERANCE) {
                        nonZeroRobustDualCalls++;
                        break;
                    }
                }
            }
            if (context.hasSubsetRowCuts()) {
                subsetRowContextCalls++;
                for (org.pdptw.cuts.SubsetRowCut cut : context.subsetRowCuts()) {
                    if (Math.abs(cut.sigma()) > TOLERANCE) {
                        nonZeroSubsetRowDualCalls++;
                        break;
                    }
                }
            }
            if (context.hasRobustCuts() && context.hasSubsetRowCuts()) {
                bothRobustAndSubsetRowContextCalls++;
            }
            if (context.hasSetOutflowPricingRules()) {
                setOutflowContextCalls++;
            }
            double bestReducedCost = Double.POSITIVE_INFINITY;
            java.util.ArrayList<RouteColumn> negativeColumns = new java.util.ArrayList<RouteColumn>();
            for (RouteColumn column : routeUniverse) {
                double reducedCost = context.directReducedCost(column.vertexIds());
                if (context.hasRobustCuts()) {
                    double robustOnlyReducedCost = withoutSubsetRows.directReducedCost(column.vertexIds());
                    double robustShift = robustOnlyReducedCost - branchOnly.directReducedCost(column.vertexIds());
                    double expectedRobustShift = robustArcPriceSum(context, column);
                    assertClose("BCP robust direct RC shift " + column.name(), expectedRobustShift, robustShift);
                    if (Math.abs(robustShift) > TOLERANCE) {
                        robustReducedCostShiftCalls++;
                    }
                    if ("robust_seed".equals(column.name()) && robustShift > TOLERANCE) {
                        robustSeedPositiveShiftCalls++;
                    }
                    if ("robust_only_priced_with_cut".equals(column.name()) && robustShift < -TOLERANCE) {
                        robustOnlyNegativeShiftCalls++;
                    }
                }
                if (context.hasSubsetRowCuts()) {
                    double subsetRowShift = reducedCost - withoutSubsetRows.directReducedCost(column.vertexIds());
                    double expectedSubsetRowShift = subsetRowPriceSum(context, column);
                    assertClose("BCP SR direct RC shift " + column.name(), expectedSubsetRowShift, subsetRowShift);
                    if (Math.abs(subsetRowShift) > TOLERANCE) {
                        subsetRowReducedCostShiftCalls++;
                    }
                }
                if (reducedCost < bestReducedCost) {
                    bestReducedCost = reducedCost;
                }
                if (reducedCost < -TOLERANCE) {
                    negativeColumns.add(column);
                }
            }
            PricingResult.Stats stats = PricingResult.Stats.of(5, 6, 0, 0, 0, routeUniverse.size(), 1);
            if (negativeColumns.isEmpty()) {
                return PricingResult.noNegativeColumn(bestReducedCost, stats);
            }
            return PricingResult.exact(negativeColumns, bestReducedCost, stats);
        }

        private int contextCalls() {
            return contextCalls;
        }

        private int matrixCalls() {
            return matrixCalls;
        }

        private int robustContextCalls() {
            return robustContextCalls;
        }

        private int nonZeroRobustDualCalls() {
            return nonZeroRobustDualCalls;
        }

        private int robustReducedCostShiftCalls() {
            return robustReducedCostShiftCalls;
        }

        private int robustSeedPositiveShiftCalls() {
            return robustSeedPositiveShiftCalls;
        }

        private int robustOnlyNegativeShiftCalls() {
            return robustOnlyNegativeShiftCalls;
        }

        private int subsetRowContextCalls() {
            return subsetRowContextCalls;
        }

        private int nonZeroSubsetRowDualCalls() {
            return nonZeroSubsetRowDualCalls;
        }

        private int subsetRowReducedCostShiftCalls() {
            return subsetRowReducedCostShiftCalls;
        }

        private int bothRobustAndSubsetRowContextCalls() {
            return bothRobustAndSubsetRowContextCalls;
        }

        private int setOutflowContextCalls() {
            return setOutflowContextCalls;
        }
    }
}
