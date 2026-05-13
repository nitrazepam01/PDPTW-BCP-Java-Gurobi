package org.pdptw.pricing;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.cuts.DtiPtiRepair;
import org.pdptw.cuts.RobustCut;
import org.pdptw.cuts.SRPricingAdjuster;
import org.pdptw.cuts.SubsetRowCut;
import org.pdptw.cuts.SubsetRowResourceState;

import java.util.List;
import java.util.Objects;

public final class PricingContext {
    private final ReducedCostMatrices matrices;
    private final List<RobustCut> robustCuts;
    private final List<SubsetRowCut> subsetRowCuts;
    private final List<SetOutflowPricingRule> setOutflowPricingRules;
    private final DtiPtiRepair.RepairResult forwardRepair;
    private final DtiPtiRepair.RepairResult backwardRepair;

    private PricingContext(
            ReducedCostMatrices matrices,
            List<RobustCut> robustCuts,
            List<SubsetRowCut> subsetRowCuts,
            List<SetOutflowPricingRule> setOutflowPricingRules,
            DtiPtiRepair.RepairResult forwardRepair,
            DtiPtiRepair.RepairResult backwardRepair) {
        this.matrices = Objects.requireNonNull(matrices, "matrices");
        this.robustCuts = List.copyOf(Objects.requireNonNull(robustCuts, "robustCuts"));
        this.subsetRowCuts = List.copyOf(Objects.requireNonNull(subsetRowCuts, "subsetRowCuts"));
        this.setOutflowPricingRules = List.copyOf(Objects.requireNonNull(
                setOutflowPricingRules,
                "setOutflowPricingRules"));
        this.forwardRepair = forwardRepair;
        this.backwardRepair = backwardRepair;
        if (hasRobustCuts() && (forwardRepair == null || backwardRepair == null)) {
            throw new IllegalArgumentException("robust pricing context requires forward and backward repair");
        }
    }

    public static PricingContext noCuts(ReducedCostMatrices matrices) {
        return new PricingContext(matrices, List.of(), List.of(), List.of(), null, null);
    }

    public static PricingContext withRobustCuts(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> robustCuts) {
        return withCuts(matrices, robustCuts, List.of());
    }

    public static PricingContext withSubsetRowCuts(
            ReducedCostMatrices matrices,
            List<? extends SubsetRowCut> subsetRowCuts) {
        return withCuts(matrices, List.of(), subsetRowCuts);
    }

    public static PricingContext withCuts(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> robustCuts,
            List<? extends SubsetRowCut> subsetRowCuts) {
        return withCutsAndSetOutflowPricing(matrices, robustCuts, subsetRowCuts, List.of());
    }

    public static PricingContext withSetOutflowPricing(
            PricingContext base,
            List<? extends SetOutflowPricingRule> setOutflowPricingRules) {
        Objects.requireNonNull(base, "base");
        return withCutsAndSetOutflowPricing(
                base.matrices(),
                base.robustCuts(),
                base.subsetRowCuts(),
                setOutflowPricingRules);
    }

    public static PricingContext withCutsAndSetOutflowPricing(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> robustCuts,
            List<? extends SubsetRowCut> subsetRowCuts,
            List<? extends SetOutflowPricingRule> setOutflowPricingRules) {
        Objects.requireNonNull(matrices, "matrices");
        Objects.requireNonNull(robustCuts, "robustCuts");
        Objects.requireNonNull(subsetRowCuts, "subsetRowCuts");
        Objects.requireNonNull(setOutflowPricingRules, "setOutflowPricingRules");
        if (robustCuts.isEmpty() && subsetRowCuts.isEmpty() && setOutflowPricingRules.isEmpty()) {
            return noCuts(matrices);
        }
        List<RobustCut> cuts = List.copyOf(robustCuts);
        List<SubsetRowCut> srCuts = List.copyOf(subsetRowCuts);
        List<SetOutflowPricingRule> branchRules = List.copyOf(setOutflowPricingRules);
        if (cuts.isEmpty()) {
            return new PricingContext(matrices, cuts, srCuts, branchRules, null, null);
        }
        return new PricingContext(
                matrices,
                cuts,
                srCuts,
                branchRules,
                DtiPtiRepair.repairForwardDti(matrices, cuts),
                DtiPtiRepair.repairBackwardPti(matrices, cuts));
    }

