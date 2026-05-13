package org.pdptw.branch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class BranchDecision {
    private final BranchConstraint.Type type;
    private final double branchingValue;
    private final BranchNode leftChild;
    private final BranchNode rightChild;

    public BranchDecision(
            BranchConstraint.Type type,
            double branchingValue,
            BranchNode leftChild,
            BranchNode rightChild) {
        this.type = Objects.requireNonNull(type, "type");
        this.branchingValue = requireFinite(branchingValue, "branchingValue");
        this.leftChild = Objects.requireNonNull(leftChild, "leftChild");
        this.rightChild = Objects.requireNonNull(rightChild, "rightChild");
        if (leftChild.isRoot() || rightChild.isRoot()) {
            throw new IllegalArgumentException("branch children must inherit constraints");
        }
    }

    public BranchConstraint.Type type() {
        return type;
    }

    public double branchingValue() {
        return branchingValue;
    }

    public BranchNode leftChild() {
        return leftChild;
    }

    public BranchNode rightChild() {
        return rightChild;
    }

    public BranchConstraint leftConstraint() {
        return leftChild.inheritedBy();
    }

    public BranchConstraint rightConstraint() {
        return rightChild.inheritedBy();
    }

    public List<BranchNode> children() {
        return List.of(leftChild, rightChild);
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    public static final class RouteValue {
        private final String routeId;
        private final double lambda;
        private final double fleetCoefficient;
        private final Set<Integer> servedRequests;
        private final List<Integer> requestVisitSequence;

        private RouteValue(
                String routeId,
                double lambda,
                double fleetCoefficient,
                Collection<Integer> servedRequests,
                Collection<Integer> requestVisitSequence) {
            this.routeId = requireNonBlank(routeId, "routeId");
            this.lambda = requireFinite(lambda, "lambda");
            this.fleetCoefficient = requireFinite(fleetCoefficient, "fleetCoefficient");
            this.servedRequests = Collections.unmodifiableSet(normalizedRequests(servedRequests));
            this.requestVisitSequence = Collections.unmodifiableList(normalizedVisitSequence(requestVisitSequence));
        }

        public static RouteValue of(String routeId, double lambda, Collection<Integer> servedRequests) {
            return new RouteValue(routeId, lambda, 1.0, servedRequests, List.of());
        }

        public static RouteValue of(
                String routeId,
                double lambda,
                double fleetCoefficient,
                Collection<Integer> servedRequests) {
            return new RouteValue(routeId, lambda, fleetCoefficient, servedRequests, List.of());
        }

        public static RouteValue withRequestVisits(
                String routeId,
                double lambda,
                Collection<Integer> requestVisitSequence) {
            return withRequestVisits(routeId, lambda, 1.0, requestVisitSequence);
        }

        public static RouteValue withRequestVisits(
                String routeId,
                double lambda,
                double fleetCoefficient,
                Collection<Integer> requestVisitSequence) {
            List<Integer> visits = normalizedVisitSequence(requestVisitSequence);
            return new RouteValue(routeId, lambda, fleetCoefficient, visits, visits);
        }

        public String routeId() {
            return routeId;
        }

        public double lambda() {
            return lambda;
        }

        public double fleetCoefficient() {
            return fleetCoefficient;
        }

        public Set<Integer> servedRequests() {
            return servedRequests;
        }

        public List<Integer> requestVisitSequence() {
            return requestVisitSequence;
        }

        public boolean hasRequestVisitSequence() {
            return !requestVisitSequence.isEmpty();
        }

        private static Set<Integer> normalizedRequests(Collection<Integer> requestIds) {
            Objects.requireNonNull(requestIds, "requestIds");
            TreeSet<Integer> sorted = new TreeSet<Integer>();
            for (Integer requestId : requestIds) {
                if (requestId == null || requestId.intValue() <= 0) {
                    throw new IllegalArgumentException("request ids must be positive: " + requestId);
                }
                sorted.add(requestId);
            }
            return new LinkedHashSet<Integer>(sorted);
        }

        private static List<Integer> normalizedVisitSequence(Collection<Integer> requestIds) {
            Objects.requireNonNull(requestIds, "requestIds");
            ArrayList<Integer> visits = new ArrayList<Integer>();
            for (Integer requestId : requestIds) {
                if (requestId == null || requestId.intValue() <= 0) {
                    throw new IllegalArgumentException("request visit ids must be positive: " + requestId);
                }
                visits.add(requestId);
            }
            return visits;
        }

        private static String requireNonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }

    public static List<RouteValue> copySolution(Collection<RouteValue> solution) {
        Objects.requireNonNull(solution, "solution");
        ArrayList<RouteValue> copy = new ArrayList<RouteValue>();
        for (RouteValue value : solution) {
            copy.add(Objects.requireNonNull(value, "solution contains null"));
        }
        return Collections.unmodifiableList(copy);
    }
}
