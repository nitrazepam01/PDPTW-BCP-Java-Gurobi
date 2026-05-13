package org.pdptw.pricing;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

public final class BidirectionalDynamicPricingSolver implements PricingSolver {
    private static final double EPS = 1.0e-9;

    private final double tolerance;
    private final BidirectionalMerger merger;

    public BidirectionalDynamicPricingSolver() {
        this(1.0e-7);
    }

    public BidirectionalDynamicPricingSolver(double tolerance) {
        this(tolerance, new BidirectionalMerger());
    }

    BidirectionalDynamicPricingSolver(double tolerance, BidirectionalMerger merger) {
        this.tolerance = PricingAdapterSupport.requireTolerance(tolerance);
        this.merger = Objects.requireNonNull(merger, "merger");
    }

    public Result solve(Instance instance) {
        return solve(ReducedCostMatrices.fromInstanceDuals(instance));
    }

    public Result solve(ReducedCostMatrices matrices) {
        Objects.requireNonNull(matrices, "matrices");
        return solve(PricingContext.noCuts(matrices));
    }

    public Result solve(PricingContext context) {
        Objects.requireNonNull(context, "context");
        return new DynamicSearch(context, merger).solve();
    }

    @Override
    public PricingResult price(ReducedCostMatrices matrices) {
        return price(PricingContext.noCuts(matrices));
    }