    public ReducedCostMatrices matrices() {
        return matrices;
    }

    public Instance instance() {
        return matrices.instance();
    }

    public List<RobustCut> robustCuts() {
        return robustCuts;
    }

    public List<SubsetRowCut> subsetRowCuts() {
        return subsetRowCuts;
    }

    public boolean hasRobustCuts() {
        return !robustCuts.isEmpty();
    }

    public boolean hasSubsetRowCuts() {
        return !subsetRowCuts.isEmpty();
    }

    public List<SetOutflowPricingRule> setOutflowPricingRules() {
        return setOutflowPricingRules;
    }

    public boolean hasSetOutflowPricingRules() {
        return !setOutflowPricingRules.isEmpty();
    }

    public boolean hasCuts() {
        return hasRobustCuts() || hasSubsetRowCuts();
    }

    public DtiPtiRepair.RepairResult forwardRepair() {
        if (!hasRobustCuts()) {
            throw new IllegalStateException("no robust forward repair is active");
        }
        return forwardRepair;
    }

    public DtiPtiRepair.RepairResult backwardRepair() {
        if (!hasRobustCuts()) {
            throw new IllegalStateException("no robust backward repair is active");
        }
        return backwardRepair;
    }

    public double requestDual(int requestId) {
        return matrices.requestDual(requestId);
    }

    public double fleetDual() {
        return matrices.fleetDual();
    }

    public double routeCost(Route route) {
        return matrices.routeCost(route);
    }

    public double routeCost(List<Integer> vertexIds) {
        return matrices.routeCost(vertexIds);
    }

    public double directReducedCost(Route route) {
        return directReducedCost(route.vertexIds());
    }

    public double directReducedCost(List<Integer> vertexIds) {
        double reducedCost = matrices.directReducedCost(vertexIds);
        if (hasRobustCuts()) {
            reducedCost += robustArcPriceSum(vertexIds);
        }
        if (hasSubsetRowCuts()) {
            reducedCost += subsetRowPriceSum(vertexIds);
        }
        if (hasSetOutflowPricingRules()) {
            reducedCost += setOutflowPriceSum(vertexIds);
        }
        return reducedCost;
    }

    public double forwardArcReducedCost(int from, int to) {
        return hasRobustCuts()
                ? forwardRepair.arcReducedCost(from, to)
                : matrices.forwardArcReducedCost(from, to);
    }

    public double backwardArcReducedCost(int from, int to) {
        return hasRobustCuts()
                ? backwardRepair.arcReducedCost(from, to)
                : matrices.backwardArcReducedCost(from, to);
    }

    public boolean satisfiesForwardDti() {
        if (hasSetOutflowPricingRules()) {
            return false;
        }
        return hasRobustCuts()
                ? DtiPtiRepair.satisfiesForwardDti(instance(), forwardRepair.matrix())
                : ForwardLabeler.satisfiesForwardDti(matrices);
    }

    public boolean satisfiesBackwardPti() {
        if (hasSetOutflowPricingRules()) {
            return false;
        }
        return hasRobustCuts()
                ? DtiPtiRepair.satisfiesBackwardPti(instance(), backwardRepair.matrix())
                : BackwardLabeler.satisfiesBackwardPti(matrices);
    }

    public double correctedMergedReducedCost(ForwardLabel forward, BackwardLabel backward) {
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(backward, "backward");
        return forward.reducedCost() + backward.reducedCost() + mergeCorrection(forward, backward);
    }

    public double mergeCorrection(ForwardLabel forward, BackwardLabel backward) {
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(backward, "backward");
        if (hasRobustCuts()) {
            return DtiPtiRepair.robustMergeCorrection(
                    matrices,
                    forward.openMask(),
                    backward.openMask(),
                    forwardRepair,
                    backwardRepair)
                    + subsetRowMergeCorrection(forward, backward);
        }
        long intersection = forward.openMask() & backward.openMask();
        long symmetricDifference = (forward.openMask() | backward.openMask()) & ~intersection;
        return requestDualSum(intersection)
                + 0.5 * requestDualSum(symmetricDifference)
                + subsetRowMergeCorrection(forward, backward);
    }

    public int[] initialSubsetRowCounts() {
        return new int[subsetRowCuts.size()];
    }

    public int[] initialSetOutflowStates() {
        return new int[setOutflowPricingRules.size()];
    }

