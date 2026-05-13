package org.pdptw.pricing;

import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class SetOutflowPricingRule {
    static final int STATE_NONE = 0;
    static final int STATE_OUTSIDE = 1;
    static final int STATE_INSIDE = 2;

    private final String name;
    private final Set<Integer> requestSet;
    private final double pricingDual;

    private SetOutflowPricingRule(String name, Collection<Integer> requestSet, double pricingDual) {
        this.name = requireNonBlank(name, "name");
        this.requestSet = Set.copyOf(normalizedRequests(requestSet));
        this.pricingDual = requireFinite(pricingDual, "pricingDual");
    }

    public static SetOutflowPricingRule of(String name, Collection<Integer> requestSet, double pricingDual) {
        return new SetOutflowPricingRule(name, requestSet, pricingDual);
    }

    public String name() {
        return name;
    }

    public Set<Integer> requestSet() {
        return requestSet;
    }

    public double pricingDual() {
        return pricingDual;
    }

    public boolean containsRequest(int requestId) {
        return requestSet.contains(Integer.valueOf(requestId));
    }

    public double routePrice(Instance instance, List<Integer> vertexIds) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(vertexIds, "vertexIds");
        int state = STATE_NONE;
        double price = 0.0;
        for (int vertexId : vertexIds) {
            Vertex vertex = instance.vertex(vertexId);
            if (!vertex.isPickup() && !vertex.isDelivery()) {
                continue;
            }
            Transition transition = forwardTransition(state, vertex.requestId());
            state = transition.state();
            price += transition.adjustment();
        }
        if (state == STATE_INSIDE) {
            price += pricingDual;
        }
        return requireFinite(price, "routePrice");
    }

    Transition forwardTransition(int previousState, int nextRequestId) {
        requireState(previousState, "previousState");
        boolean nextInside = containsRequest(nextRequestId);
        double adjustment = previousState == STATE_INSIDE && !nextInside ? pricingDual : 0.0;
        return new Transition(nextInside ? STATE_INSIDE : STATE_OUTSIDE, adjustment);
    }

    Transition forwardTerminalTransition(int previousState) {
        requireState(previousState, "previousState");
        return new Transition(previousState, previousState == STATE_INSIDE ? pricingDual : 0.0);
    }

    Transition backwardPrependTransition(int suffixFirstState, int previousRequestId) {
        requireState(suffixFirstState, "suffixFirstState");
        boolean previousInside = containsRequest(previousRequestId);
        double adjustment;
        if (suffixFirstState == STATE_NONE) {
            adjustment = previousInside ? pricingDual : 0.0;
        } else {
            adjustment = previousInside && suffixFirstState == STATE_OUTSIDE ? pricingDual : 0.0;
        }
        return new Transition(previousInside ? STATE_INSIDE : STATE_OUTSIDE, adjustment);
    }

    static void requireState(int state, String name) {
        if (state != STATE_NONE && state != STATE_OUTSIDE && state != STATE_INSIDE) {
            throw new IllegalArgumentException(name + " must be a set-outflow state: " + state);
        }
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
        return sorted;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    static final class Transition {
        private final int state;
        private final double adjustment;

        Transition(int state, double adjustment) {
            requireState(state, "state");
            this.state = state;
            this.adjustment = requireFinite(adjustment, "adjustment");
        }

        int state() {
            return state;
        }

        double adjustment() {
            return adjustment;
        }
    }
}
