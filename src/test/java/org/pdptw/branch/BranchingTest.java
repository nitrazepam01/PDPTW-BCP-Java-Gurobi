package org.pdptw.branch;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.Vertex;
import org.pdptw.cli.TraceCsv;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.cuts.RobustCut;
import org.pdptw.cuts.RobustCutRow;
import org.pdptw.cuts.SRPricingAdjuster;
import org.pdptw.cuts.SubsetRowCut;
import org.pdptw.cuts.SubsetRowCutRow;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingContext;
import org.pdptw.pricing.PricingMode;
import org.pdptw.pricing.PricingResult;
import org.pdptw.pricing.PricingSolver;
import org.pdptw.pricing.ReducedCostMatrices;
import org.pdptw.pricing.SetOutflowPricingRule;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BranchingTest {
    private static final double TOLERANCE = 1.0e-7;

    private BranchingTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("BranchingTest OK");
    }

    public static void run() throws Exception {
        assertTinyDVehicleCountBranching();
        assertBranchAndPriceHierarchy();
        assertIntegerVehicleCountUsesSetOutflow();
        assertSetOutflowCountsRequestSequenceExits();
        assertBranchMasterRowPricingDualSign();
        assertSetOutflowPricingContextMatchesBranchRow();
        assertNodeQueueOrdersByBound();
        assertForcedBranchTreeProcessesChildNodes();
        assertBranchTreeNodeLimitDoesNotClaimOptimality();
        assertPositiveArtificialDoesNotClaimIncumbent();
        assertActiveRobustCutRowsAffectNodePricing();
        assertBranchNodeRobustCandidateSeparationAddsViolatedCut();
        assertBranchNodeRobustTwoPathSeparationAddsGeneratedCut();
        assertBranchNodeRobustTwoPathSeparationExcludesInheritedBranchRows();
        assertBranchNodeRobustRoundedCapacitySeparationAddsGeneratedCut();
        assertBranchNodeRobustRoundedCapacitySeparationExcludesInheritedBranchRows();
        assertBranchNodeRobustAndSubsetRowSeparationCanCoexist();
        assertBranchNodeGeneratedRobustAndSubsetRowSeparationCanCoexist();
        assertActiveCutRowsAreInheritedByChildNodes();
        assertActiveCutAndInheritedBranchRowsAffectPublicChildTree();
        assertNodePricingBackendReceivesCutAndBranchRows();
        assertNodePricingBackendRejectsNegativeResultWithoutColumns();
        assertNodePricingResultRejectsImpossibleColumnCount();
        assertLabelingNodePricingBackendPricesRootMissingColumns();
        assertLabelingNodePricingBackendFiltersCurrentColumns();
        assertLabelingNodePricingBackendFiltersMixedCurrentColumns();
        assertLabelingNodePricingBackendAdjustsFleetDualForVehicleCountRows();
        assertLabelingNodePricingBackendSupportsVehicleCountBranchRows();
        assertLabelingNodePricingBackendSupportsSetOutflowBranchRows();
        assertLabelingNodePricingBackendCombinesActiveCutsAndBranchRows();
        assertLabelingNodePricingBackendRejectsContextBlindSetOutflowSolver();
        assertLabelingNodePricingBackendRejectsEmptyNegativeResult();
        assertLabelingNodePricingBackendRejectsNonExactResult();
        assertHybridNodePricingBackendUsesLabelingAtRoot();
        assertHybridNodePricingBackendUsesLabelingForVehicleCountRows();
        assertHybridNodePricingBackendUsesLabelingForSetOutflowRows();
        assertBranchAndPriceFactoryUsesRootLabeling();
        assertNodeCutPropagationPolicyIsExplicit();
        assertActiveSubsetRowCutRowsAffectNodePricing();
        assertBranchNodeSubsetRowSeparationAddsViolatedCut();
        assertBranchNodeSubsetRowSeparationExcludesInheritedBranchRows();
        assertBranchNodeSubsetRowSeparationSkipsNoViolation();
        assertBranchTreeSubsetRowSeparationCutsQueuedChildNode();
        assertSeparatedSubsetRowsAreNotInheritedByDescendantNodes();
        assertGlobalSubsetRowsAreInheritedByDescendantNodes();
        assertGlobalSubsetRowsPropagateThroughQueuedBranchTree();
        assertActiveCutAndInheritedBranchRowPricesCombineOnce();
        assertForbiddenProductionNamesAbsent();
    }

    private static void assertTinyDVehicleCountBranching() throws Exception {
        String fixture = Files.readString(referencePath("tiny-d-branching.json"));
        if (!fixture.contains("\"vehicleCount\": 1.5")
                || !fixture.contains("\"constraint\": \"sum_lambda <= 1\"")
                || !fixture.contains("\"constraint\": \"sum_lambda >= 2\"")) {
            throw new AssertionError("Tiny-D branching fixture is missing expected vehicle-count data");
        }

        List<String> rows = Files.readAllLines(expectedPath("tiny-d-branching.csv"));
        if (rows.size() != 2) {
            throw new AssertionError("Tiny-D expected CSV should contain header plus one data row");
        }
        List<String> fields = splitCsv(rows.get(1));
        List<BranchDecision.RouteValue> solution = List.of(
                BranchDecision.RouteValue.of("A", Double.parseDouble(fields.get(0)), ints(1, 2)),
                BranchDecision.RouteValue.of("B", Double.parseDouble(fields.get(1)), ints(1, 3)),
                BranchDecision.RouteValue.of("C", Double.parseDouble(fields.get(2)), ints(2, 3)));

        double vehicleCount = VehicleCountBrancher.vehicleCount(solution);
        assertClose("Tiny-D vehicle count", Double.parseDouble(fields.get(3)), vehicleCount);

        BranchDecision decision = new VehicleCountBrancher()
                .branch(BranchNode.root(), solution)
                .orElseThrow(() -> new AssertionError("Tiny-D should create vehicle-count branches"));
        assertEquals("Tiny-D branch type", BranchConstraint.Type.VEHICLE_COUNT, decision.type());
        assertClose("Tiny-D branch value", 1.5, decision.branchingValue());
        assertEquals("Tiny-D left expression", fields.get(4), decision.leftConstraint().expression());
        assertEquals("Tiny-D right expression", fields.get(5), decision.rightConstraint().expression());
        assertEquals("Tiny-D left name", "vehicle_count_le_1", decision.leftConstraint().name());
        assertEquals("Tiny-D right name", "vehicle_count_ge_2", decision.rightConstraint().name());
        assertDoesNotContainForbiddenWord("Tiny-D decision left", decision.leftConstraint().name());
        assertDoesNotContainForbiddenWord("Tiny-D decision right", decision.rightConstraint().name());
        assertEquals("Tiny-D left depth", 1, decision.leftChild().depth());
        assertEquals("Tiny-D right depth", 1, decision.rightChild().depth());
    }

    private static void assertIntegerVehicleCountUsesSetOutflow() {
        List<BranchDecision.RouteValue> solution = List.of(
                BranchDecision.RouteValue.of("D", 0.5, ints(1, 2)),
                BranchDecision.RouteValue.of("E", 0.5, ints(2, 3)));
        assertClose("integer vehicle count setup", 1.0, VehicleCountBrancher.vehicleCount(solution));
        Optional<BranchDecision> vehicleDecision = new VehicleCountBrancher().branch(BranchNode.root(), solution);
        if (vehicleDecision.isPresent()) {
            throw new AssertionError("integer vehicle count should not create vehicle-count branches");
        }

        BranchDecision setDecision = new SetOutflowBrancher()
                .branch(BranchNode.root(), solution)
                .orElseThrow(() -> new AssertionError("integer vehicle count should allow set-outflow branching"));
        assertEquals("set-outflow branch type", BranchConstraint.Type.SET_OUTFLOW, setDecision.type());
        assertClose("set-outflow branch value", 0.5, setDecision.branchingValue());
        assertEquals("set-outflow left expression", "x(delta+({1})) <= 0", setDecision.leftConstraint().expression());
        assertEquals("set-outflow right expression", "x(delta+({1})) >= 1", setDecision.rightConstraint().expression());
        assertDoesNotContainForbiddenWord("set-outflow decision left", setDecision.leftConstraint().name());
        assertDoesNotContainForbiddenWord("set-outflow decision right", setDecision.rightConstraint().name());
    }

    private static void assertBranchAndPriceHierarchy() {
        BranchAndPriceSolver solver = new BranchAndPriceSolver();
        List<BranchDecision.RouteValue> fractionalVehicle = List.of(
                BranchDecision.RouteValue.of("A", 0.5, ints(1, 2)),
                BranchDecision.RouteValue.of("B", 0.5, ints(1, 3)),
                BranchDecision.RouteValue.of("C", 0.5, ints(2, 3)));
        BranchDecision vehicleDecision = solver.chooseBranch(BranchNode.root(), fractionalVehicle)
                .orElseThrow(() -> new AssertionError("fractional vehicle count should branch"));
        assertEquals("hierarchy branches on vehicles first",
                BranchConstraint.Type.VEHICLE_COUNT,
                vehicleDecision.type());

        List<BranchDecision.RouteValue> integerVehicle = List.of(
                BranchDecision.RouteValue.of("D", 0.5, ints(1, 2)),
                BranchDecision.RouteValue.of("E", 0.5, ints(2, 3)));
        BranchDecision setDecision = solver.chooseBranch(BranchNode.root(), integerVehicle)
                .orElseThrow(() -> new AssertionError("integer vehicle count should reach set outflow"));
        assertEquals("hierarchy reaches set outflow second",
                BranchConstraint.Type.SET_OUTFLOW,
                setDecision.type());
    }

    private static void assertSetOutflowCountsRequestSequenceExits() {
        BranchDecision.RouteValue route = BranchDecision.RouteValue.withRequestVisits(
                "sequence",
                1.0,
                ints(1, 2, 1, 3));
        assertEquals("request-sequence set-outflow exits",
                2,
                SetOutflowConstraint.routeCoefficient(route, ints(1)));
        assertEquals("fallback set-outflow coefficient from served set",
                1,
                SetOutflowConstraint.routeCoefficient(BranchDecision.RouteValue.of("served", 1.0, ints(1, 2)), ints(1)));
    }

    private static void assertBranchMasterRowPricingDualSign() {
        Instance instance = forcedBranchingInstance();
        RouteColumn column = RouteColumn.fromRoute("forced_pair", Route.of(0, 1, 2, 4, 5, 7), instance);
        BranchMasterRow vehicleLe = BranchMasterRow.of(VehicleCountConstraint.lessOrEqual(1));
        BranchMasterRow vehicleGe = BranchMasterRow.of(VehicleCountConstraint.greaterOrEqual(2));
        BranchMasterRow setOutflowLe = BranchMasterRow.of(SetOutflowConstraint.lessOrEqual(ints(1), 0));

        assertClose("vehicle <= branch route coefficient", 1.0, vehicleLe.routeCoefficient(instance, column));
        assertClose("vehicle <= branch price",
                3.0,
                vehicleLe.routePrice(instance, column, vehicleLe.pricingDualFromRawPi(-3.0)));
        assertClose("vehicle >= branch price",
                -3.0,
                vehicleGe.routePrice(instance, column, vehicleGe.pricingDualFromRawPi(3.0)));
        assertClose("set-outflow branch route coefficient", 2.0, setOutflowLe.routeCoefficient(instance, column));
        assertClose("set-outflow branch price",
                4.0,
                setOutflowLe.routePrice(instance, column, setOutflowLe.pricingDualFromRawPi(-2.0)));
    }

    private static void assertSetOutflowPricingContextMatchesBranchRow() {
        Instance instance = forcedBranchingInstance();
        List<Integer> route = ints(0, 1, 2, 4, 5, 7);
        RouteColumn column = RouteColumn.fromRoute("forced_pair", new Route(route), instance);
        SetOutflowConstraint setOutflow = SetOutflowConstraint.lessOrEqual(ints(1), 0);
        BranchMasterRow branchRow = BranchMasterRow.of(setOutflow);
        double pricingDual = branchRow.pricingDualFromRawPi(-2.0);
        PricingContext base = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        PricingContext withSetOutflow = PricingContext.withSetOutflowPricing(
                base,
                List.of(SetOutflowPricingRule.of(branchRow.name(), setOutflow.requestSet(), pricingDual)));

        assertClose("set-outflow context route price matches branch row",
                base.directReducedCost(route) + branchRow.routePrice(instance, column, pricingDual),
                withSetOutflow.directReducedCost(route));
        if (withSetOutflow.satisfiesForwardDti()) {
            throw new AssertionError("set-outflow state-dependent pricing must not certify forward DTI");
        }
        if (withSetOutflow.satisfiesBackwardPti()) {
            throw new AssertionError("set-outflow state-dependent pricing must not certify backward PTI");
        }
    }

    private static void assertNodeQueueOrdersByBound() {
        NodeQueue queue = new NodeQueue();
        BranchNode root = BranchNode.root();
        BranchNode high = root.child(VehicleCountConstraint.greaterOrEqual(2)).withLowerBound(9.0);
        BranchNode low = root.child(VehicleCountConstraint.lessOrEqual(1)).withLowerBound(3.0);
        queue.add(high);
        queue.add(low);
        assertEquals("queue size", 2, queue.size());
        BranchNode first = queue.poll().orElseThrow(() -> new AssertionError("missing first queued node"));
        assertEquals("queue first lower-bound node", low.id(), first.id());
        BranchNode second = queue.poll().orElseThrow(() -> new AssertionError("missing second queued node"));
        assertEquals("queue second lower-bound node", high.id(), second.id());
        if (!queue.isEmpty()) {
            throw new AssertionError("queue should be empty after polling both nodes");
        }
    }

    private static void assertForcedBranchTreeProcessesChildNodes() throws Exception {
        Instance instance = forcedBranchingInstance();
        List<RouteColumn> columns = forcedBranchingColumns(instance);
        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                8).solve(instance, columns);

        assertEquals("forced branch status",
                "optimal_tiny_branch_tree",
                result.status());
        if (result.createdNodes() != 3 || result.processedNodes() != 3) {
            throw new AssertionError("forced branch tree must process child nodes"
                    + " created=" + result.createdNodes()
                    + " processed=" + result.processedNodes());
        }
        if (!result.hasIncumbent()) {
            throw new AssertionError("forced branch tree must produce a finite incumbent");
        }
        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("forced root branches on vehicle count",
                BranchConstraint.Type.VEHICLE_COUNT,
                root.branchType().orElseThrow(() -> new AssertionError("root should branch")));
        assertClose("forced root lower bound", 15.0, root.lowerBound());
        if (root.pricingCalls() <= 1 || root.generatedColumns() <= 0) {
            throw new AssertionError("forced root must run node-level column generation from an incomplete pool"
                    + " pricingCalls=" + root.pricingCalls()
                    + " generatedColumns=" + root.generatedColumns());
        }

        boolean sawLeft = false;
        boolean sawRight = false;
        long summedPricingTimeMs = 0L;
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            if (record.pricingCalls() < 1) {
                throw new AssertionError("every processed branch node must run pricing: " + record.nodeId());
            }
            if (record.pricingTimeMs() < 0L) {
                throw new AssertionError("branch node pricing time must be measured: " + record.nodeId());
            }
            if (record.activeCutCount() != 0) {
                throw new AssertionError("no-cut branch tree should not report active cuts"
                        + " node=" + record.nodeId()
                        + " activeCutCount=" + record.activeCutCount());
            }
            summedPricingTimeMs += record.pricingTimeMs();
            for (String constraint : record.constraints()) {
                if ("sum_lambda <= 1".equals(constraint)) {
                    sawLeft = true;
                }
                if ("sum_lambda >= 2".equals(constraint)) {
                    sawRight = true;
                }
            }
            if (Double.isFinite(record.bestReducedCost()) && record.bestReducedCost() < -TOLERANCE) {
                throw new AssertionError("branch node pricing audit must find no missing negative column"
                        + " node=" + record.nodeId()
                        + " bestReducedCost=" + record.bestReducedCost());
            }
        }
        if (!sawLeft || !sawRight) {
            throw new AssertionError("forced branch tree did not process both vehicle-count children");
        }
        if (result.totalPricingTimeMs() != summedPricingTimeMs) {
            throw new AssertionError("branch result pricing time should sum node records");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("sum_lambda <= 1")
                || !trace.contains("sum_lambda >= 2")
                || !trace.contains("branched_vehicle_count")) {
            throw new AssertionError("forced branch trace must expose child constraints and concrete counters");
        }
    }

    private static void assertBranchTreeNodeLimitDoesNotClaimOptimality() throws Exception {
        Instance instance = forcedBranchingInstance();
        List<RouteColumn> columns = forcedBranchingColumns(instance);
        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1).solve(instance, columns);

        assertEquals("node-limited branch status", "node_limit", result.status());
        assertEquals("node-limited processed nodes", 1, result.processedNodes());
        assertEquals("node-limited created nodes", 3, result.createdNodes());
        assertEquals("node-limited record count", 1, result.nodeRecords().size());
        if (result.hasIncumbent() || !result.incumbentColumns().isEmpty()) {
            throw new AssertionError("node-limited tree must not report an incumbent"
                    + " columns=" + result.incumbentColumns().size());
        }

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("node-limited root prune reason", "branched_vehicle_count", root.pruneReason());
        assertEquals("node-limited root branch type",
                BranchConstraint.Type.VEHICLE_COUNT,
                root.branchType().orElseThrow(() -> new AssertionError("root should branch before node limit")));

        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("branched_vehicle_count,VEHICLE_COUNT")) {
            throw new AssertionError("node-limited trace must expose branch reason and type");
        }
    }

    private static void assertPositiveArtificialDoesNotClaimIncumbent() throws Exception {
        Instance instance = singleRequestPricingInstance();
        RouteColumn emptyRoute = RouteColumn.fromRoute("empty_real_route", Route.of(0, 3), instance);
        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                4).solve(instance, List.of(emptyRoute));

        assertEquals("positive-artificial branch status", "no_incumbent", result.status());
        assertEquals("positive-artificial processed nodes", 1, result.processedNodes());
        assertEquals("positive-artificial created nodes", 1, result.createdNodes());
        assertEquals("positive-artificial record count", 1, result.nodeRecords().size());
        if (result.hasIncumbent() || !result.incumbentColumns().isEmpty()) {
            throw new AssertionError("positive-artificial tree must not report an incumbent"
                    + " columns=" + result.incumbentColumns().size());
        }

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("positive-artificial prune reason", "positive_artificial", root.pruneReason());
        if (root.branchType().isPresent()) {
            throw new AssertionError("positive-artificial root must not branch");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("positive_artificial")) {
            throw new AssertionError("positive-artificial trace must expose prune reason");
        }
    }

    private static void assertActiveRobustCutRowsAffectNodePricing() throws Exception {
        Instance instance = robustCutPricingInstance();
        List<RouteColumn> columns = robustCutPricingColumns(instance);
        RobustCutRow activeCut = robustNodePricingCutRow();

        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1).solve(instance, columns, List.of(activeCut));

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("active robust root generated columns", 2, root.generatedColumns());
        if (root.pricingCalls() < 3) {
            throw new AssertionError("active robust pricing should require a second priced route"
                    + " pricingCalls=" + root.pricingCalls());
        }
        if (Double.isFinite(root.bestReducedCost()) && root.bestReducedCost() < -TOLERANCE) {
            throw new AssertionError("active robust pricing must finish with no missing negative column"
                    + " bestReducedCost=" + root.bestReducedCost());
        }
    }

    private static void assertBranchNodeRobustCandidateSeparationAddsViolatedCut() throws Exception {
        Instance instance = robustCutPricingInstance();
        List<RouteColumn> columns = robustCutPricingColumns(instance);
        RobustCutRow candidate = robustNodePricingCutRow();
        RecordingNodePricingBackend noCandidateBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver.Result noCandidate = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                noCandidateBackend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of()).solve(instance, columns);
        assertEquals("robust no-candidate active cuts", 0, noCandidate.nodeRecords().get(0).activeCutCount());
        for (BackendSnapshot snapshot : noCandidateBackend.snapshots()) {
            if (snapshot.hasRobustCuts()) {
                throw new AssertionError("branch-node robust no-candidate path should not receive robust context");
            }
        }

        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());

        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                backend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(candidate)).solve(instance, columns);

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("robust candidate root active cuts", 1, root.activeCutCount());
        assertEquals("robust candidate pricing snapshots", result.totalPricingCalls(), backend.snapshots().size());
        boolean sawSeparatedRobustContext = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && !snapshot.hasSubsetRowCuts()
                    && snapshot.branchRowCount() == 0
                    && snapshot.robustCutCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()) {
                sawSeparatedRobustContext = true;
            }
        }
        if (!sawSeparatedRobustContext) {
            throw new AssertionError("branch-node robust candidate separation should enter PricingContext");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,,1,")) {
            throw new AssertionError("branch-node robust candidate trace must expose activeCutCount=1");
        }

        RecordingNodePricingBackend duplicateBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver.Result duplicate = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                duplicateBackend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(candidate)).solve(instance, columns, List.of(candidate));
        assertEquals("robust candidate duplicate active cuts", 1, duplicate.nodeRecords().get(0).activeCutCount());

        RobustCutRow semanticDuplicateActive = robustNodePricingCutRow("custom_robust_node_pricing_probe");
        RecordingNodePricingBackend semanticDuplicateBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver.Result semanticDuplicate = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                semanticDuplicateBackend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(candidate)).solve(instance, columns, List.of(semanticDuplicateActive));
        assertEquals("robust candidate semantic duplicate active cuts",
                1,
                semanticDuplicate.nodeRecords().get(0).activeCutCount());
        boolean sawSemanticDuplicateContext = false;
        for (BackendSnapshot snapshot : semanticDuplicateBackend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && snapshot.robustCutCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()) {
                sawSemanticDuplicateContext = true;
            }
        }
        if (!sawSemanticDuplicateContext) {
            throw new AssertionError("branch-node robust semantic duplicate guard should retain the active"
                    + " robust row in PricingContext");
        }

        try {
            new BranchAndPriceSolver(
                    new VehicleCountBrancher(),
                    new SetOutflowBrancher(),
                    1000.0,
                    TOLERANCE,
                    1,
                    new RouteUniverseNodePricingBackend(),
                    NodeCutPropagationPolicy.GLOBAL_AUTOMATIC_SR)
                    .solve(instance, columns, List.of(candidate));
            throw new AssertionError("global SR propagation must reject active robust cut rows");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            if (message == null
                    || !message.contains("GLOBAL_AUTOMATIC_SR")
                    || !message.contains("active robust cut rows")) {
                throw new AssertionError("global SR active robust rejection should explain the boundary", expected);
            }
        }

        try {
            new BranchAndPriceSolver(
                    new VehicleCountBrancher(),
                    new SetOutflowBrancher(),
                    1000.0,
                    TOLERANCE,
                    1,
                    new RouteUniverseNodePricingBackend(),
                    NodeCutPropagationPolicy.GLOBAL_AUTOMATIC_SR,
                    List.of(candidate));
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("cannot be combined")) {
                throw new AssertionError("global robust/SR rejection should explain the boundary", expected);
            }
            return;
        }
        throw new AssertionError("automatic robust candidate separation must reject global SR policy");
    }

    private static void assertBranchNodeRobustTwoPathSeparationAddsGeneratedCut() throws Exception {
        Instance instance = twoPathSeparationInstance();
        List<RouteColumn> columns = twoPathPairColumns(instance);
        RecordingNodePricingBackend noGeneratorBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver.Result noGenerator = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                noGeneratorBackend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false).solve(instance, columns);
        assertEquals("two-path no-generator active cuts", 0, noGenerator.nodeRecords().get(0).activeCutCount());
        for (BackendSnapshot snapshot : noGeneratorBackend.snapshots()) {
            if (snapshot.hasRobustCuts()) {
                throw new AssertionError("branch-node two-path no-generator path should not receive robust context");
            }
        }

        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());

        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                backend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                true).solve(instance, columns);

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("two-path robust root active cuts", 1, root.activeCutCount());
        boolean sawGeneratedRobustContext = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && !snapshot.hasSubsetRowCuts()
                    && snapshot.branchRowCount() == 0
                    && snapshot.robustCutCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()) {
                sawGeneratedRobustContext = true;
            }
        }
        if (!sawGeneratedRobustContext) {
            throw new AssertionError("branch-node generated two-path robust row should enter PricingContext");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,,1,")) {
            throw new AssertionError("branch-node generated two-path robust trace must expose activeCutCount=1");
        }
    }

    private static void assertBranchNodeRobustTwoPathSeparationExcludesInheritedBranchRows() throws Exception {
        Instance instance = twoPathSeparationInstance();
        List<RouteColumn> columns = twoPathPairColumns(instance);
        BranchNode child = BranchNode.root().child(VehicleCountConstraint.lessOrEqual(10));

        RecordingNodePricingBackend noGeneratorBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver noGeneratorSolver = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                noGeneratorBackend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false);
        NodeRelaxationProbe noGenerator = solveNodeRelaxationForTest(
                noGeneratorSolver,
                instance,
                child,
                columns,
                List.of());
        assertEquals("two-path child no-generator active cuts", 0, noGenerator.activeCutCount());
        for (BackendSnapshot snapshot : noGeneratorBackend.snapshots()) {
            if (snapshot.hasRobustCuts()) {
                throw new AssertionError("two-path child no-generator path should not receive robust context");
            }
        }

        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver generatedSolver = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                backend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                true);
        NodeRelaxationProbe generated = solveNodeRelaxationForTest(
                generatedSolver,
                instance,
                child,
                columns,
                List.of());

        assertEquals("two-path child generated robust active cuts exclude branch row", 1, generated.activeCutCount());
        if (generated.pricingCalls() <= 0) {
            throw new AssertionError("two-path child generated robust path should enter pricing");
        }
        boolean sawBranchAndRobust = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.branchRowCount() == 1
                    && snapshot.hasRobustCuts()
                    && !snapshot.hasSubsetRowCuts()
                    && snapshot.robustCutCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()) {
                sawBranchAndRobust = true;
            }
        }
        if (!sawBranchAndRobust) {
            throw new AssertionError("generated robust child node should price with inherited branch row"
                    + " and robust cut context");
        }
    }

    private static void assertBranchNodeRobustRoundedCapacitySeparationAddsGeneratedCut() throws Exception {
        Instance instance = twoPathSeparationInstance();
        List<RouteColumn> columns = roundedCapacityPairColumns(instance);
        RecordingNodePricingBackend noGeneratorBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver.Result noGenerator = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                noGeneratorBackend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false,
                false).solve(instance, columns);
        assertEquals("rounded-capacity no-generator active cuts", 0, noGenerator.nodeRecords().get(0).activeCutCount());
        for (BackendSnapshot snapshot : noGeneratorBackend.snapshots()) {
            if (snapshot.hasRobustCuts()) {
                throw new AssertionError("rounded-capacity no-generator path should not receive robust context");
            }
        }

        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                backend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false,
                true).solve(instance, columns);

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("rounded-capacity robust root active cuts", 1, root.activeCutCount());
        boolean sawGeneratedRobustContext = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && !snapshot.hasSubsetRowCuts()
                    && snapshot.branchRowCount() == 0
                    && snapshot.robustCutCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()) {
                sawGeneratedRobustContext = true;
            }
        }
        if (!sawGeneratedRobustContext) {
            throw new AssertionError("branch-node generated rounded-capacity robust row should enter PricingContext");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,,1,")) {
            throw new AssertionError("branch-node generated rounded-capacity trace must expose activeCutCount=1");
        }
    }

    private static void assertBranchNodeRobustRoundedCapacitySeparationExcludesInheritedBranchRows()
            throws Exception {
        Instance instance = twoPathSeparationInstance();
        List<RouteColumn> columns = roundedCapacityPairColumns(instance);
        BranchNode child = BranchNode.root().child(VehicleCountConstraint.lessOrEqual(10));

        RecordingNodePricingBackend noGeneratorBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver noGeneratorSolver = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                noGeneratorBackend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false,
                false);
        NodeRelaxationProbe noGenerator = solveNodeRelaxationForTest(
                noGeneratorSolver,
                instance,
                child,
                columns,
                List.of());
        assertEquals("rounded-capacity child no-generator active cuts", 0, noGenerator.activeCutCount());
        for (BackendSnapshot snapshot : noGeneratorBackend.snapshots()) {
            if (snapshot.hasRobustCuts()) {
                throw new AssertionError("rounded-capacity child no-generator path should not receive robust context");
            }
        }

        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver generatedSolver = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                backend,
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                List.of(),
                false,
                true);
        NodeRelaxationProbe generated = solveNodeRelaxationForTest(
                generatedSolver,
                instance,
                child,
                columns,
                List.of());

        assertEquals("rounded-capacity child generated robust active cuts exclude branch row",
                1,
                generated.activeCutCount());
        if (generated.pricingCalls() <= 0) {
            throw new AssertionError("rounded-capacity child generated robust path should enter pricing");
        }
        boolean sawBranchAndRobust = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.branchRowCount() == 1
                    && snapshot.hasRobustCuts()
                    && !snapshot.hasSubsetRowCuts()
                    && snapshot.robustCutCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()) {
                sawBranchAndRobust = true;
            }
        }
        if (!sawBranchAndRobust) {
            throw new AssertionError("generated rounded-capacity child node should price with inherited branch row"
                    + " and robust cut context");
        }
    }

    private static void assertBranchNodeRobustAndSubsetRowSeparationCanCoexist() throws Exception {
        Instance instance = srSeparationInstance();
        List<RouteColumn> columns = srSeparationColumns(instance);
        RobustCutRow robustCandidate = srSeparationRobustCutRow();
        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());

        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                backend,
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR,
                List.of(robustCandidate)).solve(instance, columns);

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        if (root.activeCutCount() != 2) {
            throw new AssertionError("mixed robust+SR node separation should add exactly both cut types"
                    + " activeCutCount=" + root.activeCutCount());
        }
        boolean sawMixedContext = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && snapshot.hasSubsetRowCuts()
                    && snapshot.robustCutCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()
                    && snapshot.sawNonZeroSubsetRowDual()
                    && snapshot.sawSubsetRowReducedCostShift()) {
                sawMixedContext = true;
            }
        }
        if (!sawMixedContext) {
            throw new AssertionError("mixed robust+SR node separation should reach pricing context");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,," + root.activeCutCount() + ",")) {
            throw new AssertionError("mixed robust+SR trace must expose active cut count");
        }
    }

    private static void assertBranchNodeGeneratedRobustAndSubsetRowSeparationCanCoexist() throws Exception {
        Instance instance = generatedRobustSubsetRowInstance();
        List<RouteColumn> columns = generatedRobustSubsetRowColumns(instance);
        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());

        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                backend,
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR,
                List.of(),
                true,
                false).solve(instance, columns);

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("generated robust+SR root active cuts", 2, root.activeCutCount());
        if ("positive_artificial".equals(root.pruneReason())) {
            throw new AssertionError("generated robust+SR root should finish without positive artificial columns");
        }
        boolean sawMixedContext = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && snapshot.hasSubsetRowCuts()
                    && snapshot.robustCutCount() == 1
                    && snapshot.branchRowCount() == 0
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()
                    && snapshot.sawNonZeroSubsetRowDual()
                    && snapshot.sawSubsetRowReducedCostShift()) {
                sawMixedContext = true;
            }
        }
        if (!sawMixedContext) {
            throw new AssertionError("generated robust+SR separation should reach pricing context"
                    + " with the generated robust row and node-local SR row");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,," + root.activeCutCount() + ",")) {
            throw new AssertionError("generated robust+SR trace must expose active cut count");
        }

        ArrayList<MasterCutRow> inheritedActiveRows = new ArrayList<MasterCutRow>();
        RecordingNodePricingBackend branchBackend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());
        BranchAndPriceSolver branchSolver = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                1,
                branchBackend,
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR,
                List.of(),
                true,
                false);
        NodeRelaxationProbe branchRelaxation = solveNodeRelaxationForTest(
                branchSolver,
                instance,
                BranchNode.root("generated-robust-sr-branch-row")
                        .child(VehicleCountConstraint.lessOrEqual(10)),
                columns,
                inheritedActiveRows);
        assertEquals("generated robust+SR branch-row active cuts exclude branch row",
                2,
                branchRelaxation.activeCutCount());
        if (!inheritedActiveRows.isEmpty()) {
            throw new AssertionError("node-local generated robust+SR separation must not mutate inherited cut rows");
        }
        boolean sawBranchMixedContext = false;
        for (BackendSnapshot snapshot : branchBackend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && snapshot.hasSubsetRowCuts()
                    && snapshot.robustCutCount() == 1
                    && snapshot.branchRowCount() == 1
                    && snapshot.sawNonZeroRobustDual()
                    && snapshot.sawRobustReducedCostShift()
                    && snapshot.sawNonZeroSubsetRowDual()
                    && snapshot.sawSubsetRowReducedCostShift()) {
                sawBranchMixedContext = true;
            }
        }
        if (!sawBranchMixedContext) {
            throw new AssertionError("generated robust+SR branch-row node should price with both cuts"
                    + " and the inherited branch row");
        }

        try {
            new BranchAndPriceSolver(
                    new VehicleCountBrancher(),
                    new SetOutflowBrancher(),
                    1000.0,
                    TOLERANCE,
                    1,
                    new RouteUniverseNodePricingBackend(),
                    NodeCutPropagationPolicy.GLOBAL_AUTOMATIC_SR,
                    List.of(),
                    true,
                    false);
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("cannot be combined")) {
                throw new AssertionError("global SR generated robust rejection should explain the boundary", expected);
            }
            return;
        }
        throw new AssertionError("generated robust separation must reject global SR policy");
    }

    private static void assertActiveCutRowsAreInheritedByChildNodes() throws Exception {
        Instance instance = forcedBranchingInstance();
        List<RouteColumn> columns = forcedBranchingColumns(instance);
        RobustCutRow activeCut = RobustCutRow.ofArcCoefficients(
                "public_active_cut_inheritance_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                10.0,
                Map.of(new RobustCut.Arc(0, 1), 1.0));

        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                8).solve(instance, columns, List.of(activeCut));

        if (result.createdNodes() != 3 || result.processedNodes() != 3) {
            throw new AssertionError("active-cut public branch tree must still process child nodes"
                    + " created=" + result.createdNodes()
                    + " processed=" + result.processedNodes());
        }
        boolean sawChild = false;
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            if (record.activeCutCount() != 1) {
                throw new AssertionError("active cut rows must be inherited by every processed branch node"
                        + " node=" + record.nodeId()
                        + " activeCutCount=" + record.activeCutCount());
            }
            if (record.depth() > 0) {
                sawChild = true;
            }
        }
        if (!sawChild) {
            throw new AssertionError("active-cut inheritance test must observe at least one child node");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,,1,")
                || !trace.contains("sum_lambda <= 1,1,")
                || !trace.contains("sum_lambda >= 2,1,")) {
            throw new AssertionError("active-cut branch trace must expose inherited active cut counts");
        }
    }

    private static void assertActiveCutAndInheritedBranchRowsAffectPublicChildTree() throws Exception {
        Instance instance = comboBranchPricingInstance();
        List<RouteColumn> columns = comboBranchPricingColumns(instance);
        RobustCutRow activeCut = comboBranchPricingCutRow();

        BranchAndPriceSolver.Result noCut = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                8).solve(instance, columns);
        BranchAndPriceSolver.Result active = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                8).solve(instance, columns, List.of(activeCut));

        assertEquals("public combo no-cut status", "optimal_tiny_branch_tree", noCut.status());
        assertEquals("public combo active-cut status", "optimal_tiny_branch_tree", active.status());
        assertEquals("public combo no-cut created nodes", 3, noCut.createdNodes());
        assertEquals("public combo active-cut created nodes", 5, active.createdNodes());
        assertEquals("public combo no-cut generated columns", 10, noCut.totalGeneratedColumns());
        assertEquals("public combo active-cut generated columns", 12, active.totalGeneratedColumns());

        BranchAndPriceSolver.NodeRecord noCutVehicleGe = nodeWithConstraint(noCut, "sum_lambda >= 2");
        BranchAndPriceSolver.NodeRecord activeVehicleGe = nodeWithConstraint(active, "sum_lambda >= 2");
        assertEquals("public combo no-cut child has no active cuts", 0, noCutVehicleGe.activeCutCount());
        assertEquals("public combo active child inherited cut count", 1, activeVehicleGe.activeCutCount());
        if (activeVehicleGe.pricingCalls() < 2 || activeVehicleGe.generatedColumns() <= 0) {
            throw new AssertionError("active vehicle child must run column generation with inherited rows"
                    + " pricingCalls=" + activeVehicleGe.pricingCalls()
                    + " generatedColumns=" + activeVehicleGe.generatedColumns());
        }
        if (noCutVehicleGe.branchType().isPresent()) {
            throw new AssertionError("no-cut vehicle child should close without set-outflow branching");
        }
        assertEquals("active vehicle child reaches set-outflow branch",
                BranchConstraint.Type.SET_OUTFLOW,
                activeVehicleGe.branchType().orElseThrow(() -> new AssertionError("active child should branch")));

        BranchAndPriceSolver.NodeRecord activeGrandchild =
                nodeWithConstraintPrefix(active, "sum_lambda >= 2", "x(delta+(");
        if (!activeGrandchild.constraints().contains("sum_lambda >= 2")) {
            throw new AssertionError("active grandchild must inherit vehicle-count branch row");
        }
        assertEquals("active grandchild inherited cut count", 1, activeGrandchild.activeCutCount());
        if (activeGrandchild.pricingCalls() < 2 || activeGrandchild.generatedColumns() <= 0) {
            throw new AssertionError("active grandchild must price with active cut and inherited branch rows"
                    + " pricingCalls=" + activeGrandchild.pricingCalls()
                    + " generatedColumns=" + activeGrandchild.generatedColumns());
        }
        assertNonNegativeFinalPricing("public combo active tree", active);

        assertEquals("public combo no-cut incumbent routes", List.of("B", "C"), routeNames(noCut.incumbentColumns()));
        assertEquals("public combo active-cut incumbent routes", List.of("A", "C"), routeNames(active.incumbentColumns()));
        String trace = TraceCsv.bcpNodes(instance.name(), active);
        if (!trace.startsWith(TraceCsv.BCP_NODE_HEADER + System.lineSeparator())
                || !trace.contains("x(delta+(")) {
            throw new AssertionError("public combo trace must expose inherited active cut counts in child rows");
        }
    }

    private static void assertActiveSubsetRowCutRowsAffectNodePricing() throws Exception {
        Instance instance = subsetRowNodePricingInstance();
        List<RouteColumn> columns = subsetRowNodePricingColumns(instance);
        BranchNode node = BranchNode.root("sr-pricing-node")
                .child(SetOutflowConstraint.greaterOrEqual(ints(1), 0))
                .child(SetOutflowConstraint.greaterOrEqual(ints(3), 0));
        SubsetRowCutRow activeCut = SubsetRowCutRow.ofL2Triple("sr_node_pricing_probe", 1, 2, 3);

        NodeRelaxationProbe relaxation = solveNodeRelaxationForTest(
                instance,
                node,
                columns,
                List.of(activeCut));

        assertClose("active SR node lower bound", 1010.0, relaxation.lowerBound());
        if (!relaxation.hasPositiveArtificial()) {
            throw new AssertionError("active SR node should expose the row-forced artificial usage");
        }
        assertEquals("active SR node generated columns", 0, relaxation.generatedColumns());
        if (relaxation.bestReducedCost() < -TOLERANCE) {
            throw new AssertionError("SR-adjusted node pricing should not leave a missing negative column"
                    + " bestReducedCost=" + relaxation.bestReducedCost());
        }
    }

    private static void assertBranchNodeSubsetRowSeparationAddsViolatedCut() throws Exception {
        Instance instance = srSeparationInstance();
        List<RouteColumn> columns = srSeparationColumns(instance);
        RecordingSubsetRowPricingSolver solver = new RecordingSubsetRowPricingSolver(columns);

        BranchAndPriceSolver.Result result = BranchAndPriceSolver
                .withRootLabelingAndSubsetRowSeparation(solver, 1)
                .solve(instance, columns);

        assertEquals("branch-node SR separation status", "optimal_tiny_branch_tree", result.status());
        assertEquals("branch-node SR separation processed nodes", 1, result.processedNodes());
        if (solver.calls() < 2) {
            throw new AssertionError("branch-node SR separation should price with the separated row across CG iterations");
        }
        if (!solver.sawSubsetRowContext()) {
            throw new AssertionError("branch-node SR separation must pass the separated SR row into PricingContext");
        }
        if (!solver.sawNonZeroSubsetRowDual()) {
            throw new AssertionError("branch-node SR separation should expose a nonzero SR pricing dual");
        }
        if (!solver.sawSubsetRowCutCount(0)) {
            throw new AssertionError("branch-node SR separation should start from a no-SR pricing context");
        }
        assertEquals("branch-node max pricing SR cuts", 1, solver.maxSubsetRowCutCount());
        if (!solver.sawExpectedSubsetRowCut()) {
            throw new AssertionError("branch-node SR separation should add the canonical {1,2,3}, l=2 cut");
        }
        if (!solver.sawSubsetRowReducedCostShift()) {
            throw new AssertionError("branch-node SR separation should change route direct reduced costs");
        }

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("branch-node SR separation active cuts", 1, root.activeCutCount());
        if (root.generatedColumns() <= 0) {
            throw new AssertionError("branch-node SR separation should still add priced columns");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,,1,")) {
            throw new AssertionError("branch-node SR separation trace must expose activeCutCount=1");
        }
    }

    private static void assertBranchNodeSubsetRowSeparationExcludesInheritedBranchRows() throws Exception {
        Instance instance = srSeparationInstance();
        List<RouteColumn> columns = srSeparationColumns(instance);
        RecordingSubsetRowPricingSolver pricingSolver = new RecordingSubsetRowPricingSolver(columns);
        BranchAndPriceSolver solver = BranchAndPriceSolver
                .withRootLabelingAndSubsetRowSeparation(pricingSolver, 1);
        BranchNode node = BranchNode.root("sr-node-separation-branch-row")
                .child(VehicleCountConstraint.lessOrEqual(10));

        NodeRelaxationProbe relaxation = solveNodeRelaxationForTest(
                solver,
                instance,
                node,
                columns,
                List.of());

        if (!pricingSolver.sawSubsetRowContext()) {
            throw new AssertionError("branch-row node SR separation must still pass SR cuts into PricingContext");
        }
        if (!pricingSolver.sawSubsetRowCutCount(0)) {
            throw new AssertionError("branch-row node SR separation should start from a no-SR pricing context");
        }
        assertEquals("branch-row node max pricing SR cuts", 1, pricingSolver.maxSubsetRowCutCount());
        if (!pricingSolver.sawExpectedSubsetRowCut()) {
            throw new AssertionError("branch-row node SR separation should add the canonical {1,2,3}, l=2 cut");
        }
        assertEquals("branch-row node separated active cuts", 1, relaxation.activeCutCount());
        if (relaxation.pricingCalls() <= 0) {
            throw new AssertionError("branch-row node SR separation should call pricing");
        }
    }

    private static void assertBranchNodeSubsetRowSeparationSkipsNoViolation() throws Exception {
        Instance instance = srSeparationInstance();
        List<RouteColumn> columns = List.of(RouteColumn.fromRoute(
                "sr_full_only",
                Route.of(0, 1, 2, 3, 4, 5, 6, 7),
                instance));
        RecordingSubsetRowPricingSolver solver = new RecordingSubsetRowPricingSolver(columns);

        BranchAndPriceSolver.Result result = BranchAndPriceSolver
                .withRootLabelingAndSubsetRowSeparation(solver, 1)
                .solve(instance, columns);

        assertEquals("branch-node no-violation SR separation status",
                "optimal_tiny_branch_tree",
                result.status());
        assertEquals("branch-node no-violation processed nodes", 1, result.processedNodes());
        if (solver.calls() <= 0) {
            throw new AssertionError("branch-node no-violation path should call pricing");
        }
        assertEquals("branch-node no-violation first pricing SR cuts", 0, solver.firstSubsetRowCutCount());
        assertEquals("branch-node no-violation max pricing SR cuts", 0, solver.maxSubsetRowCutCount());
        if (solver.sawSubsetRowContext()) {
            throw new AssertionError("branch-node no-violation path must not fabricate SR cut context");
        }
        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("branch-node no-violation active cuts", 0, root.activeCutCount());
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        if (!trace.contains("bcp-node," + instance.name() + ",root,0,,0,")) {
            throw new AssertionError("branch-node no-violation trace must keep activeCutCount=0");
        }
    }

    private static void assertBranchTreeSubsetRowSeparationCutsQueuedChildNode() throws Exception {
        Instance instance = queuedChildSrSeparationInstance();
        List<RouteColumn> columns = queuedChildSrSeparationColumns(instance);
        RecordingSubsetRowPricingSolver solver = new RecordingSubsetRowPricingSolver(columns);

        BranchAndPriceSolver.Result result = BranchAndPriceSolver
                .withRootLabelingAndSubsetRowSeparation(solver, 10)
                .solve(instance, columns);

        assertEquals("queued child SR separation status", "optimal_tiny_branch_tree", result.status());
        if (result.createdNodes() < 3 || result.processedNodes() < 3) {
            throw new AssertionError("queued child SR separation should process a branched child tree"
                    + " created=" + result.createdNodes()
                    + " processed=" + result.processedNodes());
        }

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("queued child SR root active cuts", 0, root.activeCutCount());
        assertEquals("queued child SR root prune reason", "branched_vehicle_count", root.pruneReason());
        assertEquals("queued child SR root branch type",
                BranchConstraint.Type.VEHICLE_COUNT,
                root.branchType().orElseThrow(() -> new AssertionError("root should branch on vehicle count")));

        BranchAndPriceSolver.NodeRecord geChild = nodeWithConstraint(result, "sum_lambda >= 3");
        assertEquals("queued child SR ge depth", 1, geChild.depth());
        assertEquals("queued child SR ge branch-row count", 1, geChild.constraints().size());
        if (geChild.activeCutCount() <= 0) {
            throw new AssertionError("queued child >= branch should separate node-local SR cuts");
        }
        if (geChild.pruneReason().equals("positive_artificial")) {
            throw new AssertionError("queued child >= branch should stay LP-feasible after SR cuts");
        }
        if (geChild.pricingCalls() <= 0) {
            throw new AssertionError("queued child >= branch should enter cut-aware pricing");
        }
        if (geChild.bestReducedCost() < -TOLERANCE) {
            throw new AssertionError("queued child >= branch should close pricing before pruning"
                    + " bestReducedCost=" + geChild.bestReducedCost());
        }
        assertEquals("queued child SR ge branch type",
                BranchConstraint.Type.SET_OUTFLOW,
                geChild.branchType().orElseThrow(() -> new AssertionError("ge child should branch")));

        BranchAndPriceSolver.NodeRecord leChild = nodeWithConstraint(result, "sum_lambda <= 2");
        assertEquals("queued child SR le branch-row count", 1, leChild.constraints().size());
        assertEquals("queued child SR le active cuts", 0, leChild.activeCutCount());
        assertEquals("queued child SR le prune reason", "incumbent", leChild.pruneReason());

        BranchAndPriceSolver.NodeRecord geGrandchild =
                nodeWithConstraintPrefix(result, "sum_lambda >= 3", "x(delta+(");
        assertEquals("queued child SR descendant depth", 2, geGrandchild.depth());
        if (!geGrandchild.constraints().contains("sum_lambda >= 3")) {
            throw new AssertionError("queued child SR descendant should inherit vehicle-count branch row");
        }
        if (geGrandchild.pricingCalls() <= 0) {
            throw new AssertionError("queued child SR descendant should enter node pricing");
        }

        if (!solver.sawSubsetRowPricingCall(geChild.activeCutCount())) {
            throw new AssertionError("queued child pricing should receive active SR cuts and adjusted route RCs");
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        List<String> geTrace = traceRowWithConstraint(trace, "sum_lambda >= 3");
        assertEquals("queued child trace depth", "1", geTrace.get(3));
        assertEquals("queued child trace active cuts",
                Integer.toString(geChild.activeCutCount()),
                geTrace.get(5));
        List<String> descendantTrace = traceRowWithConstraintPrefix(trace, "sum_lambda >= 3", "x(delta+(");
        assertEquals("queued child descendant trace depth", "2", descendantTrace.get(3));
        assertEquals("queued child descendant trace active cuts",
                Integer.toString(geGrandchild.activeCutCount()),
                descendantTrace.get(5));
    }

    private static void assertSeparatedSubsetRowsAreNotInheritedByDescendantNodes() throws Exception {
        Instance instance = srSeparationInstance();
        List<RouteColumn> columns = srSeparationColumns(instance);
        RecordingSubsetRowPricingSolver solver = new RecordingSubsetRowPricingSolver(columns);
        BranchAndPriceSolver branchAndPrice = BranchAndPriceSolver
                .withRootLabelingAndSubsetRowSeparation(solver, 1);
        ArrayList<MasterCutRow> inheritedActiveRows = new ArrayList<MasterCutRow>();

        NodeRelaxationProbe parent = solveNodeRelaxationForTest(
                branchAndPrice,
                instance,
                BranchNode.root(),
                columns,
                inheritedActiveRows);

        assertEquals("parent separated SR active count", 1, parent.activeCutCount());
        if (!solver.sawSubsetRowPricingCall(1)) {
            throw new AssertionError("parent should price with its locally separated SR row");
        }
        if (!inheritedActiveRows.isEmpty()) {
            throw new AssertionError("node-local SR separation must not mutate inherited active cut rows");
        }
        int callsAfterParent = solver.subsetRowPricingCallCount();

        BranchNode child = BranchNode.root().child(VehicleCountConstraint.lessOrEqual(1));
        NodeRelaxationProbe childRelaxation = solveNodeRelaxationForTest(
                branchAndPrice,
                instance,
                child,
                columns,
                inheritedActiveRows);

        assertEquals("descendant branch row count", 1, child.constraints().size());
        assertEquals("descendant must not inherit parent separated SR row", 0, childRelaxation.activeCutCount());
        if (!solver.sawSubsetRowCutCountFrom(callsAfterParent, 0)) {
            throw new AssertionError("descendant pricing should run with zero inherited SR rows");
        }
        if (solver.sawSubsetRowContextFrom(callsAfterParent)) {
            throw new AssertionError("parent node-local SR row leaked into descendant pricing context");
        }
        if (!inheritedActiveRows.isEmpty()) {
            throw new AssertionError("child solve must not mutate inherited active cut rows");
        }
    }

    private static void assertGlobalSubsetRowsAreInheritedByDescendantNodes() throws Exception {
        Instance instance = srSeparationInstance();
        List<RouteColumn> columns = srSeparationColumns(instance);
        RecordingSubsetRowPricingSolver solver = new RecordingSubsetRowPricingSolver(columns);
        BranchAndPriceSolver branchAndPrice = BranchAndPriceSolver
                .withRootLabelingAndGlobalSubsetRowSeparation(solver, 1);
        ArrayList<MasterCutRow> globalCutRows = new ArrayList<MasterCutRow>();

        NodeRelaxationProbe parent = solveNodeRelaxationForTest(
                branchAndPrice,
                instance,
                BranchNode.root(),
                columns,
                globalCutRows);

        assertEquals("global parent separated SR active count", 1, parent.activeCutCount());
        assertEquals("global cut pool receives separated SR row", 1, globalCutRows.size());
        assertEquals("global cut pool canonical SR row",
                "sr-U1-2-3-l2",
                globalCutRows.get(0).name());
        if (!(globalCutRows.get(0) instanceof SubsetRowCutRow)) {
            throw new AssertionError("global cut pool must store only SR row templates");
        }
        if (!solver.sawSubsetRowPricingCall(1)) {
            throw new AssertionError("global parent should price with its separated SR row");
        }
        int callsAfterParent = solver.subsetRowPricingCallCount();

        BranchNode child = BranchNode.root().child(VehicleCountConstraint.lessOrEqual(1));
        NodeRelaxationProbe childRelaxation = solveNodeRelaxationForTest(
                branchAndPrice,
                instance,
                child,
                columns,
                globalCutRows);

        assertEquals("global descendant branch row count", 1, child.constraints().size());
        assertEquals("global descendant inherits separated SR row", 1, childRelaxation.activeCutCount());
        assertEquals("global cut pool does not duplicate inherited SR row", 1, globalCutRows.size());
        if (!solver.sawSubsetRowCutCountFrom(callsAfterParent, 1)) {
            throw new AssertionError("global descendant pricing should see the inherited SR row");
        }
        if (!solver.sawSubsetRowContextFrom(callsAfterParent)) {
            throw new AssertionError("global descendant pricing context should include the inherited SR row");
        }

        RecordingSubsetRowPricingSolver duplicateSolver = new RecordingSubsetRowPricingSolver(columns);
        BranchAndPriceSolver duplicateBranchAndPrice = BranchAndPriceSolver
                .withRootLabelingAndGlobalSubsetRowSeparation(duplicateSolver, 1);
        ArrayList<MasterCutRow> customNamedGlobalRows = new ArrayList<MasterCutRow>();
        customNamedGlobalRows.add(SubsetRowCutRow.ofL2Triple("custom-global-sr", 1, 2, 3));
        NodeRelaxationProbe duplicateParent = solveNodeRelaxationForTest(
                duplicateBranchAndPrice,
                instance,
                BranchNode.root(),
                columns,
                customNamedGlobalRows);

        assertEquals("global semantic duplicate active count", 1, duplicateParent.activeCutCount());
        assertEquals("global semantic duplicate cut pool size", 1, customNamedGlobalRows.size());
        if (!duplicateSolver.sawSubsetRowContext()) {
            throw new AssertionError("custom-name global SR row should still reach pricing context");
        }
    }

    private static void assertGlobalSubsetRowsPropagateThroughQueuedBranchTree() throws Exception {
        Instance instance = queuedChildSrSeparationInstance();
        List<RouteColumn> columns = queuedChildSrSeparationColumns(instance);
        RecordingSubsetRowPricingSolver solver = new RecordingSubsetRowPricingSolver(columns);

        BranchAndPriceSolver.Result result = BranchAndPriceSolver
                .withRootLabelingAndGlobalSubsetRowSeparation(solver, 10)
                .solve(instance, columns);

        assertEquals("global queued-child status", "optimal_tiny_branch_tree", result.status());
        if (result.processedNodes() < 3 || result.createdNodes() < 3) {
            throw new AssertionError("global queued-child branch tree should process at least one branched child"
                    + " processed=" + result.processedNodes()
                    + " created=" + result.createdNodes());
        }

        BranchAndPriceSolver.NodeRecord root = result.nodeRecords().get(0);
        assertEquals("global queued-child root active cuts", 0, root.activeCutCount());
        BranchAndPriceSolver.NodeRecord geChild = nodeWithConstraint(result, "sum_lambda >= 3");
        if (geChild.activeCutCount() <= 0) {
            throw new AssertionError("global queued-child >= branch should separate and publish an SR row");
        }
        assertEquals("global queued-child ge branch type",
                BranchConstraint.Type.SET_OUTFLOW,
                geChild.branchType().orElseThrow(() -> new AssertionError("ge child should branch")));
        if (!solver.sawSubsetRowContext()) {
            throw new AssertionError("global queued-child pricing should see SR context");
        }

        BranchAndPriceSolver.NodeRecord leChild = nodeWithConstraint(result, "sum_lambda <= 2");
        int geIndex = result.nodeRecords().indexOf(geChild);
        int leIndex = result.nodeRecords().indexOf(leChild);
        if (leIndex > geIndex && leChild.activeCutCount() <= 0) {
            throw new AssertionError("global queued-child <= branch should inherit SR rows when processed after ge");
        }

        BranchAndPriceSolver.NodeRecord geGrandchild =
                nodeWithConstraintPrefix(result, "sum_lambda >= 3", "x(delta+(");
        if (!geGrandchild.constraints().contains("sum_lambda >= 3")) {
            throw new AssertionError("global queued-child descendant should inherit vehicle-count branch row");
        }
        if (geGrandchild.activeCutCount() <= 0) {
            throw new AssertionError("global queued-child descendant should inherit the separated SR row");
        }
        if (solver.maxSubsetRowCutCount() < 1) {
            throw new AssertionError("global queued-child pricing should expose at least one SR cut");
        }
        solver.assertPricingCallCutCountsMatchNodeRecords(
                result.nodeRecords(),
                "global queued-child");
        List<String> publishedKeys = solver.lastSubsetRowKeysForNode(
                result.nodeRecords(),
                geIndex);
        if (publishedKeys.isEmpty()) {
            throw new AssertionError("global queued-child publishing node should price with an SR key");
        }
        List<String> descendantKeys = solver.lastSubsetRowKeysForNode(
                result.nodeRecords(),
                result.nodeRecords().indexOf(geGrandchild));
        if (!descendantKeys.containsAll(publishedKeys)) {
            throw new AssertionError("global queued-child descendant should inherit published SR keys"
                    + " published=" + publishedKeys
                    + " descendant=" + descendantKeys);
        }
        if (leIndex > geIndex) {
            List<String> leKeys = solver.lastSubsetRowKeysForNode(result.nodeRecords(), leIndex);
            if (!leKeys.containsAll(publishedKeys)) {
                throw new AssertionError("global queued-child later sibling should inherit published SR keys"
                        + " published=" + publishedKeys
                        + " sibling=" + leKeys);
            }
        }
        String trace = TraceCsv.bcpNodes(instance.name(), result);
        List<String> geTrace = traceRowWithConstraint(trace, "sum_lambda >= 3");
        if (!Integer.toString(geChild.activeCutCount()).equals(geTrace.get(5))) {
            throw new AssertionError("global queued-child trace must report ge-child active cuts");
        }
        List<String> leTrace = traceRowWithConstraint(trace, "sum_lambda <= 2");
        if (!Integer.toString(leChild.activeCutCount()).equals(leTrace.get(5))) {
            throw new AssertionError("global queued-child trace must report le-child active cuts");
        }
        List<String> descendantTrace = traceRowWithConstraintPrefix(trace, "sum_lambda >= 3", "x(delta+(");
        if (!Integer.toString(geGrandchild.activeCutCount()).equals(descendantTrace.get(5))) {
            throw new AssertionError("global queued-child trace must report descendant active cuts");
        }
    }

    private static void assertNodePricingBackendReceivesCutAndBranchRows() throws Exception {
        Instance instance = comboBranchPricingInstance();
        List<RouteColumn> columns = comboBranchPricingColumns(instance);
        RecordingNodePricingBackend backend =
                new RecordingNodePricingBackend(new RouteUniverseNodePricingBackend());

        BranchAndPriceSolver.Result result = new BranchAndPriceSolver(
                new VehicleCountBrancher(),
                new SetOutflowBrancher(),
                1000.0,
                TOLERANCE,
                8,
                backend).solve(instance, columns, List.of(comboBranchPricingCutRow()));

        assertEquals("backend contract result status", "optimal_tiny_branch_tree", result.status());
        assertEquals("backend snapshots cover pricing calls", result.totalPricingCalls(), backend.snapshots().size());
        assertEquals("backend contract name",
                RouteUniverseNodePricingBackend.BACKEND_NAME,
                backend.name());
        boolean sawCutAwareRootPricing = false;
        boolean sawCutAwareChildPricing = false;
        boolean sawCutAwareGrandchildPricing = false;
        for (BackendSnapshot snapshot : backend.snapshots()) {
            if (snapshot.hasRobustCuts()
                    && !snapshot.hasSubsetRowCuts()
                    && snapshot.branchRowCount() == 0
                    && snapshot.routeUniverseSize() == columns.size()) {
                sawCutAwareRootPricing = true;
            }
            if (snapshot.hasRobustCuts()
                    && snapshot.branchRowCount() == 1
                    && snapshot.currentColumnCount() > 0) {
                sawCutAwareChildPricing = true;
            }
            if (snapshot.hasRobustCuts()
                    && snapshot.branchRowCount() >= 2
                    && snapshot.currentColumnCount() > 0) {
                sawCutAwareGrandchildPricing = true;
            }
        }
        if (!sawCutAwareRootPricing) {
            throw new AssertionError("node pricing backend should receive active robust cut context at root");
        }
        if (!sawCutAwareChildPricing) {
            throw new AssertionError("node pricing backend should receive active cuts plus child branch row");
        }
        if (!sawCutAwareGrandchildPricing) {
            throw new AssertionError("node pricing backend should receive active cuts plus inherited branch rows");
        }
    }

    private static void assertNodePricingBackendRejectsNegativeResultWithoutColumns() throws Exception {
        Instance instance = forcedBranchingInstance();
        List<RouteColumn> columns = forcedBranchingColumns(instance);
        try {
            new BranchAndPriceSolver(
                    new VehicleCountBrancher(),
                    new SetOutflowBrancher(),
                    1000.0,
                    TOLERANCE,
                    1,
                    new NegativeNoColumnBackend()).solve(instance, columns);
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("returned no columns")) {
                throw new AssertionError("negative backend failure should explain missing columns", expected);
            }
            return;
        }
        throw new AssertionError("negative node pricing backend result without columns must fail");
    }

    private static void assertNodePricingResultRejectsImpossibleColumnCount() {
        Instance instance = singleRequestPricingInstance();
        RouteColumn candidate = RouteColumn.fromRoute("impossible_count", Route.of(0, 1, 2, 3), instance);
        try {
            BranchNodePricingResult.of(0.0, 0, List.of(candidate));
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("pricedColumns must cover returned columns")) {
                throw new AssertionError("bad pricedColumns message should be explicit", expected);
            }
            return;
        }
        throw new AssertionError("pricing result must reject returned columns exceeding pricedColumns");
    }

    private static void assertLabelingNodePricingBackendPricesRootMissingColumns() {
        Instance instance = singleRequestPricingInstanceWithDuals(10.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);

        BranchNodePricingResult result = new LabelingNodePricingBackend(
                PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE)).price(new BranchNodePricingRequest(
                        instance,
                        context,
                        List.of(),
                        List.of(candidate),
                        List.of(),
                        TOLERANCE));

        assertEquals("labeling backend name",
                LabelingNodePricingBackend.BACKEND_NAME,
                new LabelingNodePricingBackend(PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE)).name());
        assertClose("labeling root best missing RC", -5.0, result.bestReducedCost());
        assertEquals("labeling root generated missing columns", 1, result.columns().size());
        assertEquals("labeling root priced columns", 1, result.pricedColumns());
        assertEquals("labeling root route", List.of(0, 1, 2, 3), result.columns().get(0).vertexIds());
    }

    private static void assertLabelingNodePricingBackendFiltersCurrentColumns() {
        Instance instance = singleRequestPricingInstanceWithDuals(10.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        RouteColumn alreadyLoaded = RouteColumn.fromRoute("already_loaded", Route.of(0, 1, 2, 3), instance);

        BranchNodePricingResult result = new LabelingNodePricingBackend(
                PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE)).price(new BranchNodePricingRequest(
                        instance,
                        context,
                        List.of(),
                        List.of(candidate),
                        List.of(alreadyLoaded),
                        TOLERANCE));

        assertClose("labeling duplicate-only best missing RC", 0.0, result.bestReducedCost());
        assertEquals("labeling duplicate-only missing columns", 0, result.columns().size());
        assertEquals("labeling duplicate-only priced columns", 1, result.pricedColumns());
    }

    private static void assertLabelingNodePricingBackendFiltersMixedCurrentColumns() {
        Instance instance = twoRequestPricingInstance();
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn alreadyLoaded = RouteColumn.fromRoute("already_loaded", Route.of(0, 1, 3, 2, 4, 5), instance);
        RouteColumn missing = RouteColumn.fromRoute("missing_negative", Route.of(0, 2, 4, 5), instance);
        FixedPricingSolver solver = new FixedPricingSolver(
                PricingResult.exact(List.of(alreadyLoaded, missing), -10.0));

        BranchNodePricingResult result = new LabelingNodePricingBackend(solver)
                .price(new BranchNodePricingRequest(
                        instance,
                        context,
                        List.of(),
                        List.of(alreadyLoaded, missing),
                        List.of(alreadyLoaded),
                        TOLERANCE));

        assertEquals("labeling mixed filter solver calls", 1, solver.calls());
        assertClose("labeling mixed filter best missing RC", -3.0, result.bestReducedCost());
        assertEquals("labeling mixed filter missing column count", 1, result.columns().size());
        assertEquals("labeling mixed filter missing route", missing.vertexIds(), result.columns().get(0).vertexIds());
        assertEquals("labeling mixed filter priced columns", 2, result.pricedColumns());
    }

    private static void assertLabelingNodePricingBackendSupportsVehicleCountBranchRows() throws Exception {
        Instance instance = singleRequestPricingInstanceWithDuals(0.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        BranchMasterRow inheritedBranchRow = BranchMasterRow.of(VehicleCountConstraint.greaterOrEqual(1));
        BranchNodePricingRequest request = new BranchNodePricingRequest(
                instance,
                context,
                List.of(cutDualForTest(inheritedBranchRow, 10.0, -10.0)),
                List.of(candidate),
                List.of(),
                TOLERANCE);

        BranchNodePricingResult result = new LabelingNodePricingBackend(
                PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE)).price(request);
        BranchNodePricingResult universe = new RouteUniverseNodePricingBackend().price(request);

        assertClose("labeling vehicle-count branch row best RC", -5.0, result.bestReducedCost());
        assertClose("labeling vehicle-count branch row matches route universe",
                universe.bestReducedCost(),
                result.bestReducedCost());
        assertEquals("labeling vehicle-count branch row generated columns", 1, result.columns().size());
        assertEquals("labeling vehicle-count branch row route", candidate.vertexIds(), result.columns().get(0).vertexIds());
    }

    private static void assertLabelingNodePricingBackendAdjustsFleetDualForVehicleCountRows() throws Exception {
        Instance instance = singleRequestPricingInstanceWithDuals(0.0, 4.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        BranchMasterRow vehicleLe = BranchMasterRow.of(VehicleCountConstraint.lessOrEqual(1));
        BranchMasterRow vehicleGe = BranchMasterRow.of(VehicleCountConstraint.greaterOrEqual(1));
        RecordingContextPricingSolver solver = new RecordingContextPricingSolver();

        new LabelingNodePricingBackend(solver).price(new BranchNodePricingRequest(
                instance,
                context,
                List.of(
                        cutDualForTest(vehicleLe, -2.0, 2.0),
                        cutDualForTest(vehicleGe, 1.5, -1.5)),
                List.of(candidate),
                List.of(),
                TOLERANCE));

        assertEquals("labeling adjusted fleet dual solver calls", 1, solver.calls());
        assertClose("labeling adjusted fleet dual",
                3.5,
                solver.observedFleetDual());
    }

    private static void assertLabelingNodePricingBackendSupportsSetOutflowBranchRows() throws Exception {
        Instance instance = singleRequestPricingInstanceWithDuals(0.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        BranchMasterRow inheritedBranchRow = BranchMasterRow.of(SetOutflowConstraint.greaterOrEqual(ints(1), 1));
        BranchNodePricingRequest request = new BranchNodePricingRequest(
                instance,
                context,
                List.of(cutDualForTest(inheritedBranchRow, 10.0, -10.0)),
                List.of(candidate),
                List.of(),
                TOLERANCE);

        BranchNodePricingResult result = new LabelingNodePricingBackend(
                PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE)).price(request);
        BranchNodePricingResult universe = new RouteUniverseNodePricingBackend().price(request);

        assertClose("labeling set-outflow branch row best RC", -5.0, result.bestReducedCost());
        assertClose("labeling set-outflow branch row matches route universe",
                universe.bestReducedCost(),
                result.bestReducedCost());
        assertEquals("labeling set-outflow branch row generated columns", 1, result.columns().size());
        assertEquals("labeling set-outflow branch row route", candidate.vertexIds(), result.columns().get(0).vertexIds());
    }

    private static void assertLabelingNodePricingBackendCombinesActiveCutsAndBranchRows() throws Exception {
        Instance instance = srSeparationInstance();
        RouteColumn candidate = RouteColumn.fromRoute(
                "labeling_cut_branch_component_candidate",
                Route.of(0, 1, 2, 3, 4, 5, 6, 7),
                instance);
        RobustCutRow robustRow = RobustCutRow.ofArcCoefficients(
                "labeling_component_robust",
                MasterCutRow.Sense.LESS_EQUAL,
                0.0,
                Map.of(new RobustCut.Arc(1, 2), 1.0));
        double robustRawPi = -4.0;
        RobustCut robustCut = robustRow.toPricingCut(robustRawPi);
        SubsetRowCutRow subsetRow = SubsetRowCutRow.ofL2Triple("labeling_component_sr", 1, 2, 3);
        double subsetRowRawPi = -3.0;
        SubsetRowCut subsetCut = subsetRow.toPricingCut(subsetRowRawPi);
        BranchMasterRow vehicleRow = BranchMasterRow.of(VehicleCountConstraint.greaterOrEqual(1));
        double vehiclePricingDual = -20.0;
        BranchMasterRow setOutflowRow = BranchMasterRow.of(SetOutflowConstraint.greaterOrEqual(ints(1, 2, 3), 1));
        double setOutflowPricingDual = -5.0;
        PricingContext context = PricingContext.withCuts(
                ReducedCostMatrices.fromInstanceDuals(instance),
                List.of(robustCut),
                List.of(subsetCut));
        List<GurobiRmp.CutDual> cutDuals = List.of(
                cutDualForTest(robustRow, robustRawPi, robustRow.pricingDualValue(robustRawPi)),
                cutDualForTest(subsetRow, subsetRowRawPi, subsetRow.pricingDualFromRawPi(subsetRowRawPi)),
                cutDualForTest(vehicleRow, 20.0, vehiclePricingDual),
                cutDualForTest(setOutflowRow, 5.0, setOutflowPricingDual));

        double baseDirect = ReducedCostMatrices.fromInstanceDuals(instance).directReducedCost(candidate.vertexIds());
        double robustShift = robustArcPriceSum(instance, robustCut, candidate.vertexIds());
        double subsetShift = SRPricingAdjuster.routePricingAdjustment(subsetCut, instance, candidate.vertexIds());
        double vehicleShift = vehicleRow.routePrice(instance, candidate, vehiclePricingDual);
        double setOutflowShift = setOutflowRow.routePrice(instance, candidate, setOutflowPricingDual);
        assertClose("robust raw to pricing dual", 4.0, robustCut.dualValue());
        assertClose("SR raw to sigma", -3.0, subsetCut.sigma());
        assertNonZero("mixed branch/cut robust shift", robustShift);
        assertNonZero("mixed branch/cut SR shift", subsetShift);
        assertNonZero("mixed branch/cut vehicle shift", vehicleShift);
        assertNonZero("mixed branch/cut set-outflow shift", setOutflowShift);
        double expectedWithoutBranchRows = baseDirect + robustShift + subsetShift;
        double expectedWithBranchRows = expectedWithoutBranchRows + vehicleShift + setOutflowShift;
        assertClose("active cut context direct RC before branch rows",
                expectedWithoutBranchRows,
                context.directReducedCost(candidate.vertexIds()));

        BranchNodePricingRequest request = new BranchNodePricingRequest(
                instance,
                context,
                cutDuals,
                List.of(candidate),
                List.of(),
                TOLERANCE);
        ContextRoutePricingSolver solver = new ContextRoutePricingSolver(candidate);
        BranchNodePricingResult labeling = new LabelingNodePricingBackend(solver).price(request);
        BranchNodePricingResult universe = new RouteUniverseNodePricingBackend().price(request);

        assertEquals("labeling mixed branch/cut solver calls", 1, solver.calls());
        assertClose("labeling mixed branch/cut component RC", expectedWithBranchRows, labeling.bestReducedCost());
        assertClose("route-universe mixed branch/cut component RC", expectedWithBranchRows, universe.bestReducedCost());
        assertClose("labeling mixed branch/cut matches route universe",
                universe.bestReducedCost(),
                labeling.bestReducedCost());
        assertEquals("labeling mixed branch/cut generated columns", 1, labeling.columns().size());
        assertEquals("labeling mixed branch/cut route", candidate.vertexIds(), labeling.columns().get(0).vertexIds());
        assertEquals("labeling mixed branch/cut priced columns", 1, labeling.pricedColumns());
        assertEquals("route-universe mixed branch/cut priced columns", 1, universe.pricedColumns());
    }

    private static void assertLabelingNodePricingBackendRejectsContextBlindSetOutflowSolver() throws Exception {
        Instance instance = singleRequestPricingInstanceWithDuals(0.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        BranchMasterRow inheritedBranchRow = BranchMasterRow.of(SetOutflowConstraint.greaterOrEqual(ints(1), 1));
        FixedPricingSolver solver = new FixedPricingSolver(PricingResult.exact(List.of(candidate), -5.0));

        try {
            new LabelingNodePricingBackend(solver).price(new BranchNodePricingRequest(
                    instance,
                    context,
                    List.of(cutDualForTest(inheritedBranchRow, 2.0, -10.0)),
                    List.of(candidate),
                    List.of(),
                    TOLERANCE));
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("branch pricing rules")) {
                throw new AssertionError("context-blind set-outflow solver should fail fast", expected);
            }
            assertEquals("context-blind set-outflow solver matrix calls", 0, solver.calls());
            return;
        }
        throw new AssertionError("set-outflow branch rows require a context-aware labeling solver");
    }

    private static void assertLabelingNodePricingBackendRejectsEmptyNegativeResult() {
        Instance instance = singleRequestPricingInstanceWithDuals(10.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        try {
            new LabelingNodePricingBackend(new FixedPricingSolver(
                    PricingResult.of(List.of(), -1.0, true, PricingResult.OPTIMAL)))
                    .price(new BranchNodePricingRequest(
                            instance,
                            context,
                            List.of(),
                            List.of(candidate),
                            List.of(),
                            TOLERANCE));
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("returned no columns")) {
                throw new AssertionError("empty negative pricing result should explain missing columns", expected);
            }
            return;
        }
        throw new AssertionError("labeling backend must reject negative best RC without returned columns");
    }

    private static void assertLabelingNodePricingBackendRejectsNonExactResult() {
        Instance instance = singleRequestPricingInstanceWithDuals(10.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        try {
            new LabelingNodePricingBackend(new FixedPricingSolver(
                    PricingResult.of(List.of(), 0.0, false, "heuristic_probe")))
                    .price(new BranchNodePricingRequest(
                            instance,
                            context,
                            List.of(),
                            List.of(candidate),
                            List.of(),
                            TOLERANCE));
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("requires exact pricing result")) {
                throw new AssertionError("non-exact pricing result should be rejected explicitly", expected);
            }
            return;
        }
        throw new AssertionError("labeling backend must reject non-exact pricing results");
    }

    private static void assertHybridNodePricingBackendUsesLabelingAtRoot() {
        Instance instance = singleRequestPricingInstanceWithDuals(10.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        FixedPricingSolver solver = new FixedPricingSolver(PricingResult.exact(List.of(candidate), -5.0));
        BranchNodePricingBackend backend = new HybridNodePricingBackend(
                new LabelingNodePricingBackend(solver),
                new RouteUniverseNodePricingBackend());

        BranchNodePricingResult result = backend.price(new BranchNodePricingRequest(
                instance,
                context,
                List.of(),
                List.of(),
                List.of(),
                TOLERANCE));

        assertEquals("hybrid backend name",
                HybridNodePricingBackend.BACKEND_NAME,
                backend.name());
        assertEquals("hybrid root labeling solver calls", 1, solver.calls());
        assertClose("hybrid root best RC", -5.0, result.bestReducedCost());
        assertEquals("hybrid root generated columns", 1, result.columns().size());
        assertEquals("hybrid root priced columns", 1, result.pricedColumns());
    }

    private static void assertHybridNodePricingBackendUsesLabelingForVehicleCountRows() throws Exception {
        Instance instance = singleRequestPricingInstanceWithDuals(0.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        FixedPricingSolver solver = new FixedPricingSolver(PricingResult.exact(List.of(candidate), -5.0));
        BranchMasterRow inheritedBranchRow = BranchMasterRow.of(VehicleCountConstraint.greaterOrEqual(1));
        BranchNodePricingBackend backend = new HybridNodePricingBackend(
                new LabelingNodePricingBackend(solver),
                new RouteUniverseNodePricingBackend());

        BranchNodePricingResult result = backend.price(new BranchNodePricingRequest(
                instance,
                context,
                List.of(cutDualForTest(inheritedBranchRow, 10.0, -10.0)),
                List.of(candidate),
                List.of(),
                TOLERANCE));

        assertEquals("hybrid vehicle-count branch row uses labeling solver", 1, solver.calls());
        assertClose("hybrid vehicle-count branch row best RC", -5.0, result.bestReducedCost());
        assertEquals("hybrid vehicle-count branch row generated columns", 1, result.columns().size());
    }

    private static void assertHybridNodePricingBackendUsesLabelingForSetOutflowRows() throws Exception {
        Instance instance = singleRequestPricingInstanceWithDuals(0.0, 0.0);
        PricingContext context = PricingContext.noCuts(ReducedCostMatrices.fromInstanceDuals(instance));
        RouteColumn candidate = RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance);
        ContextRoutePricingSolver solver = new ContextRoutePricingSolver(candidate);
        BranchMasterRow inheritedBranchRow = BranchMasterRow.of(SetOutflowConstraint.greaterOrEqual(ints(1), 1));
        double branchPricingDual = -10.0;
        double expectedLabelingReducedCost = context.directReducedCost(candidate.vertexIds())
                + inheritedBranchRow.routePrice(instance, candidate, branchPricingDual);
        BranchNodePricingBackend backend = new HybridNodePricingBackend(
                new LabelingNodePricingBackend(solver),
                new RouteUniverseNodePricingBackend());

        BranchNodePricingResult result = backend.price(new BranchNodePricingRequest(
                instance,
                context,
                List.of(cutDualForTest(inheritedBranchRow, 2.0, branchPricingDual)),
                List.of(candidate),
                List.of(),
                TOLERANCE));

        assertEquals("hybrid set-outflow branch row uses labeling solver", 1, solver.calls());
        assertClose("hybrid set-outflow best RC includes branch row price",
                expectedLabelingReducedCost,
                result.bestReducedCost());
        assertEquals("hybrid set-outflow generated columns", 1, result.columns().size());
        assertEquals("hybrid set-outflow priced columns", 1, result.pricedColumns());
    }

    private static void assertBranchAndPriceFactoryUsesRootLabeling() throws Exception {
        Instance instance = singleRequestPricingInstance();
        List<RouteColumn> columns = List.of(
                RouteColumn.fromRoute("single_candidate", Route.of(0, 1, 2, 3), instance));
        BranchAndPriceSolver.Result result = BranchAndPriceSolver
                .withRootLabeling(PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE), 4)
                .solve(instance, columns);

        assertEquals("root-labeling factory status", "optimal_tiny_branch_tree", result.status());
        assertEquals("root-labeling factory processed root only", 1, result.processedNodes());
        if (result.totalGeneratedColumns() <= 0 || result.totalPricedColumns() <= 0) {
            throw new AssertionError("root-labeling factory should add and audit a priced column"
                    + " generated=" + result.totalGeneratedColumns()
                    + " priced=" + result.totalPricedColumns());
        }
    }

    private static void assertNodeCutPropagationPolicyIsExplicit() {
        BranchAndPriceSolver defaultSolver = new BranchAndPriceSolver();
        BranchAndPriceSolver rootLabeling = BranchAndPriceSolver.withRootLabeling(
                PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE),
                4);
        BranchAndPriceSolver nodeLocalSr = BranchAndPriceSolver.withRootLabelingAndSubsetRowSeparation(
                PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE),
                4);
        BranchAndPriceSolver globalSr = BranchAndPriceSolver.withRootLabelingAndGlobalSubsetRowSeparation(
                PricingMode.BIDIR_DYNAMIC.createSolver(TOLERANCE),
                4);

        assertEquals("default cut propagation policy",
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                defaultSolver.cutPropagationPolicy());
        assertEquals("root-labeling cut propagation policy",
                NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS,
                rootLabeling.cutPropagationPolicy());
        assertEquals("SR separation cut propagation policy",
                NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR,
                nodeLocalSr.cutPropagationPolicy());
        assertEquals("global SR cut propagation policy",
                NodeCutPropagationPolicy.GLOBAL_AUTOMATIC_SR,
                globalSr.cutPropagationPolicy());
        if (NodeCutPropagationPolicy.NO_AUTOMATIC_CUTS.separateSubsetRowsAtNodes()) {
            throw new AssertionError("NO_AUTOMATIC_CUTS must not separate subset-row cuts");
        }
        if (!NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR.separateSubsetRowsAtNodes()) {
            throw new AssertionError("NODE_LOCAL_AUTOMATIC_SR must enable node-local subset-row separation");
        }
        if (NodeCutPropagationPolicy.NODE_LOCAL_AUTOMATIC_SR.publishSeparatedRowsToGlobalPool()) {
            throw new AssertionError("NODE_LOCAL_AUTOMATIC_SR must keep separated rows node-local");
        }
        if (!NodeCutPropagationPolicy.GLOBAL_AUTOMATIC_SR.separateSubsetRowsAtNodes()
                || !NodeCutPropagationPolicy.GLOBAL_AUTOMATIC_SR.publishSeparatedRowsToGlobalPool()) {
            throw new AssertionError("GLOBAL_AUTOMATIC_SR must separate subset-row cuts and publish them");
        }
    }

    private static void assertActiveCutAndInheritedBranchRowPricesCombineOnce() throws Exception {
        Instance instance = singleRequestPricingInstance();
        RouteColumn candidate = RouteColumn.fromRoute(
                "branch_and_active_cut_candidate",
                Route.of(0, 1, 2, 3),
                instance);
        RobustCutRow robustRow = RobustCutRow.ofArcCoefficients(
                "combo_robust_positive_price",
                MasterCutRow.Sense.LESS_EQUAL,
                1.0,
                Map.of(new RobustCut.Arc(0, 1), 1.0));
        BranchMasterRow inheritedBranchRow = BranchMasterRow.of(VehicleCountConstraint.greaterOrEqual(1));
        PricingContext context = PricingContext.withRobustCuts(
                ReducedCostMatrices.fromInstanceDuals(instance),
                List.of(robustRow.toPricingCut(-4.0)));
        List<GurobiRmp.CutDual> cutDuals = List.of(
                cutDualForTest(robustRow, -4.0, 4.0),
                cutDualForTest(inheritedBranchRow, 10.0, -10.0));

        BranchNodePricingResult audit = new RouteUniverseNodePricingBackend().price(new BranchNodePricingRequest(
                instance,
                context,
                cutDuals,
                List.of(candidate),
                List.of(),
                TOLERANCE));

        assertClose("active cut plus inherited branch row RC", -1.0, audit.bestReducedCost());
        assertEquals("active cut plus inherited branch row priced candidates", 1, audit.pricedColumns());
        assertEquals("active cut plus inherited branch row generated candidates",
                1,
                audit.columns().size());
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
                Collections.emptyMap(),
                0.0);
    }

    private static List<RouteColumn> forcedBranchingColumns(Instance instance) {
        List<Route> routes = List.of(
                Route.of(0, 1, 2, 4, 5, 7),
                Route.of(0, 1, 3, 4, 6, 7),
                Route.of(0, 2, 3, 5, 6, 7),
                Route.of(0, 3, 2, 1, 4, 5, 6, 7));
        ArrayList<RouteColumn> columns = new ArrayList<RouteColumn>();
        for (int index = 0; index < routes.size(); index++) {
            columns.add(RouteColumn.fromRoute("forced_" + index, routes.get(index), instance));
        }
        return List.copyOf(columns);
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
                Collections.emptyMap(),
                0.0);
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
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 3), 1.0);
        coefficients.put(new RobustCut.Arc(3, 6), -1.0);
        return RobustCutRow.ofArcCoefficients(
                "combo_public_active_cut_probe",
                MasterCutRow.Sense.LESS_EQUAL,
                0.5,
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
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = denseMatrix(8, 100.0);
        setRouteArcCosts(matrix, 2.0, 0, 1, 4, 2, 5, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1, 4, 3, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 2, 5, 3, 6, 7);
        setRouteArcCosts(matrix, 2.0, 0, 1);
        setRouteArcCosts(matrix, 14.0 / 6.0, 1, 2, 3, 4, 5, 6, 7);
        return new Instance(
                "sr-node-separation",
                3,
                3,
                3,
                vertices,
                matrix,
                matrix,
                Map.of(),
                0.0);
    }

    private static List<RouteColumn> srSeparationColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("sr_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("sr_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("sr_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("sr_full_123", Route.of(0, 1, 2, 3, 4, 5, 6, 7), instance));
    }

    private static RobustCutRow srSeparationRobustCutRow() {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(0, 1), 1.0);
        coefficients.put(new RobustCut.Arc(0, 2), -1.0);
        return RobustCutRow.ofArcCoefficients(
                "sr_mixed_robust_candidate",
                MasterCutRow.Sense.LESS_EQUAL,
                0.25,
                coefficients);
    }

    private static Instance queuedChildSrSeparationInstance() {
        ArrayList<Vertex> vertices = new ArrayList<Vertex>();
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
        int[][] pairs = queuedChildOddCyclePairs();
        for (int requestId = 1; requestId <= 5; requestId++) {
            setRouteArcCosts(matrix, 50.0, 0, requestId, requestId + 5, 11);
        }
        for (int[] pair : pairs) {
            setRouteArcCosts(matrix, 2.0, 0, pair[0], pair[1], pair[0] + 5, pair[1] + 5, 11);
        }
        return new Instance(
                "queued-child-sr-separation",
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
        ArrayList<RouteColumn> columns = new ArrayList<RouteColumn>();
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

    private static Instance subsetRowNodePricingInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 7.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = denseMatrix(8, 10000.0);
        setRouteArcCosts(matrix, 2.0, 0, 1, 2, 4, 5, 7);
        setRouteArcCosts(matrix, 4.0, 0, 2, 3, 5, 6, 7);
        setRouteArcCosts(matrix, 202.2, 0, 3, 1, 6, 4, 7);
        return new Instance(
                "sr-node-pricing",
                3,
                3,
                2,
                vertices,
                matrix,
                matrix,
                Collections.emptyMap(),
                0.0);
    }

    private static List<RouteColumn> subsetRowNodePricingColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("sr_seed_12", Route.of(0, 1, 2, 4, 5, 7), instance),
                RouteColumn.fromRoute("sr_seed_23", Route.of(0, 2, 3, 5, 6, 7), instance),
                RouteColumn.fromRoute("sr_should_not_be_added_without_context",
                        Route.of(0, 3, 1, 6, 4, 7),
                        instance));
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
                "robust-cut-node-pricing",
                2,
                2,
                1,
                vertices,
                matrix,
                matrix,
                Collections.emptyMap(),
                0.0);
    }

    private static List<RouteColumn> robustCutPricingColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("robust_seed", Route.of(0, 1, 2, 3, 4, 5), instance),
                RouteColumn.fromRoute("robust_only_priced_with_cut", Route.of(0, 2, 1, 3, 4, 5), instance));
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
                Collections.emptyMap(),
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

    private static Instance generatedRobustSubsetRowInstance() {
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
                "generated-robust-sr-node-separation",
                3,
                2,
                2,
                vertices,
                matrix,
                matrix,
                Collections.emptyMap(),
                0.0);
    }

    private static List<RouteColumn> generatedRobustSubsetRowColumns(Instance instance) {
        return List.of(
                RouteColumn.fromRoute("generated_mixed_pair_12", Route.of(0, 1, 4, 2, 5, 7), instance),
                RouteColumn.fromRoute("generated_mixed_pair_13", Route.of(0, 1, 4, 3, 6, 7), instance),
                RouteColumn.fromRoute("generated_mixed_pair_23", Route.of(0, 2, 5, 3, 6, 7), instance),
                RouteColumn.fromRoute("generated_mixed_single_3", Route.of(0, 3, 6, 7), instance));
    }

    private static RobustCutRow robustNodePricingCutRow() {
        return robustNodePricingCutRow("robust_node_pricing_probe");
    }

    private static RobustCutRow robustNodePricingCutRow(String name) {
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        coefficients.put(new RobustCut.Arc(1, 2), 1.0);
        coefficients.put(new RobustCut.Arc(0, 2), -1.0);
        return RobustCutRow.ofArcCoefficients(
                name,
                MasterCutRow.Sense.LESS_EQUAL,
                0.5,
                coefficients);
    }

    private static Instance singleRequestPricingInstance() {
        return singleRequestPricingInstanceWithDuals(0.0, 0.0);
    }

    private static Instance singleRequestPricingInstanceWithDuals(double requestDual, double fleetDual) {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.DELIVERY, 1, 2.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(3, Vertex.Type.DEPOT_END, 0, 3.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = denseMatrix(4, 1000.0);
        matrix[0][1] = 5.0;
        matrix[1][2] = 0.0;
        matrix[2][3] = 0.0;
        return new Instance(
                "single-request-pricing",
                1,
                1,
                1,
                vertices,
                matrix,
                matrix,
                Map.of(1, requestDual),
                fleetDual);
    }

    private static Instance twoRequestPricingInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.DELIVERY, 1, 3.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(4, Vertex.Type.DELIVERY, 2, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DEPOT_END, 0, 5.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = denseMatrix(6, 1000.0);
        setRouteArcCosts(matrix, 2.0, 0, 1, 3, 2, 4, 5);
        matrix[0][2] = 5.0;
        matrix[2][4] = 0.0;
        matrix[4][5] = 0.0;
        return new Instance(
                "two-request-pricing",
                2,
                2,
                1,
                vertices,
                matrix,
                matrix,
                Map.of(1, 8.0, 2, 8.0),
                0.0);
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

    private static double robustArcPriceSum(Instance instance, RobustCut cut, List<Integer> vertexIds) {
        double sum = 0.0;
        for (int index = 0; index + 1 < vertexIds.size(); index++) {
            int from = vertexIds.get(index).intValue();
            int to = vertexIds.get(index + 1).intValue();
            sum += cut.arcPrice(instance, from, to);
        }
        return sum;
    }

    private static NodeRelaxationProbe solveNodeRelaxationForTest(
            Instance instance,
            BranchNode node,
            List<RouteColumn> routeUniverse,
            List<? extends MasterCutRow> activeCutRows) throws Exception {
        return solveNodeRelaxationForTest(
                new BranchAndPriceSolver(
                        new VehicleCountBrancher(),
                        new SetOutflowBrancher(),
                        1000.0,
                        TOLERANCE,
                        1),
                instance,
                node,
                routeUniverse,
                activeCutRows);
    }

    private static NodeRelaxationProbe solveNodeRelaxationForTest(
            BranchAndPriceSolver solver,
            Instance instance,
            BranchNode node,
            List<RouteColumn> routeUniverse,
            List<? extends MasterCutRow> activeCutRows) throws Exception {
        Objects.requireNonNull(solver, "solver");
        Method method = BranchAndPriceSolver.class.getDeclaredMethod(
                "solveNodeRelaxation",
                Instance.class,
                BranchNode.class,
                List.class,
                List.class);
        method.setAccessible(true);
        Object relaxation = invokeReflective(method, solver, instance, node, routeUniverse, activeCutRows);
        Object audit = invokePrivate(relaxation, "pricingAudit");
        return new NodeRelaxationProbe(
                ((Double) invokePrivate(relaxation, "lowerBound")).doubleValue(),
                ((Boolean) invokePrivate(relaxation, "hasPositiveArtificial")).booleanValue(),
                ((Double) invokePrivate(audit, "bestReducedCost")).doubleValue(),
                ((Integer) invokePrivate(audit, "pricingCalls")).intValue(),
                ((Integer) invokePrivate(audit, "generatedColumns")).intValue(),
                ((Integer) invokePrivate(relaxation, "activeCutCount")).intValue());
    }

    private static GurobiRmp.CutDual cutDualForTest(
            MasterCutRow row,
            double rawPi,
            double pricingDual) throws Exception {
        Constructor<GurobiRmp.CutDual> constructor = GurobiRmp.CutDual.class.getDeclaredConstructor(
                MasterCutRow.class,
                double.class,
                double.class);
        constructor.setAccessible(true);
        try {
            return constructor.newInstance(row, rawPi, pricingDual);
        } catch (InvocationTargetException e) {
            throw rethrowReflectiveCause(e);
        }
    }

    private static BranchAndPriceSolver.NodeRecord nodeWithConstraint(
            BranchAndPriceSolver.Result result,
            String expression) {
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            if (record.constraints().contains(expression)) {
                return record;
            }
        }
        throw new AssertionError("missing branch node with constraint: " + expression);
    }

    private static List<String> traceRowWithConstraint(String trace, String expression) {
        String[] rows = trace.split("\\R");
        for (int i = 1; i < rows.length; i++) {
            List<String> fields = splitCsv(rows[i]);
            if (fields.size() > 5 && fields.get(4).equals(expression)) {
                return fields;
            }
        }
        throw new AssertionError("missing trace row with constraint: " + expression);
    }

    private static List<String> traceRowWithConstraintPrefix(
            String trace,
            String requiredExpression,
            String prefix) {
        String[] rows = trace.split("\\R");
        for (int i = 1; i < rows.length; i++) {
            List<String> fields = splitCsv(rows[i]);
            if (fields.size() <= 5 || !fields.get(4).contains(requiredExpression)) {
                continue;
            }
            for (String constraint : fields.get(4).split(";")) {
                if (constraint.startsWith(prefix)) {
                    return fields;
                }
            }
        }
        throw new AssertionError("missing trace row with required constraint "
                + requiredExpression + " and prefix " + prefix);
    }

    private static BranchAndPriceSolver.NodeRecord nodeWithConstraintPrefix(
            BranchAndPriceSolver.Result result,
            String requiredExpression,
            String prefix) {
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            if (!record.constraints().contains(requiredExpression)) {
                continue;
            }
            for (String constraint : record.constraints()) {
                if (constraint.startsWith(prefix)) {
                    return record;
                }
            }
        }
        throw new AssertionError("missing branch node with required constraint "
                + requiredExpression + " and prefix " + prefix);
    }

    private static void assertNonNegativeFinalPricing(
            String label,
            BranchAndPriceSolver.Result result) {
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            if (Double.isFinite(record.bestReducedCost()) && record.bestReducedCost() < -TOLERANCE) {
                throw new AssertionError(label + " left a missing negative column"
                        + " node=" + record.nodeId()
                        + " bestReducedCost=" + record.bestReducedCost());
            }
        }
    }

    private static List<String> routeNames(List<RouteColumn> columns) {
        ArrayList<String> names = new ArrayList<String>();
        for (RouteColumn column : columns) {
            names.add(column.name());
        }
        return List.copyOf(names);
    }

    private static Object invokePrivate(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return invokeReflective(method, target);
    }

    private static Object invokeReflective(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw rethrowReflectiveCause(e);
        }
    }

    private static Exception rethrowReflectiveCause(InvocationTargetException e) throws Exception {
        Throwable cause = e.getCause();
        if (cause instanceof Exception exception) {
            throw exception;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new AssertionError(cause);
    }

    private static void assertForbiddenProductionNamesAbsent() throws IOException {
        String forbiddenPattern = "BranchOnArc|FixArc|RemoveArc|ArcBranch|branchOnArc";
        Path branchDir = Path.of("src", "main", "java", "org", "pdptw", "branch");
        if (!Files.exists(branchDir)) {
            branchDir = Path.of("G:\\bid\\pdptw-bcp-java-gurobi\\src\\main\\java\\org\\pdptw\\branch");
        }
        List<String> offenders = new ArrayList<String>();
        try (var stream = Files.walk(branchDir)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path);
                            if (text.matches("(?s).*(" + forbiddenPattern + ").*")) {
                                offenders.add(path.toString());
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }
        if (!offenders.isEmpty()) {
            throw new AssertionError("production branch package contains forbidden names: " + offenders);
        }
    }

    private static void assertDoesNotContainForbiddenWord(String label, String value) {
        if (value.toLowerCase().contains("arc")) {
            throw new AssertionError(label + " must not mention forbidden branch object: " + value);
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

    private static Path expectedPath(String name) {
        Path[] candidates = new Path[] {
                Path.of("..", "references", "tiny", "expected", name),
                Path.of("references", "tiny", "expected", name),
                Path.of("G:\\bid\\references\\tiny\\expected", name)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate tiny expected file: " + name);
    }

    private static List<String> splitCsv(String row) {
        ArrayList<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static List<Integer> ints(Integer... values) {
        return Arrays.asList(values);
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertNonZero(String label, double actual) {
        if (Math.abs(actual) <= TOLERANCE) {
            throw new AssertionError(label + " should be nonzero but got " + actual);
        }
    }

    private static final class RecordingNodePricingBackend implements BranchNodePricingBackend {
        private final BranchNodePricingBackend delegate;
        private final ArrayList<BackendSnapshot> snapshots = new ArrayList<BackendSnapshot>();

        private RecordingNodePricingBackend(BranchNodePricingBackend delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public BranchNodePricingResult price(BranchNodePricingRequest request) {
            int branchRows = 0;
            for (GurobiRmp.CutDual dual : request.cutDuals()) {
                if (dual.row() instanceof BranchMasterRow) {
                    branchRows++;
                }
            }
            boolean sawNonZeroRobustDual = false;
            boolean sawRobustReducedCostShift = false;
            boolean sawNonZeroSubsetRowDual = false;
            boolean sawSubsetRowReducedCostShift = false;
            if (request.pricingContext().hasRobustCuts()) {
                for (RobustCut cut : request.pricingContext().robustCuts()) {
                    if (!Double.isFinite(cut.dualValue())) {
                        throw new AssertionError("robust cut dual must be finite: " + cut.name());
                    }
                    sawNonZeroRobustDual |= Math.abs(cut.dualValue()) > TOLERANCE;
                }
                PricingContext withoutRobustCuts = PricingContext.withCutsAndSetOutflowPricing(
                        request.pricingContext().matrices(),
                        List.of(),
                        request.pricingContext().subsetRowCuts(),
                        request.pricingContext().setOutflowPricingRules());
                for (RouteColumn column : request.routeUniverse()) {
                    double shift = request.pricingContext().directReducedCost(column.route())
                            - withoutRobustCuts.directReducedCost(column.route());
                    double expectedShift = 0.0;
                    for (RobustCut cut : request.pricingContext().robustCuts()) {
                        expectedShift += robustArcPriceSum(request.instance(), cut, column.vertexIds());
                    }
                    assertClose("branch-node robust direct RC shift", expectedShift, shift);
                    sawRobustReducedCostShift |= Math.abs(shift) > TOLERANCE;
                }
            }
            if (request.pricingContext().hasSubsetRowCuts()) {
                for (SubsetRowCut cut : request.pricingContext().subsetRowCuts()) {
                    if (!Double.isFinite(cut.sigma())) {
                        throw new AssertionError("subset-row cut sigma must be finite: " + cut.name());
                    }
                    sawNonZeroSubsetRowDual |= Math.abs(cut.sigma()) > TOLERANCE;
                }
                PricingContext withoutSubsetRows = PricingContext.withCutsAndSetOutflowPricing(
                        request.pricingContext().matrices(),
                        request.pricingContext().robustCuts(),
                        List.of(),
                        request.pricingContext().setOutflowPricingRules());
                for (RouteColumn column : request.routeUniverse()) {
                    double shift = request.pricingContext().directReducedCost(column.route())
                            - withoutSubsetRows.directReducedCost(column.route());
                    double expectedShift = 0.0;
                    for (SubsetRowCut cut : request.pricingContext().subsetRowCuts()) {
                        expectedShift += SRPricingAdjuster.routePricingAdjustment(
                                cut,
                                request.instance(),
                                column.vertexIds());
                    }
                    assertClose("branch-node subset-row direct RC shift", expectedShift, shift);
                    sawSubsetRowReducedCostShift |= Math.abs(shift) > TOLERANCE;
                }
            }
            snapshots.add(new BackendSnapshot(
                    request.pricingContext().hasRobustCuts(),
                    request.pricingContext().hasSubsetRowCuts(),
                    branchRows,
                    request.pricingContext().robustCuts().size(),
                    sawNonZeroRobustDual,
                    sawRobustReducedCostShift,
                    sawNonZeroSubsetRowDual,
                    sawSubsetRowReducedCostShift,
                    request.routeUniverse().size(),
                    request.currentColumns().size()));
            return delegate.price(request);
        }

        private List<BackendSnapshot> snapshots() {
            return List.copyOf(snapshots);
        }
    }

    private record BackendSnapshot(
            boolean hasRobustCuts,
            boolean hasSubsetRowCuts,
            int branchRowCount,
            int robustCutCount,
            boolean sawNonZeroRobustDual,
            boolean sawRobustReducedCostShift,
            boolean sawNonZeroSubsetRowDual,
            boolean sawSubsetRowReducedCostShift,
            int routeUniverseSize,
            int currentColumnCount) {
    }

    private static final class NegativeNoColumnBackend implements BranchNodePricingBackend {
        @Override
        public String name() {
            return "negative_no_column_probe";
        }

        @Override
        public BranchNodePricingResult price(BranchNodePricingRequest request) {
            return BranchNodePricingResult.of(-1.0, 1, List.of());
        }
    }

    private static final class FixedPricingSolver implements PricingSolver {
        private final PricingResult result;
        private int calls;

        private FixedPricingSolver(PricingResult result) {
            this.result = Objects.requireNonNull(result, "result");
        }

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            calls++;
            return result;
        }

        private int calls() {
            return calls;
        }
    }

    private static final class RecordingContextPricingSolver implements PricingSolver {
        private int calls;
        private double observedFleetDual = Double.NaN;

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            throw new AssertionError("recording solver should receive PricingContext");
        }

        @Override
        public PricingResult price(PricingContext context) {
            calls++;
            observedFleetDual = context.fleetDual();
            return PricingResult.noNegativeColumn(0.0);
        }

        private int calls() {
            return calls;
        }

        private double observedFleetDual() {
            return observedFleetDual;
        }
    }

    private static final class ContextRoutePricingSolver implements PricingSolver {
        private final RouteColumn column;
        private int calls;

        private ContextRoutePricingSolver(RouteColumn column) {
            this.column = Objects.requireNonNull(column, "column");
        }

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            throw new AssertionError("context route solver should receive PricingContext");
        }

        @Override
        public PricingResult price(PricingContext context) {
            calls++;
            double reducedCost = context.directReducedCost(column.vertexIds());
            if (reducedCost < -TOLERANCE) {
                return PricingResult.exact(List.of(column), reducedCost);
            }
            return PricingResult.noNegativeColumn(reducedCost);
        }

        private int calls() {
            return calls;
        }
    }

    private static final class RecordingSubsetRowPricingSolver implements PricingSolver {
        private final List<RouteColumn> routeUniverse;
        private final ArrayList<Integer> subsetRowCutCounts = new ArrayList<Integer>();
        private final ArrayList<SubsetRowPricingCall> subsetRowPricingCalls =
                new ArrayList<SubsetRowPricingCall>();
        private int calls;
        private boolean sawSubsetRowContext;
        private boolean sawNonZeroSubsetRowDual;
        private boolean sawSubsetRowReducedCostShift;
        private boolean sawExpectedSubsetRowCut;

        private RecordingSubsetRowPricingSolver(List<RouteColumn> routeUniverse) {
            this.routeUniverse = List.copyOf(routeUniverse);
        }

        @Override
        public PricingResult price(ReducedCostMatrices matrices) {
            return price(PricingContext.noCuts(matrices));
        }

        @Override
        public PricingResult price(PricingContext context) {
            calls++;
            sawSubsetRowContext |= context.hasSubsetRowCuts();
            subsetRowCutCounts.add(Integer.valueOf(context.subsetRowCuts().size()));
            boolean callSawNonZeroSubsetRowDual = false;
            boolean callSawSubsetRowReducedCostShift = false;
            boolean callSawExpectedSubsetRowCut = false;
            ArrayList<String> callSubsetRowKeys = new ArrayList<String>();
            ArrayList<Double> callSubsetRowSigmas = new ArrayList<Double>();
            if (context.hasSubsetRowCuts() && context.hasRobustCuts()) {
                throw new AssertionError("pure SR recording solver should not receive robust cuts");
            }
            PricingContext withoutSubsetRows = PricingContext.withCutsAndSetOutflowPricing(
                    context.matrices(),
                    context.robustCuts(),
                    List.of(),
                    context.setOutflowPricingRules());
            if (context.hasSubsetRowCuts()) {
                for (org.pdptw.cuts.SubsetRowCut cut : context.subsetRowCuts()) {
                    callSubsetRowKeys.add(subsetRowSemanticKey(cut));
                    callSubsetRowSigmas.add(Double.valueOf(cut.sigma()));
                    sawNonZeroSubsetRowDual |= Math.abs(cut.sigma()) > TOLERANCE;
                    callSawNonZeroSubsetRowDual |= Math.abs(cut.sigma()) > TOLERANCE;
                    boolean expectedSubsetRowCut = cut.name().equals("sr-U1-2-3-l2")
                            && cut.l() == 2
                            && cut.requests().size() == 3
                            && cut.requests().containsAll(List.of(
                                    Integer.valueOf(1),
                                    Integer.valueOf(2),
                                    Integer.valueOf(3)));
                    sawExpectedSubsetRowCut |= expectedSubsetRowCut;
                    callSawExpectedSubsetRowCut |= expectedSubsetRowCut;
                }
            }

            double bestReducedCost = Double.POSITIVE_INFINITY;
            ArrayList<RouteColumn> negativeColumns = new ArrayList<RouteColumn>();
            for (RouteColumn column : routeUniverse) {
                double reducedCost = context.directReducedCost(column.route());
                if (context.hasSubsetRowCuts()) {
                    double subsetRowShift = reducedCost - withoutSubsetRows.directReducedCost(column.route());
                    double expectedShift = 0.0;
                    for (org.pdptw.cuts.SubsetRowCut cut : context.subsetRowCuts()) {
                        expectedShift += SRPricingAdjuster.routePricingAdjustment(
                                cut,
                                context.instance(),
                                column.vertexIds());
                    }
                    if (Math.abs(subsetRowShift - expectedShift) > TOLERANCE) {
                        throw new AssertionError("branch-node SR direct RC shift should match route adjustment"
                                + " route=" + column.name()
                                + " shift=" + subsetRowShift
                                + " expected=" + expectedShift);
                    }
                    if (Math.abs(subsetRowShift) > TOLERANCE) {
                        sawSubsetRowReducedCostShift = true;
                        callSawSubsetRowReducedCostShift = true;
                    }
                }
                if (reducedCost < bestReducedCost) {
                    bestReducedCost = reducedCost;
                }
                if (reducedCost < -TOLERANCE) {
                    negativeColumns.add(column);
                }
            }
            subsetRowPricingCalls.add(new SubsetRowPricingCall(
                    context.subsetRowCuts().size(),
                    callSawNonZeroSubsetRowDual,
                    callSawSubsetRowReducedCostShift,
                    callSawExpectedSubsetRowCut,
                    callSubsetRowKeys,
                    callSubsetRowSigmas));
            if (negativeColumns.isEmpty()) {
                return PricingResult.noNegativeColumn(bestReducedCost);
            }
            return PricingResult.exact(negativeColumns, bestReducedCost);
        }

        private int calls() {
            return calls;
        }

        private int firstSubsetRowCutCount() {
            if (subsetRowCutCounts.isEmpty()) {
                return -1;
            }
            return subsetRowCutCounts.get(0).intValue();
        }

        private int maxSubsetRowCutCount() {
            int max = 0;
            for (Integer count : subsetRowCutCounts) {
                max = Math.max(max, count.intValue());
            }
            return max;
        }

        private boolean sawSubsetRowCutCount(int expected) {
            for (Integer count : subsetRowCutCounts) {
                if (count.intValue() == expected) {
                    return true;
                }
            }
            return false;
        }

        private boolean sawSubsetRowPricingCall(int expectedCutCount) {
            for (SubsetRowPricingCall call : subsetRowPricingCalls) {
                if (call.subsetRowCutCount() == expectedCutCount
                        && call.sawNonZeroSubsetRowDual()
                        && call.sawSubsetRowReducedCostShift()
                        && call.sawExpectedSubsetRowCut()) {
                    return true;
                }
            }
            return false;
        }

        private int subsetRowPricingCallCount() {
            return subsetRowPricingCalls.size();
        }

        private boolean sawSubsetRowCutCountFrom(int startIndex, int expectedCutCount) {
            for (int index = startIndex; index < subsetRowPricingCalls.size(); index++) {
                if (subsetRowPricingCalls.get(index).subsetRowCutCount() == expectedCutCount) {
                    return true;
                }
            }
            return false;
        }

        private boolean sawSubsetRowContextFrom(int startIndex) {
            for (int index = startIndex; index < subsetRowPricingCalls.size(); index++) {
                if (subsetRowPricingCalls.get(index).subsetRowCutCount() > 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean sawSubsetRowContext() {
            return sawSubsetRowContext;
        }

        private void assertPricingCallCutCountsMatchNodeRecords(
                List<BranchAndPriceSolver.NodeRecord> records,
                String scenario) {
            int callIndex = 0;
            for (BranchAndPriceSolver.NodeRecord record : records) {
                for (int localCall = 0; localCall < record.pricingCalls(); localCall++) {
                    if (callIndex >= subsetRowPricingCalls.size()) {
                        throw new AssertionError(scenario + " missing pricing call"
                                + " node=" + record.nodeId()
                                + " callIndex=" + callIndex
                                + " expectedTotalAtLeast=" + (callIndex + 1));
                    }
                    SubsetRowPricingCall call = subsetRowPricingCalls.get(callIndex);
                    if (call.subsetRowCutCount() != record.activeCutCount()) {
                        throw new AssertionError(scenario + " pricing context cut count must match node record"
                                + " node=" + record.nodeId()
                                + " depth=" + record.depth()
                                + " localCall=" + localCall
                                + " expected=" + record.activeCutCount()
                                + " actual=" + call.subsetRowCutCount());
                    }
                    if (record.activeCutCount() > 0 && !call.sawExpectedSubsetRowCut()) {
                        throw new AssertionError(scenario + " pricing context should include expected SR row"
                                + " node=" + record.nodeId()
                                + " depth=" + record.depth()
                                + " localCall=" + localCall
                                + " activeCutCount=" + record.activeCutCount());
                    }
                    if (record.activeCutCount() > 0) {
                        if (call.subsetRowSigmas().size() != record.activeCutCount()) {
                            throw new AssertionError(scenario + " pricing context should carry one SR sigma per active cut"
                                    + " node=" + record.nodeId()
                                    + " depth=" + record.depth()
                                    + " localCall=" + localCall
                                    + " activeCutCount=" + record.activeCutCount()
                                    + " sigmas=" + call.subsetRowSigmas());
                        }
                        for (Double sigma : call.subsetRowSigmas()) {
                            if (!Double.isFinite(sigma.doubleValue())) {
                                throw new AssertionError(scenario + " SR sigma must be finite"
                                        + " node=" + record.nodeId()
                                        + " depth=" + record.depth()
                                        + " sigma=" + sigma);
                            }
                        }
                    }
                    callIndex++;
                }
            }
            if (callIndex != subsetRowPricingCalls.size()) {
                throw new AssertionError(scenario + " recorded pricing calls should match node records"
                        + " consumed=" + callIndex
                        + " recorded=" + subsetRowPricingCalls.size());
            }
        }

        private List<String> lastSubsetRowKeysForNode(
                List<BranchAndPriceSolver.NodeRecord> records,
                int nodeIndex) {
            if (nodeIndex < 0 || nodeIndex >= records.size()) {
                throw new AssertionError("node index out of range: " + nodeIndex);
            }
            int callIndex = 0;
            for (int index = 0; index < nodeIndex; index++) {
                callIndex += records.get(index).pricingCalls();
            }
            BranchAndPriceSolver.NodeRecord record = records.get(nodeIndex);
            if (record.pricingCalls() <= 0) {
                return List.of();
            }
            int finalCallIndex = callIndex + record.pricingCalls() - 1;
            if (finalCallIndex >= subsetRowPricingCalls.size()) {
                throw new AssertionError("missing pricing call for node"
                        + " node=" + record.nodeId()
                        + " nodeIndex=" + nodeIndex
                        + " finalCallIndex=" + finalCallIndex
                        + " recorded=" + subsetRowPricingCalls.size());
            }
            return subsetRowPricingCalls.get(finalCallIndex).subsetRowKeys();
        }

        private static String subsetRowSemanticKey(org.pdptw.cuts.SubsetRowCut cut) {
            ArrayList<Integer> requests = new ArrayList<Integer>(cut.requests());
            Collections.sort(requests);
            return "l=" + cut.l() + ";requests=" + requests;
        }

        private boolean sawNonZeroSubsetRowDual() {
            return sawNonZeroSubsetRowDual;
        }

        private boolean sawSubsetRowReducedCostShift() {
            return sawSubsetRowReducedCostShift;
        }

        private boolean sawExpectedSubsetRowCut() {
            return sawExpectedSubsetRowCut;
        }
    }

    private record SubsetRowPricingCall(
            int subsetRowCutCount,
            boolean sawNonZeroSubsetRowDual,
            boolean sawSubsetRowReducedCostShift,
            boolean sawExpectedSubsetRowCut,
            List<String> subsetRowKeys,
            List<Double> subsetRowSigmas) {
    }

    private record NodeRelaxationProbe(
            double lowerBound,
            boolean hasPositiveArtificial,
            double bestReducedCost,
            int pricingCalls,
            int generatedColumns,
            int activeCutCount) {
    }
}