    public int subsetRowCutCount() {
        return subsetRowCuts.size();
    }

    public SrTransition forwardSubsetRowTransition(int[] currentCounts, int vertexId) {
        return subsetRowTransition(currentCounts, vertexId, true);
    }

    public SrTransition backwardSubsetRowTransition(int[] currentCounts, int vertexId) {
        return subsetRowTransition(currentCounts, vertexId, false);
    }

    public SetOutflowTransition forwardSetOutflowTransition(int[] currentStates, int vertexId) {
        requireSetOutflowStateLength(currentStates, "forward");
        if (!hasSetOutflowPricingRules()) {
            return new SetOutflowTransition(currentStates.clone(), 0.0);
        }
        int[] nextStates = currentStates.clone();
        double reducedCostAdjustment = 0.0;
        VertexKind vertexKind = vertexKind(vertexId);
        for (int index = 0; index < setOutflowPricingRules.size(); index++) {
            SetOutflowPricingRule rule = setOutflowPricingRules.get(index);
            SetOutflowPricingRule.Transition transition;
            if (vertexKind.requestVertex()) {
                transition = rule.forwardTransition(nextStates[index], vertexKind.requestId());
            } else if (vertexId == instance().endDepotId()) {
                transition = rule.forwardTerminalTransition(nextStates[index]);
            } else {
                transition = new SetOutflowPricingRule.Transition(nextStates[index], 0.0);
            }
            nextStates[index] = transition.state();
            reducedCostAdjustment += transition.adjustment();
        }
        return new SetOutflowTransition(nextStates, reducedCostAdjustment);
    }

    public SetOutflowTransition backwardSetOutflowTransition(int[] currentStates, int vertexId) {
        requireSetOutflowStateLength(currentStates, "backward");
        if (!hasSetOutflowPricingRules()) {
            return new SetOutflowTransition(currentStates.clone(), 0.0);
        }
        int[] nextStates = currentStates.clone();
        double reducedCostAdjustment = 0.0;
        VertexKind vertexKind = vertexKind(vertexId);
        for (int index = 0; index < setOutflowPricingRules.size(); index++) {
            SetOutflowPricingRule rule = setOutflowPricingRules.get(index);
            SetOutflowPricingRule.Transition transition = vertexKind.requestVertex()
                    ? rule.backwardPrependTransition(nextStates[index], vertexKind.requestId())
                    : new SetOutflowPricingRule.Transition(nextStates[index], 0.0);
            nextStates[index] = transition.state();
            reducedCostAdjustment += transition.adjustment();
        }
        return new SetOutflowTransition(nextStates, reducedCostAdjustment);
    }

    private double robustArcPriceSum(List<Integer> vertexIds) {
        double sum = 0.0;
        Instance instance = instance();
        for (int index = 0; index + 1 < vertexIds.size(); index++) {
            int from = vertexIds.get(index).intValue();
            int to = vertexIds.get(index + 1).intValue();
            sum += DtiPtiRepair.robustArcPrice(instance, robustCuts, from, to);
        }
        return sum;
    }

    private double subsetRowPriceSum(List<Integer> vertexIds) {
        double sum = 0.0;
        Instance instance = instance();
        for (SubsetRowCut cut : subsetRowCuts) {
            sum += SRPricingAdjuster.routePricingAdjustment(cut, instance, vertexIds);
        }
        return sum;
    }

    private double setOutflowPriceSum(List<Integer> vertexIds) {
        double sum = 0.0;
        Instance instance = instance();
        for (SetOutflowPricingRule rule : setOutflowPricingRules) {
            sum += rule.routePrice(instance, vertexIds);
        }
        return sum;
    }

    private double subsetRowMergeCorrection(ForwardLabel forward, BackwardLabel backward) {
        if (!hasSubsetRowCuts()) {
            return 0.0;
        }
        double correction = 0.0;
        int[] forwardCounts = forward.subsetRowRelevantVisitCounts();
        int[] backwardCounts = backward.subsetRowRelevantVisitCounts();
        requireSrCountLength(forwardCounts, "forward");
        requireSrCountLength(backwardCounts, "backward");
        for (int index = 0; index < subsetRowCuts.size(); index++) {
            SubsetRowCut cut = subsetRowCuts.get(index);
            correction += SRPricingAdjuster.mergeCorrection(
                    cut,
                    forward.openMask(),
                    backward.openMask(),
                    SubsetRowResourceState.of(cut, forwardCounts[index]),
                    SubsetRowResourceState.of(cut, backwardCounts[index]));
        }
        return correction;
    }

