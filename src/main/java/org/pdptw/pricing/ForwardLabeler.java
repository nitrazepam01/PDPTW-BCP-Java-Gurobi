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

public final class ForwardLabeler {
    private static final double EPS = 1.0e-9;

    private final boolean useStrongDominance;

    public ForwardLabeler() {
        this(false);
    }

    public ForwardLabeler(boolean useStrongDominance) {
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
        boolean dtiVerified = context.satisfiesForwardDti();
        if (useStrongDominance && !dtiVerified) {
            throw new IllegalStateException("Forward strong dominance requires DTI.");
        }
        ForwardLabel start = new ForwardLabel(
                instance.startDepotId(),
                0.0,
                Math.max(0.0, instance.vertex(instance.startDepotId()).readyTime()),
                0,
                0L,
                0L,
                context.initialSubsetRowCounts(),
                context.initialSetOutflowStates(),
                Collections.singletonList(Integer.valueOf(instance.startDepotId())));

        ArrayDeque<ForwardLabel> queue = new ArrayDeque<ForwardLabel>();
        ArrayList<ForwardLabel> partialLabels = new ArrayList<ForwardLabel>();
        ArrayList<ForwardLabel> completeLabels = new ArrayList<ForwardLabel>();
        queue.add(start);
        partialLabels.add(start);

        while (!queue.isEmpty()) {
            ForwardLabel label = queue.removeFirst();
            if (label.openMask() == 0L && label.completedMask() != 0L) {
                Optional<ForwardLabel> sink = LabelExtender.forwardExtend(context, label, instance.endDepotId());
                if (sink.isPresent()) {
                    addComplete(completeLabels, sink.get());
                }
            }

            for (int requestId : instance.requestIds()) {
                if (!BitSetOps.contains(label.openMask(), requestId)
                        && !BitSetOps.contains(label.completedMask(), requestId)) {
                    addPartial(queue, partialLabels, dtiVerified,
                            LabelExtender.forwardExtend(context, label, instance.request(requestId).pickupVertexId()));
                }
                if (BitSetOps.contains(label.openMask(), requestId)) {
                    addPartial(queue, partialLabels, dtiVerified,
                            LabelExtender.forwardExtend(context, label, instance.request(requestId).deliveryVertexId()));
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
        return new ForwardLabeler().solve(instance);
    }

    public static Result priceStatic(ReducedCostMatrices matrices) {
        return new ForwardLabeler().solve(matrices);
    }

    private void addPartial(
            ArrayDeque<ForwardLabel> queue,
            ArrayList<ForwardLabel> labels,
            boolean dtiVerified,
            Optional<ForwardLabel> candidate) {
        if (candidate.isEmpty()) {
            return;
        }
        ForwardLabel label = candidate.get();
        if (useStrongDominance && isDominated(labels, label, dtiVerified)) {
            return;
        }
        if (useStrongDominance) {
            labels.removeIf(existing -> Dominance.forwardStrongDominates(label, existing, dtiVerified)
                    && !sameRoute(label, existing));
        }
        labels.add(label);
        queue.addLast(label);
    }

    private static boolean isDominated(List<ForwardLabel> labels, ForwardLabel candidate, boolean dtiVerified) {
        for (ForwardLabel label : labels) {
            if (Dominance.forwardStrongDominates(label, candidate, dtiVerified) && !sameRoute(label, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void addComplete(ArrayList<ForwardLabel> labels, ForwardLabel candidate) {
        labels.add(candidate);
    }

    private static ForwardLabel bestLabel(Instance instance, List<ForwardLabel> labels) {
        ForwardLabel best = null;
        for (ForwardLabel label : labels) {
            if (best == null || isBetter(instance, label, best)) {
                best = label;
            }
        }
        return best;
    }

    private static boolean isBetter(Instance instance, ForwardLabel candidate, ForwardLabel incumbent) {
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

    private static boolean sameRoute(ForwardLabel left, ForwardLabel right) {
        return left.vertexIds().equals(right.vertexIds());
    }

    public static boolean satisfiesForwardDti(ReducedCostMatrices matrices) {
        return satisfiesForwardDti(matrices, EPS);
    }

    public static boolean satisfiesForwardDti(ReducedCostMatrices matrices, double tolerance) {
        Objects.requireNonNull(matrices, "matrices");
        if (tolerance < 0.0 || !Double.isFinite(tolerance)) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        Instance instance = matrices.instance();
        List<Vertex> vertices = instance.vertices();
        for (int requestId : instance.requestIds()) {
            int deliveryVertexId = instance.request(requestId).deliveryVertexId();
            for (Vertex from : vertices) {
                for (Vertex to : vertices) {
                    double direct = matrices.forwardArcReducedCost(from.id(), to.id());
                    double viaDelivery = matrices.forwardArcReducedCost(from.id(), deliveryVertexId)
                            + matrices.forwardArcReducedCost(deliveryVertexId, to.id());
                    if (direct > viaDelivery + tolerance) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static final class Result {
        private final ForwardLabel bestLabel;
        private final List<ForwardLabel> completeLabels;
        private final List<ForwardLabel> partialLabels;

        private Result(
                ForwardLabel bestLabel,
                List<ForwardLabel> completeLabels,
                List<ForwardLabel> partialLabels) {
            this.bestLabel = bestLabel;
            this.completeLabels = Collections.unmodifiableList(new ArrayList<ForwardLabel>(completeLabels));
            this.partialLabels = Collections.unmodifiableList(new ArrayList<ForwardLabel>(partialLabels));
        }

        public ForwardLabel bestLabel() {
            return bestLabel;
        }

        public List<Integer> bestRoute() {
            return bestLabel == null ? Collections.<Integer>emptyList() : bestLabel.vertexIds();
        }

        public double bestReducedCost() {
            return bestLabel == null ? Double.NaN : bestLabel.reducedCost();
        }

        public List<ForwardLabel> completeLabels() {
            return completeLabels;
        }

        public List<ForwardLabel> labels() {
            return completeLabels;
        }

        public List<ForwardLabel> partialLabels() {
            return partialLabels;
        }
    }
}
