package org.pdptw.pricing;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BackwardLabeler {
    private static final double EPS = 1.0e-9;

    private final boolean useStrongDominance;

    public BackwardLabeler() {
        this(false);
    }

    public BackwardLabeler(boolean useStrongDominance) {
        this.useStrongDominance = useStrongDominance;
    }

    public Result solve(Instance instance) {
        return solve(ReducedCostMatrices.fromInstanceDuals(instance));
    }

    public Result solve(ReducedCostMatrices matrices) {
        return solve(PricingContext.noCuts(matrices));
    }

    public Result solve(PricingContext context) {
        Objects.requireNonNull(context, "context");
        Instance instance = context.instance();
        boolean ptiVerified = context.satisfiesBackwardPti();
        if (useStrongDominance && !ptiVerified) {
            throw new IllegalStateException("Backward strong dominance requires PTI.");
        }

        BackwardLabel sink = new BackwardLabel(
                instance.endDepotId(),
                0.0,
                instance.vertex(instance.endDepotId()).dueTime(),
                0,
                0L,
                0L,
                context.initialSubsetRowCounts(),
                context.initialSetOutflowStates(),
                Collections.singletonList(Integer.valueOf(instance.endDepotId())));

        ArrayDeque<BackwardLabel> queue = new ArrayDeque<BackwardLabel>();
        ArrayList<BackwardLabel> partialLabels = new ArrayList<BackwardLabel>();
        ArrayList<BackwardLabel> completeLabels = new ArrayList<BackwardLabel>();
        queue.add(sink);
        partialLabels.add(sink);

        while (!queue.isEmpty()) {
            BackwardLabel label = queue.removeFirst();
            if (label.openMask() == 0L && label.completedMask() != 0L) {
                Optional<BackwardLabel> origin = LabelExtender.backwardExtend(context, label, instance.startDepotId());
                if (origin.isPresent()) {
                    addComplete(completeLabels, origin.get());
                }
            }

            for (int requestId : instance.requestIds()) {
                if (!BitSetOps.contains(label.openMask(), requestId)
                        && !BitSetOps.contains(label.completedMask(), requestId)) {
                    addPartial(queue, partialLabels, ptiVerified,
                            LabelExtender.backwardExtend(context, label, instance.request(requestId).deliveryVertexId()));
                }
                if (BitSetOps.contains(label.openMask(), requestId)) {
                    addPartial(queue, partialLabels, ptiVerified,
                            LabelExtender.backwardExtend(context, label, instance.request(requestId).pickupVertexId()));
                }
            }
        }

        return new Result(bestLabel(instance, completeLabels), completeLabels, partialLabels);
    }

    public Result price(Instance instance) {
        return solve(instance);
    }

    public Result price(ReducedCostMatrices matrices) {
        return solve(matrices);
    }

    public static Result priceStatic(Instance instance) {
        return new BackwardLabeler().solve(instance);
    }

    public static Result priceStatic(ReducedCostMatrices matrices) {
        return new BackwardLabeler().solve(matrices);
    }

    private void addPartial(
            ArrayDeque<BackwardLabel> queue,
            ArrayList<BackwardLabel> labels,
            boolean ptiVerified,
            Optional<BackwardLabel> candidate) {
        if (candidate.isEmpty()) {
            return;
        }
        BackwardLabel label = candidate.get();
        if (useStrongDominance && isDominated(labels, label, ptiVerified)) {
            return;
        }
        if (useStrongDominance) {
            labels.removeIf(existing -> Dominance.backwardStrongDominates(label, existing, ptiVerified)
                    && !sameRoute(label, existing));
        }
        labels.add(label);
        queue.addLast(label);
    }

    private static boolean isDominated(List<BackwardLabel> labels, BackwardLabel candidate, boolean ptiVerified) {
        for (BackwardLabel label : labels) {
            if (Dominance.backwardStrongDominates(label, candidate, ptiVerified) && !sameRoute(label, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void addComplete(ArrayList<BackwardLabel> labels, BackwardLabel candidate) {
        labels.add(candidate);
    }

    private static BackwardLabel bestLabel(Instance instance, List<BackwardLabel> labels) {
        BackwardLabel best = null;
        for (BackwardLabel label : labels) {
            if (best == null || isBetter(instance, label, best)) {
                best = label;
            }
        }
        return best;
    }

    private static boolean isBetter(Instance instance, BackwardLabel candidate, BackwardLabel incumbent) {
        if (candidate.reducedCost() < incumbent.reducedCost() - EPS) {
            return true;
        }
        if (Math.abs(candidate.reducedCost() - incumbent.reducedCost()) > EPS) {
            return false;
        }
        double candidateCost = routeCost(instance, candidate.vertexIds());
        double incumbentCost = routeCost(instance, incumbent.vertexIds());
        if (candidateCost < incumbentCost - EPS) {
            return true;
        }
        if (Math.abs(candidateCost - incumbentCost) > EPS) {
            return false;
        }
        if (candidate.vertexIds().size() != incumbent.vertexIds().size()) {
            return candidate.vertexIds().size() < incumbent.vertexIds().size();
        }
        return lexicographicLess(candidate.vertexIds(), incumbent.vertexIds());
    }

    private static double routeCost(Instance instance, List<Integer> vertexIds) {
        double cost = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            cost += instance.travelCost(vertexIds.get(i).intValue(), vertexIds.get(i + 1).intValue());
        }
        return cost;
    }

    private static boolean lexicographicLess(List<Integer> left, List<Integer> right) {
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            int leftValue = left.get(i).intValue();
            int rightValue = right.get(i).intValue();
            if (leftValue != rightValue) {
                return leftValue < rightValue;
            }
        }
        return left.size() < right.size();
    }

    private static boolean sameRoute(BackwardLabel left, BackwardLabel right) {
        return left.vertexIds().equals(right.vertexIds());
    }

    public static boolean satisfiesBackwardPti(ReducedCostMatrices matrices) {
        return satisfiesBackwardPti(matrices, EPS);
    }

    public static boolean satisfiesBackwardPti(ReducedCostMatrices matrices, double tolerance) {
        Objects.requireNonNull(matrices, "matrices");
        if (tolerance < 0.0 || !Double.isFinite(tolerance)) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        Instance instance = matrices.instance();
        List<Vertex> vertices = instance.vertices();
        for (int requestId : instance.requestIds()) {
            int pickupVertexId = instance.request(requestId).pickupVertexId();
            for (Vertex from : vertices) {
                for (Vertex to : vertices) {
                    double direct = matrices.backwardArcReducedCost(from.id(), to.id());
                    double viaPickup = matrices.backwardArcReducedCost(from.id(), pickupVertexId)
                            + matrices.backwardArcReducedCost(pickupVertexId, to.id());
                    if (direct > viaPickup + tolerance) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static final class Result {
        private final BackwardLabel bestLabel;
        private final List<BackwardLabel> completeLabels;
        private final List<BackwardLabel> partialLabels;

        private Result(
                BackwardLabel bestLabel,
                List<BackwardLabel> completeLabels,
                List<BackwardLabel> partialLabels) {
            this.bestLabel = bestLabel;
            this.completeLabels = Collections.unmodifiableList(new ArrayList<BackwardLabel>(completeLabels));
            this.partialLabels = Collections.unmodifiableList(new ArrayList<BackwardLabel>(partialLabels));
        }

        public BackwardLabel bestLabel() {
            return bestLabel;
        }

        public List<Integer> bestRoute() {
            return bestLabel == null ? Collections.<Integer>emptyList() : bestLabel.vertexIds();
        }

        public double bestReducedCost() {
            return bestLabel == null ? Double.NaN : bestLabel.reducedCost();
        }

        public List<BackwardLabel> completeLabels() {
            return completeLabels;
        }

        public List<BackwardLabel> labels() {
            return completeLabels;
        }

        public List<BackwardLabel> partialLabels() {
            return partialLabels;
        }
    }
}
