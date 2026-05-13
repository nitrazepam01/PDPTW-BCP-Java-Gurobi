package org.pdptw.branch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class SetOutflowBrancher {
    public static final double DEFAULT_TOLERANCE = 1.0e-7;
    public static final int DEFAULT_MAX_REQUEST_SET_SIZE = Integer.MAX_VALUE;

    private final double tolerance;
    private final int maxRequestSetSize;

    public SetOutflowBrancher() {
        this(DEFAULT_TOLERANCE, DEFAULT_MAX_REQUEST_SET_SIZE);
    }

    public SetOutflowBrancher(double tolerance) {
        this(tolerance, DEFAULT_MAX_REQUEST_SET_SIZE);
    }

    public SetOutflowBrancher(double tolerance, int maxRequestSetSize) {
        this.tolerance = requireTolerance(tolerance);
        if (maxRequestSetSize < 1) {
            throw new IllegalArgumentException("maxRequestSetSize must be positive: " + maxRequestSetSize);
        }
        this.maxRequestSetSize = maxRequestSetSize;
    }

    public Optional<BranchDecision> branch(
            BranchNode parent,
            List<BranchDecision.RouteValue> solution) {
        Objects.requireNonNull(parent, "parent");
        List<BranchDecision.RouteValue> values = BranchDecision.copySolution(solution);
        for (Set<Integer> candidate : candidateRequestSets(values, maxRequestSetSize)) {
            Optional<BranchDecision> decision = branchOnRequestSet(parent, values, candidate);
            if (decision.isPresent()) {
                return decision;
            }
        }
        return Optional.empty();
    }

    public Optional<BranchDecision> branchOnRequestSet(
            BranchNode parent,
            List<BranchDecision.RouteValue> solution,
            Collection<Integer> requestSet) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(solution, "solution");
        double value = SetOutflowConstraint.value(solution, requestSet);
        if (!VehicleCountBrancher.isFractional(value, tolerance)) {
            return Optional.empty();
        }
        int floor = (int) Math.floor(value);
        int ceil = (int) Math.ceil(value);
        SetOutflowConstraint left = SetOutflowConstraint.lessOrEqual(requestSet, floor);
        SetOutflowConstraint right = SetOutflowConstraint.greaterOrEqual(requestSet, ceil);
        return Optional.of(new BranchDecision(
                BranchConstraint.Type.SET_OUTFLOW,
                value,
                parent.child(left),
                parent.child(right)));
    }

    private static List<Set<Integer>> candidateRequestSets(
            List<BranchDecision.RouteValue> solution,
            int maxRequestSetSize) {
        TreeSet<Integer> universe = new TreeSet<Integer>();
        for (BranchDecision.RouteValue value : solution) {
            universe.addAll(value.servedRequests());
        }
        ArrayList<Integer> requests = new ArrayList<Integer>(universe);
        ArrayList<Set<Integer>> candidates = new ArrayList<Set<Integer>>();
        for (int requestId : requests) {
            candidates.add(Set.of(Integer.valueOf(requestId)));
        }
        int maxSize = Math.min(maxRequestSetSize, Math.max(1, requests.size() - 1));
        for (int size = 2; size <= maxSize; size++) {
            addCombinations(requests, size, 0, new TreeSet<Integer>(), candidates);
        }
        return List.copyOf(candidates);
    }

    private static void addCombinations(
            List<Integer> requests,
            int targetSize,
            int startIndex,
            TreeSet<Integer> current,
            ArrayList<Set<Integer>> candidates) {
        if (current.size() == targetSize) {
            candidates.add(Set.copyOf(current));
            return;
        }
        int remaining = targetSize - current.size();
        for (int index = startIndex; index <= requests.size() - remaining; index++) {
            Integer requestId = requests.get(index);
            current.add(requestId);
            addCombinations(requests, targetSize, index + 1, current, candidates);
            current.remove(requestId);
        }
    }

    private static double requireTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        return tolerance;
    }
}
