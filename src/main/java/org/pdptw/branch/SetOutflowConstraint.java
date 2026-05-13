package org.pdptw.branch;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class SetOutflowConstraint implements BranchConstraint {
    private final Set<Integer> requestSet;
    private final Sense sense;
    private final int rhs;

    private SetOutflowConstraint(Collection<Integer> requestSet, Sense sense, int rhs) {
        this.requestSet = Set.copyOf(normalizedRequests(requestSet));
        this.sense = Objects.requireNonNull(sense, "sense");
        this.rhs = requireNonNegative(rhs, "rhs");
    }

    public static SetOutflowConstraint lessOrEqual(Collection<Integer> requestSet, int rhs) {
        return new SetOutflowConstraint(requestSet, Sense.LESS_OR_EQUAL, rhs);
    }

    public static SetOutflowConstraint greaterOrEqual(Collection<Integer> requestSet, int rhs) {
        return new SetOutflowConstraint(requestSet, Sense.GREATER_OR_EQUAL, rhs);
    }

    @Override
    public Type type() {
        return Type.SET_OUTFLOW;
    }

    @Override
    public Sense sense() {
        return sense;
    }

    @Override
    public int rhs() {
        return rhs;
    }

    public Set<Integer> requestSet() {
        return requestSet;
    }

    @Override
    public String name() {
        return "set_outflow_U" + compactRequestSet() + "_" + (sense == Sense.LESS_OR_EQUAL ? "le_" : "ge_") + rhs;
    }

    @Override
    public String expression() {
        return "x(delta+(" + displayRequestSet() + ")) " + sense.symbol() + " " + rhs;
    }

    @Override
    public boolean isSatisfied(List<BranchDecision.RouteValue> solution, double tolerance) {
        return sense.accepts(value(solution, requestSet), rhs, tolerance);
    }

    public static double value(List<BranchDecision.RouteValue> solution, Collection<Integer> requestSet) {
        Objects.requireNonNull(solution, "solution");
        Set<Integer> normalized = normalizedRequests(requestSet);
        double value = 0.0;
        for (BranchDecision.RouteValue routeValue : solution) {
            value += routeCoefficient(routeValue, normalized) * routeValue.lambda();
        }
        return requireFinite(value, "setOutflowValue");
    }

    public static int routeCoefficient(BranchDecision.RouteValue routeValue, Collection<Integer> requestSet) {
        Objects.requireNonNull(routeValue, "routeValue");
        Set<Integer> normalized = normalizedRequests(requestSet);
        if (routeValue.hasRequestVisitSequence()) {
            int exits = 0;
            boolean previousInside = false;
            for (int requestId : routeValue.requestVisitSequence()) {
                boolean currentInside = normalized.contains(Integer.valueOf(requestId));
                if (previousInside && !currentInside) {
                    exits++;
                }
                previousInside = currentInside;
            }
            if (previousInside) {
                exits++;
            }
            return exits;
        }
        for (int requestId : routeValue.servedRequests()) {
            if (normalized.contains(Integer.valueOf(requestId))) {
                return 1;
            }
        }
        return 0;
    }

    private String compactRequestSet() {
        StringBuilder sb = new StringBuilder();
        for (int requestId : requestSet) {
            sb.append(requestId);
        }
        return sb.toString();
    }

    private String displayRequestSet() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int requestId : requestSet) {
            if (!first) {
                sb.append(',');
            }
            sb.append(requestId);
            first = false;
        }
        sb.append('}');
        return sb.toString();
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
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("requestIds must not be empty");
        }
        return new LinkedHashSet<Integer>(sorted);
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