    private SrTransition subsetRowTransition(int[] currentCounts, int vertexId, boolean forward) {
        requireSrCountLength(currentCounts, forward ? "forward" : "backward");
        if (!hasSubsetRowCuts()) {
            return new SrTransition(currentCounts.clone(), 0.0);
        }
        int[] nextCounts = currentCounts.clone();
        double reducedCostAdjustment = 0.0;
        for (int index = 0; index < subsetRowCuts.size(); index++) {
            SubsetRowCut cut = subsetRowCuts.get(index);
            SubsetRowResourceState state = SubsetRowResourceState.of(cut, currentCounts[index]);
            SRPricingAdjuster.Transition transition = forward
                    ? SRPricingAdjuster.forwardPickupTransition(state, instance(), vertexId)
                    : SRPricingAdjuster.backwardDeliveryTransition(state, instance(), vertexId);
            nextCounts[index] = transition.after().relevantVisitCount();
            reducedCostAdjustment += transition.reducedCostAdjustment();
        }
        return new SrTransition(nextCounts, reducedCostAdjustment);
    }

    private void requireSrCountLength(int[] counts, String label) {
        Objects.requireNonNull(counts, label + " subset-row counts");
        if (counts.length != subsetRowCuts.size()) {
            throw new IllegalArgumentException(label + " subset-row count length "
                    + counts.length + " does not match active cut count " + subsetRowCuts.size());
        }
    }

    private void requireSetOutflowStateLength(int[] states, String label) {
        Objects.requireNonNull(states, label + " set-outflow states");
        if (states.length != setOutflowPricingRules.size()) {
            throw new IllegalArgumentException(label + " set-outflow state length "
                    + states.length + " does not match active rule count " + setOutflowPricingRules.size());
        }
        for (int state : states) {
            SetOutflowPricingRule.requireState(state, label + " set-outflow state");
        }
    }

    private double requestDualSum(long mask) {
        double sum = 0.0;
        for (int requestId = 1; requestId <= instance().nRequests(); requestId++) {
            if (org.pdptw.core.BitSetOps.contains(mask, requestId)) {
                sum += matrices.requestDual(requestId);
            }
        }
        return sum;
    }

    public static final class SrTransition {
        private final int[] counts;
        private final double reducedCostAdjustment;

        private SrTransition(int[] counts, double reducedCostAdjustment) {
            this.counts = counts.clone();
            if (!Double.isFinite(reducedCostAdjustment)) {
                throw new IllegalArgumentException("reducedCostAdjustment must be finite: "
                        + reducedCostAdjustment);
            }
            this.reducedCostAdjustment = reducedCostAdjustment;
        }

        public int[] counts() {
            return counts.clone();
        }

        public double reducedCostAdjustment() {
            return reducedCostAdjustment;
        }
    }

    public static final class SetOutflowTransition {
        private final int[] states;
        private final double reducedCostAdjustment;

        private SetOutflowTransition(int[] states, double reducedCostAdjustment) {
            this.states = states.clone();
            for (int state : this.states) {
                SetOutflowPricingRule.requireState(state, "set-outflow transition state");
            }
            if (!Double.isFinite(reducedCostAdjustment)) {
                throw new IllegalArgumentException("reducedCostAdjustment must be finite: "
                        + reducedCostAdjustment);
            }
            this.reducedCostAdjustment = reducedCostAdjustment;
        }

        public int[] states() {
            return states.clone();
        }

        public double reducedCostAdjustment() {
            return reducedCostAdjustment;
        }
    }

    private VertexKind vertexKind(int vertexId) {
        org.pdptw.core.Vertex vertex = instance().vertex(vertexId);
        if (vertex.isPickup() || vertex.isDelivery()) {
            return new VertexKind(true, vertex.requestId());
        }
        return new VertexKind(false, 0);
    }

    private static final class VertexKind {
        private final boolean requestVertex;
        private final int requestId;

        private VertexKind(boolean requestVertex, int requestId) {
            this.requestVertex = requestVertex;
            this.requestId = requestId;
        }

        private boolean requestVertex() {
            return requestVertex;
        }

        private int requestId() {
            return requestId;
        }
    }
}
