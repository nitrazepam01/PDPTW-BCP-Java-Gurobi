package org.pdptw.pricing;

import org.pdptw.core.Instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BidirectionalPricingSolver {
    private static final double EPS = 1.0e-9;

    private final BidirectionalMerger merger;

    public BidirectionalPricingSolver() {
        this(new BidirectionalMerger());
    }

    public BidirectionalPricingSolver(BidirectionalMerger merger) {
        this.merger = Objects.requireNonNull(merger, "merger");
    }

    public Result solve(Instance instance) {
        return solve(ReducedCostMatrices.fromInstanceDuals(instance));
    }

    public Result solve(ReducedCostMatrices matrices) {
        return solve(PricingContext.noCuts(matrices));
    }

    public Result solve(PricingContext context) {
        Objects.requireNonNull(context, "context");
        ForwardLabeler.Result forward = new ForwardLabeler().solve(context);
        BackwardLabeler.Result backward = new BackwardLabeler().solve(context);
        List<BidirectionalMerger.MergeResult> merges = mergeAll(context, forward, backward);
        return new Result(bestMerge(context, merges), merges, forward, backward);
    }

    public Result price(Instance instance) {
        return solve(instance);
    }

    public Result price(ReducedCostMatrices matrices) {
        return solve(matrices);
    }

    public static Result priceStatic(Instance instance) {
        return new BidirectionalPricingSolver().solve(instance);
    }

    public static Result priceStatic(ReducedCostMatrices matrices) {
        return new BidirectionalPricingSolver().solve(matrices);
    }

    private List<BidirectionalMerger.MergeResult> mergeAll(
            PricingContext context,
            ForwardLabeler.Result forward,
            BackwardLabeler.Result backward) {
        Map<Integer, List<BackwardLabel>> backwardByFirstVertex = new LinkedHashMap<Integer, List<BackwardLabel>>();
        for (BackwardLabel label : allBackwardLabels(backward)) {
            backwardByFirstVertex.computeIfAbsent(label.firstVertexId(), ignored -> new ArrayList<BackwardLabel>())
                    .add(label);
        }

        ArrayList<BidirectionalMerger.MergeResult> merges = new ArrayList<BidirectionalMerger.MergeResult>();
        for (ForwardLabel forwardLabel : allForwardLabels(forward)) {
            List<BackwardLabel> candidates = backwardByFirstVertex.get(forwardLabel.lastVertexId());
            if (candidates == null) {
                continue;
            }
            for (BackwardLabel backwardLabel : candidates) {
                merger.merge(context, forwardLabel, backwardLabel).ifPresent(merges::add);
            }
        }
        return Collections.unmodifiableList(merges);
    }

    private static List<ForwardLabel> allForwardLabels(ForwardLabeler.Result result) {
        ArrayList<ForwardLabel> labels = new ArrayList<ForwardLabel>(result.partialLabels());
        labels.addAll(result.completeLabels());
        return labels;
    }

    private static List<BackwardLabel> allBackwardLabels(BackwardLabeler.Result result) {
        ArrayList<BackwardLabel> labels = new ArrayList<BackwardLabel>(result.partialLabels());
        labels.addAll(result.completeLabels());
        return labels;
    }

    private static BidirectionalMerger.MergeResult bestMerge(
            PricingContext context,
            List<BidirectionalMerger.MergeResult> merges) {
        BidirectionalMerger.MergeResult best = null;
        for (BidirectionalMerger.MergeResult candidate : merges) {
            if (best == null || isBetter(context, candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isBetter(
            PricingContext context,
            BidirectionalMerger.MergeResult candidate,
            BidirectionalMerger.MergeResult incumbent) {
        if (candidate.mergedReducedCost() < incumbent.mergedReducedCost() - EPS) {
            return true;
        }
        if (Math.abs(candidate.mergedReducedCost() - incumbent.mergedReducedCost()) > EPS) {
            return false;
        }
        double candidateCost = context.routeCost(candidate.route());
        double incumbentCost = context.routeCost(incumbent.route());
        if (candidateCost < incumbentCost - EPS) {
            return true;
        }
        if (Math.abs(candidateCost - incumbentCost) > EPS) {
            return false;
        }
        if (candidate.route().size() != incumbent.route().size()) {
            return candidate.route().size() < incumbent.route().size();
        }
        return lexicographicLess(candidate.route(), incumbent.route());
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

    public static final class Result {
        private final BidirectionalMerger.MergeResult bestMerge;
        private final List<BidirectionalMerger.MergeResult> merges;
        private final ForwardLabeler.Result forwardResult;
        private final BackwardLabeler.Result backwardResult;

        private Result(
                BidirectionalMerger.MergeResult bestMerge,
                List<BidirectionalMerger.MergeResult> merges,
                ForwardLabeler.Result forwardResult,
                BackwardLabeler.Result backwardResult) {
            this.bestMerge = bestMerge;
            this.merges = Collections.unmodifiableList(new ArrayList<BidirectionalMerger.MergeResult>(merges));
            this.forwardResult = Objects.requireNonNull(forwardResult, "forwardResult");
            this.backwardResult = Objects.requireNonNull(backwardResult, "backwardResult");
        }

        public BidirectionalMerger.MergeResult bestMerge() {
            return bestMerge;
        }

        public List<Integer> bestRoute() {
            return bestMerge == null ? Collections.<Integer>emptyList() : bestMerge.route();
        }

        public double bestReducedCost() {
            return bestMerge == null ? Double.NaN : bestMerge.mergedReducedCost();
        }

        public List<BidirectionalMerger.MergeResult> merges() {
            return merges;
        }

        public ForwardLabeler.Result forwardResult() {
            return forwardResult;
        }

        public BackwardLabeler.Result backwardResult() {
            return backwardResult;
        }
    }
}