    @Override
    public PricingResult price(PricingContext context) {
        Result result = solve(context);
        List<List<Integer>> routes = new ArrayList<List<Integer>>();
        for (BidirectionalMerger.MergeResult merge : result.merges()) {
            routes.add(merge.route());
        }
        return PricingAdapterSupport.exactFromRoutes(
                "bidir_dynamic",
                context,
                routes,
                result.bestReducedCost(),
                tolerance,
                PricingResult.Stats.ofDynamic(
                        result.forwardLabelCount(),
                        result.backwardLabelCount(),
                        result.forwardCompleteLabels().size(),
                        result.backwardCompleteLabels().size(),
                        result.forwardCompleteLabels().size() + result.backwardCompleteLabels().size(),
                        result.merges().size(),
                        result.dominatedLabels(),
                        result.finalSnapshot(),
                        result.dominanceCleanupPrunedLabels(),
                        result.dominanceCleanupTriggers()));
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

    private static int lexicographicCompare(List<Integer> left, List<Integer> right) {
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            int leftValue = left.get(i).intValue();
            int rightValue = right.get(i).intValue();
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static boolean lexicographicLess(List<Integer> left, List<Integer> right) {
        return lexicographicCompare(left, right) < 0;
    }

    private static int routeKeyCompare(List<Integer> left, List<Integer> right) {
        RouteKeyCursor leftCursor = new RouteKeyCursor(left);
        RouteKeyCursor rightCursor = new RouteKeyCursor(right);
        while (leftCursor.hasNext() && rightCursor.hasNext()) {
            int comparison = Character.compare(leftCursor.next(), rightCursor.next());
            if (comparison != 0) {
                return comparison;
            }
        }
        if (leftCursor.hasNext()) {
            return 1;
        }
        if (rightCursor.hasNext()) {
            return -1;
        }
        return 0;
    }

    private static List<ForwardLabel> allForwardLabels(
            List<ForwardLabel> partialLabels,
            List<ForwardLabel> completeLabels,
            List<ForwardLabel> prunedLabels) {
        ArrayList<ForwardLabel> labels = new ArrayList<ForwardLabel>(partialLabels);
        labels.addAll(completeLabels);
        labels.addAll(prunedLabels);
        return labels;
    }

    private static List<BackwardLabel> allBackwardLabels(
            List<BackwardLabel> partialLabels,
            List<BackwardLabel> completeLabels,
            List<BackwardLabel> prunedLabels) {
        ArrayList<BackwardLabel> labels = new ArrayList<BackwardLabel>(partialLabels);
        labels.addAll(completeLabels);
        labels.addAll(prunedLabels);
        return labels;
    }

    private static final class DynamicSearch {
        private final PricingContext context;
        private final Instance instance;
        private final BidirectionalMerger merger;
        private final PriorityQueue<ForwardLabel> forwardQueue;
        private final PriorityQueue<BackwardLabel> backwardQueue;
        private final ArrayList<ForwardLabel> forwardPartialLabels = new ArrayList<ForwardLabel>();
        private final ArrayList<ForwardLabel> forwardCompleteLabels = new ArrayList<ForwardLabel>();
        private final ArrayList<ForwardLabel> forwardPrunedLabels = new ArrayList<ForwardLabel>();
        private final ArrayList<BackwardLabel> backwardPartialLabels = new ArrayList<BackwardLabel>();
        private final ArrayList<BackwardLabel> backwardCompleteLabels = new ArrayList<BackwardLabel>();
        private final ArrayList<BackwardLabel> backwardPrunedLabels = new ArrayList<BackwardLabel>();
        private final DynamicHalfwayController controller;
        private final boolean forwardDominanceEnabled;
        private final boolean backwardDominanceEnabled;
        private int dominatedLabels;
        private int dominanceCleanupPrunedLabels;
        private int dominanceCleanupTriggers;

        private DynamicSearch(PricingContext context, BidirectionalMerger merger) {
            this.context = Objects.requireNonNull(context, "context");
            this.instance = context.instance();
            this.merger = Objects.requireNonNull(merger, "merger");
            this.forwardQueue = new PriorityQueue<ForwardLabel>(
                    Comparator.comparingDouble(ForwardLabel::time)
                            .thenComparing((left, right) -> routeKeyCompare(
                                    left.vertexIds(),
                                    right.vertexIds())));
            this.backwardQueue = new PriorityQueue<BackwardLabel>(
                    Comparator.comparingDouble(BackwardLabel::time)
                            .reversed()
                            .thenComparing((left, right) -> routeKeyCompare(
                                    left.vertexIds(),
                                    right.vertexIds())));

            ForwardLabel start = new ForwardLabel(
                    instance.startDepotId(),
                    0.0,
                    Math.max(0.0, instance.vertex(instance.startDepotId()).readyTime()),
                    0,
                    0L,
                    0L,
                    context.initialSubsetRowCounts(),
                    context.initialSetOutflowStates(),
                    List.of(Integer.valueOf(instance.startDepotId())));
            BackwardLabel sink = new BackwardLabel(
                    instance.endDepotId(),
                    0.0,
                    instance.vertex(instance.endDepotId()).dueTime(),
                    0,
                    0L,
                    0L,
                    context.initialSubsetRowCounts(),
                    context.initialSetOutflowStates(),
                    List.of(Integer.valueOf(instance.endDepotId())));

            addForwardPartial(start);
            addBackwardPartial(sink);
            this.forwardDominanceEnabled = context.satisfiesForwardDti()
                    && !context.hasSubsetRowCuts();
            this.backwardDominanceEnabled = context.satisfiesBackwardPti()
                    && !context.hasSubsetRowCuts();
            this.controller = new DynamicHalfwayController(
                    instance.vertex(instance.startDepotId()).readyTime(),
                    instance.vertex(instance.endDepotId()).dueTime(),
                    forwardQueue.size(),
                    backwardQueue.size());
        }

        private Result solve() {
            while (!controller.terminated()) {
                DynamicHalfwayController.Direction direction = controller.nextDirection();
                if (direction == DynamicHalfwayController.Direction.FORWARD) {
                    processForward();
                } else {
                    processBackward();
                }
            }

            Map<Integer, List<BackwardLabel>> backwardByFirstVertex =
                    new LinkedHashMap<Integer, List<BackwardLabel>>();
            for (BackwardLabel backward : allBackwardLabels(
                    backwardPartialLabels,
                    backwardCompleteLabels,
                    backwardPrunedLabels)) {
                backwardByFirstVertex.computeIfAbsent(
                        backward.firstVertexId(),
                        ignored -> new ArrayList<BackwardLabel>())
                        .add(backward);
            }
            ArrayList<BidirectionalMerger.MergeResult> merges = new ArrayList<BidirectionalMerger.MergeResult>();
            for (ForwardLabel forward : allForwardLabels(
                    forwardPartialLabels,
                    forwardCompleteLabels,
                    forwardPrunedLabels)) {
                List<BackwardLabel> candidates = backwardByFirstVertex.get(forward.lastVertexId());
                if (candidates == null) {
                    continue;
                }
                for (BackwardLabel backward : candidates) {
                    merger.merge(context, forward, backward).ifPresent(merges::add);
                }
            }
            return new Result(
                    bestMerge(context, merges),
                    merges,
                    forwardPartialLabels,
                    forwardCompleteLabels,
                    forwardPrunedLabels,
                    backwardPartialLabels,
                    backwardCompleteLabels,
                    backwardPrunedLabels,
                    controller.snapshot(),
                    controller.decisions(),
                    dominatedLabels,
                    dominanceCleanupPrunedLabels,
                    dominanceCleanupTriggers);
        }

        private void processForward() {
            ForwardLabel label = forwardQueue.remove();
            int generatedQueued = 0;
            int generatedComplete = 0;
            if (label.time() <= controller.snapshot().forwardUpperBound() + EPS) {
                if (label.openMask() == 0L && label.completedMask() != 0L) {
                    generatedComplete += addForwardComplete(LabelExtender.forwardExtend(
                            context,
                            label,
                            instance.endDepotId()));
                }
                for (int requestId : instance.requestIds()) {
                    if (!BitSetOps.contains(label.openMask(), requestId)
                            && !BitSetOps.contains(label.completedMask(), requestId)) {
                        generatedQueued += addForwardCandidate(LabelExtender.forwardExtend(
                                context,
                                label,
                                instance.request(requestId).pickupVertexId()));
                    }
                    if (BitSetOps.contains(label.openMask(), requestId)) {
                        generatedQueued += addForwardCandidate(LabelExtender.forwardExtend(
                                context,
                                label,
                                instance.request(requestId).deliveryVertexId()));
                    }
                }
            }
            DynamicHalfwayController.Decision decision = controller.process(
                    DynamicHalfwayController.Direction.FORWARD,
                    label.time(),
                    generatedQueued + generatedComplete,
                    generatedQueued,
                    0,
                    0);
            triggerDominanceCleanupIfBoundsChanged(decision);
        }

        private void processBackward() {
            BackwardLabel label = backwardQueue.remove();
            int generatedQueued = 0;
            int generatedComplete = 0;
            if (label.time() > controller.snapshot().backwardLowerBound() + EPS) {
                if (label.openMask() == 0L && label.completedMask() != 0L) {
                    generatedComplete += addBackwardComplete(LabelExtender.backwardExtend(
                            context,
                            label,
                            instance.startDepotId()));
                }
                for (int requestId : instance.requestIds()) {
                    if (!BitSetOps.contains(label.openMask(), requestId)
                            && !BitSetOps.contains(label.completedMask(), requestId)) {
                        generatedQueued += addBackwardCandidate(LabelExtender.backwardExtend(
                                context,
                                label,
                                instance.request(requestId).deliveryVertexId()));
                    }
                    if (BitSetOps.contains(label.openMask(), requestId)) {
                        generatedQueued += addBackwardCandidate(LabelExtender.backwardExtend(
                                context,
                                label,
                                instance.request(requestId).pickupVertexId()));
                    }
                }
            }
            DynamicHalfwayController.Decision decision = controller.process(
                    DynamicHalfwayController.Direction.BACKWARD,
                    label.time(),
                    0,
                    0,
                    generatedQueued + generatedComplete,
                    generatedQueued);
            triggerDominanceCleanupIfBoundsChanged(decision);
        }

        private void triggerDominanceCleanupIfBoundsChanged(DynamicHalfwayController.Decision decision) {
            if (boundsChanged(decision)) {
                dominanceCleanupTriggers++;
                dominanceCleanupPrunedLabels += pruneDominatedQueuedLabels();
            }
        }

        private static boolean boundsChanged(DynamicHalfwayController.Decision decision) {
            return Math.abs(decision.before().backwardLowerBound()
                    - decision.after().backwardLowerBound()) > EPS
                    || Math.abs(decision.before().forwardUpperBound()
                    - decision.after().forwardUpperBound()) > EPS;
        }

        private int pruneDominatedQueuedLabels() {
            int pruned = 0;
            if (forwardDominanceEnabled) {
                pruned += pruneDominatedForwardQueue();
            }
            if (backwardDominanceEnabled) {
                pruned += pruneDominatedBackwardQueue();
            }
            return pruned;
        }

        private int pruneDominatedForwardQueue() {
            ArrayList<ForwardLabel> pruned = new ArrayList<ForwardLabel>();
            for (ForwardLabel label : forwardQueue) {
                if (isForwardCleanupDominated(label)) {
                    pruned.add(label);
                }
            }
            for (ForwardLabel label : pruned) {
                forwardQueue.remove(label);
            }
            if (!pruned.isEmpty()) {
                for (ForwardLabel label : pruned) {
                    forwardPartialLabels.remove(label);
                    forwardPrunedLabels.add(label);
                }
                controller.pruneUnprocessed(
                        DynamicHalfwayController.Direction.FORWARD,
                        pruned.size());
                dominatedLabels += pruned.size();
            }
            return pruned.size();
        }

        private int pruneDominatedBackwardQueue() {
            ArrayList<BackwardLabel> pruned = new ArrayList<BackwardLabel>();
            for (BackwardLabel label : backwardQueue) {
                if (isBackwardCleanupDominated(label)) {
                    pruned.add(label);
                }
            }
            for (BackwardLabel label : pruned) {
                backwardQueue.remove(label);
            }
            if (!pruned.isEmpty()) {
                for (BackwardLabel label : pruned) {
                    backwardPartialLabels.remove(label);
                    backwardPrunedLabels.add(label);
                }
                controller.pruneUnprocessed(
                        DynamicHalfwayController.Direction.BACKWARD,
                        pruned.size());
                dominatedLabels += pruned.size();
            }
            return pruned.size();
        }

        private int addForwardComplete(Optional<ForwardLabel> candidate) {
            if (candidate.isEmpty()) {
                return 0;
            }
            forwardCompleteLabels.add(candidate.get());
            return 1;
        }

        private int addBackwardComplete(Optional<BackwardLabel> candidate) {
            if (candidate.isEmpty()) {
                return 0;
            }
            backwardCompleteLabels.add(candidate.get());
            return 1;
        }

        private int addForwardCandidate(Optional<ForwardLabel> candidate) {
            if (candidate.isEmpty()) {
                return 0;
            }
            ForwardLabel label = candidate.get();
            if (forwardDominanceEnabled && isForwardDominated(label)) {
                dominatedLabels++;
                return 0;
            }
            addForwardPartial(label);
            return 1;
        }

        private int addBackwardCandidate(Optional<BackwardLabel> candidate) {
            if (candidate.isEmpty()) {
                return 0;
            }
            BackwardLabel label = candidate.get();
            if (backwardDominanceEnabled && isBackwardDominated(label)) {
                dominatedLabels++;
                return 0;
            }
            addBackwardPartial(label);
            return 1;
        }

        private boolean isForwardDominated(ForwardLabel candidate) {
            for (ForwardLabel existing : forwardPartialLabels) {
                if (Dominance.forwardStrongDominates(existing, candidate, forwardDominanceEnabled)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isBackwardDominated(BackwardLabel candidate) {
            for (BackwardLabel existing : backwardPartialLabels) {
                if (Dominance.backwardStrongDominates(existing, candidate, backwardDominanceEnabled)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isForwardCleanupDominated(ForwardLabel candidate) {
            for (ForwardLabel existing : forwardPartialLabels) {
                if (existing == candidate || sameRoute(existing.vertexIds(), candidate.vertexIds())) {
                    continue;
                }
                if (forwardDominatesForCleanup(existing, candidate)) {
                    return true;
                }
            }
            return false;
        }

        private boolean forwardDominatesForCleanup(ForwardLabel existing, ForwardLabel candidate) {
            if (!Dominance.forwardStrongDominates(existing, candidate, forwardDominanceEnabled)) {
                return false;
            }
            if (!Dominance.forwardStrongDominates(candidate, existing, forwardDominanceEnabled)) {
                return true;
            }
            return routeKeyCompare(existing.vertexIds(), candidate.vertexIds()) < 0;
        }

        private boolean isBackwardCleanupDominated(BackwardLabel candidate) {
            for (BackwardLabel existing : backwardPartialLabels) {
                if (existing == candidate || sameRoute(existing.vertexIds(), candidate.vertexIds())) {
                    continue;
                }
                if (backwardDominatesForCleanup(existing, candidate)) {
                    return true;
                }
            }
            return false;
        }

        private boolean backwardDominatesForCleanup(BackwardLabel existing, BackwardLabel candidate) {
            if (!Dominance.backwardStrongDominates(existing, candidate, backwardDominanceEnabled)) {
                return false;
            }
            if (!Dominance.backwardStrongDominates(candidate, existing, backwardDominanceEnabled)) {
                return true;
            }
            return routeKeyCompare(existing.vertexIds(), candidate.vertexIds()) < 0;
        }

        private static boolean sameRoute(List<Integer> left, List<Integer> right) {
            return left.equals(right);
        }

        private void addForwardPartial(ForwardLabel label) {
            forwardPartialLabels.add(label);
            forwardQueue.add(label);
        }

        private void addBackwardPartial(BackwardLabel label) {
            backwardPartialLabels.add(label);
            backwardQueue.add(label);
        }
    }

    private static final class RouteKeyCursor {
        private final List<Integer> vertexIds;
        private int vertexIndex;
        private boolean separatorPending;
        private boolean tokenStarted;
        private boolean signPending;
        private long tokenAbsValue;
        private long divisor;

        private RouteKeyCursor(List<Integer> vertexIds) {
            this.vertexIds = Objects.requireNonNull(vertexIds, "vertexIds");
        }

        private boolean hasNext() {
            return separatorPending || vertexIndex < vertexIds.size();
        }

        private char next() {
            if (separatorPending) {
                separatorPending = false;
                return '-';
            }
            if (!tokenStarted) {
                int value = vertexIds.get(vertexIndex).intValue();
                tokenStarted = true;
                signPending = value < 0;
                tokenAbsValue = Math.abs((long) value);
                divisor = highestDivisor(tokenAbsValue);
            }
            if (signPending) {
                signPending = false;
                return '-';
            }
            char digit = (char) ('0' + (int) ((tokenAbsValue / divisor) % 10L));
            divisor /= 10L;
            if (divisor == 0L) {
                tokenStarted = false;
                vertexIndex++;
                separatorPending = vertexIndex < vertexIds.size();
            }
            return digit;
        }

        private static long highestDivisor(long value) {
            long result = 1L;
            while (value >= 10L) {
                value /= 10L;
                result *= 10L;
            }
            return result;
        }
    }

    public static final class Result {
        private final BidirectionalMerger.MergeResult bestMerge;
        private final List<BidirectionalMerger.MergeResult> merges;
        private final List<ForwardLabel> forwardPartialLabels;
        private final List<ForwardLabel> forwardCompleteLabels;
        private final List<ForwardLabel> forwardPrunedLabels;
        private final List<BackwardLabel> backwardPartialLabels;
        private final List<BackwardLabel> backwardCompleteLabels;
        private final List<BackwardLabel> backwardPrunedLabels;
        private final DynamicHalfwayController.Snapshot finalSnapshot;
        private final List<DynamicHalfwayController.Decision> directionDecisions;
        private final int dominatedLabels;
        private final int dominanceCleanupPrunedLabels;
        private final int dominanceCleanupTriggers;

        private Result(
                BidirectionalMerger.MergeResult bestMerge,
                List<BidirectionalMerger.MergeResult> merges,
                List<ForwardLabel> forwardPartialLabels,
                List<ForwardLabel> forwardCompleteLabels,
                List<ForwardLabel> forwardPrunedLabels,
                List<BackwardLabel> backwardPartialLabels,
                List<BackwardLabel> backwardCompleteLabels,
                List<BackwardLabel> backwardPrunedLabels,
                DynamicHalfwayController.Snapshot finalSnapshot,
                List<DynamicHalfwayController.Decision> directionDecisions,
                int dominatedLabels,
                int dominanceCleanupPrunedLabels,
                int dominanceCleanupTriggers) {
            this.bestMerge = bestMerge;
            this.merges = Collections.unmodifiableList(new ArrayList<BidirectionalMerger.MergeResult>(merges));
            this.forwardPartialLabels = Collections.unmodifiableList(new ArrayList<ForwardLabel>(forwardPartialLabels));
            this.forwardCompleteLabels = Collections.unmodifiableList(new ArrayList<ForwardLabel>(forwardCompleteLabels));
            this.forwardPrunedLabels = Collections.unmodifiableList(new ArrayList<ForwardLabel>(forwardPrunedLabels));
            this.backwardPartialLabels = Collections.unmodifiableList(new ArrayList<BackwardLabel>(backwardPartialLabels));
            this.backwardCompleteLabels = Collections.unmodifiableList(new ArrayList<BackwardLabel>(backwardCompleteLabels));
            this.backwardPrunedLabels = Collections.unmodifiableList(new ArrayList<BackwardLabel>(backwardPrunedLabels));
            this.finalSnapshot = Objects.requireNonNull(finalSnapshot, "finalSnapshot");
            this.directionDecisions = List.copyOf(directionDecisions);
            if (dominatedLabels < 0) {
                throw new IllegalArgumentException("dominatedLabels must be non-negative");
            }
            if (dominanceCleanupPrunedLabels < 0) {
                throw new IllegalArgumentException("dominanceCleanupPrunedLabels must be non-negative");
            }
            if (dominanceCleanupTriggers < 0) {
                throw new IllegalArgumentException("dominanceCleanupTriggers must be non-negative");
            }
            this.dominatedLabels = dominatedLabels;
            this.dominanceCleanupPrunedLabels = dominanceCleanupPrunedLabels;
            this.dominanceCleanupTriggers = dominanceCleanupTriggers;
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

        public List<ForwardLabel> forwardPartialLabels() {
            return forwardPartialLabels;
        }

        public List<ForwardLabel> forwardCompleteLabels() {
            return forwardCompleteLabels;
        }

        public List<ForwardLabel> forwardPrunedLabels() {
            return forwardPrunedLabels;
        }

        public List<BackwardLabel> backwardPartialLabels() {
            return backwardPartialLabels;
        }

        public List<BackwardLabel> backwardCompleteLabels() {
            return backwardCompleteLabels;
        }

        public List<BackwardLabel> backwardPrunedLabels() {
            return backwardPrunedLabels;
        }

        public int forwardLabelCount() {
            return forwardPartialLabels.size() + forwardCompleteLabels.size() + forwardPrunedLabels.size();
        }

        public int backwardLabelCount() {
            return backwardPartialLabels.size() + backwardCompleteLabels.size() + backwardPrunedLabels.size();
        }

        public DynamicHalfwayController.Snapshot finalSnapshot() {
            return finalSnapshot;
        }

        public List<DynamicHalfwayController.Decision> directionDecisions() {
            return directionDecisions;
        }

        public int dominatedLabels() {
            return dominatedLabels;
        }

        public int dominanceCleanupPrunedLabels() {
            return dominanceCleanupPrunedLabels;
        }

        public int dominanceCleanupTriggers() {
            return dominanceCleanupTriggers;
        }
    }
}
