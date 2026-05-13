package org.pdptw.cuts;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class SRPricingAdjuster {
    private SRPricingAdjuster() {
    }

    public static double pricingAdjustmentForCoefficient(SubsetRowCut cut, int coefficient) {
        Objects.requireNonNull(cut, "cut");
        if (coefficient < 0) {
            throw new IllegalArgumentException("coefficient must be non-negative: " + coefficient);
        }
        return -cut.sigma() * coefficient;
    }

    public static double routePricingAdjustment(SubsetRowCut cut, Collection<Integer> servedRequests) {
        return pricingAdjustmentForCoefficient(cut, cut.coefficientForServedRequests(servedRequests));
    }

    public static double routePricingAdjustment(
            SubsetRowCut cut,
            Instance instance,
            List<Integer> vertexIds) {
        return pricingAdjustmentForCoefficient(cut, cut.coefficientForRoute(instance, vertexIds));
    }

    public static Transition forwardPickupTransition(
            SubsetRowResourceState state,
            Instance instance,
            int vertexId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(instance, "instance");
        Vertex vertex = instance.vertex(vertexId);
        if (!vertex.isPickup() || !state.cut().containsRequest(vertex.requestId())) {
            return Transition.unchanged(state);
        }
        return transitionForRelevantVisit(state);
    }

    public static Transition backwardDeliveryTransition(
            SubsetRowResourceState state,
            Instance instance,
            int vertexId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(instance, "instance");
        Vertex vertex = instance.vertex(vertexId);
        if (!vertex.isDelivery() || !state.cut().containsRequest(vertex.requestId())) {
            return Transition.unchanged(state);
        }
        return transitionForRelevantVisit(state);
    }

    public static Transition transitionForRelevantVisit(SubsetRowResourceState state) {
        Objects.requireNonNull(state, "state");
        SubsetRowResourceState next = state.afterRelevantVisit();
        int delta = next.coefficient() - state.coefficient();
        return new Transition(state, next, delta, pricingAdjustmentForCoefficient(state.cut(), delta));
    }

    public static double mergeCorrection(
            SubsetRowCut cut,
            long forwardOpenMask,
            long backwardOpenMask,
            SubsetRowResourceState forwardState,
            SubsetRowResourceState backwardState) {
        return cut.sigma() * mergeCorrectionCoefficient(
                cut,
                forwardOpenMask,
                backwardOpenMask,
                forwardState,
                backwardState);
    }

    public static int mergeCorrectionCoefficient(
            SubsetRowCut cut,
            long forwardOpenMask,
            long backwardOpenMask,
            SubsetRowResourceState forwardState,
            SubsetRowResourceState backwardState) {
        Objects.requireNonNull(cut, "cut");
        requireSameCut(cut, forwardState, "forwardState");
        requireSameCut(cut, backwardState, "backwardState");
        if (cut.l() != 2) {
            throw new IllegalArgumentException("SR merge correction currently supports l=2, got " + cut.l());
        }
        int openAcrossMerge = BitSetOps.size(cut.requestMask() & (forwardOpenMask | backwardOpenMask));
        int forwardCoefficient = forwardState.coefficient();
        int backwardCoefficient = backwardState.coefficient();
        int directRelevantVisits = forwardState.relevantVisitCount()
                + backwardState.relevantVisitCount()
                - openAcrossMerge;
        int directCoefficient = directRelevantVisits / cut.l();
        return forwardCoefficient + backwardCoefficient - directCoefficient;
    }

    public static double correctedMergedReducedCost(
            double forwardReducedCost,
            double backwardReducedCost,
            SubsetRowCut cut,
            long forwardOpenMask,
            long backwardOpenMask,
            SubsetRowResourceState forwardState,
            SubsetRowResourceState backwardState) {
        return forwardReducedCost
                + backwardReducedCost
                + mergeCorrection(cut, forwardOpenMask, backwardOpenMask, forwardState, backwardState);
    }

    private static void requireSameCut(SubsetRowCut cut, SubsetRowResourceState state, String name) {
        Objects.requireNonNull(state, name);
        if (state.cut().l() != cut.l()
                || state.cut().requestMask() != cut.requestMask()
                || Math.abs(state.cut().sigma() - cut.sigma()) > 0.0) {
            throw new IllegalArgumentException(name + " belongs to a different cut");
        }
    }

    public static final class Transition {
        private final SubsetRowResourceState before;
        private final SubsetRowResourceState after;
        private final int coefficientDelta;
        private final double reducedCostAdjustment;

        private Transition(
                SubsetRowResourceState before,
                SubsetRowResourceState after,
                int coefficientDelta,
                double reducedCostAdjustment) {
            this.before = Objects.requireNonNull(before, "before");
            this.after = Objects.requireNonNull(after, "after");
            this.coefficientDelta = coefficientDelta;
            this.reducedCostAdjustment = requireFinite(reducedCostAdjustment, "reducedCostAdjustment");
        }

        private static Transition unchanged(SubsetRowResourceState state) {
            return new Transition(state, state, 0, 0.0);
        }

        public SubsetRowResourceState before() {
            return before;
        }

        public SubsetRowResourceState after() {
            return after;
        }

        public int coefficientDelta() {
            return coefficientDelta;
        }

        public double reducedCostAdjustment() {
            return reducedCostAdjustment;
        }

        private static double requireFinite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite: " + value);
            }
            return value;
        }
    }
}
