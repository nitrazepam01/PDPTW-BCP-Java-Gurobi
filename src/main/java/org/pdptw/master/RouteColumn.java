package org.pdptw.master;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable route-column descriptor shared by the LP RMP and final integer master.
 */
public final class RouteColumn {
    private final String name;
    private final Route route;
    private final List<Integer> vertexIds;
    private final Set<Integer> servedRequests;
    private final long servedRequestMask;
    private final double cost;
    private final double fleetCoefficient;
    private final boolean artificial;
    private final String signature;

    private RouteColumn(String name, Route route, Collection<Integer> vertexIds,
                        Collection<Integer> servedRequests, double cost,
                        double fleetCoefficient, boolean artificial) {
        this.name = requireNonBlank(name, "name");
        this.route = route;
        this.vertexIds = Collections.unmodifiableList(new ArrayList<>(vertexIds));
        this.servedRequests = Collections.unmodifiableSet(normalizedRequests(servedRequests));
        this.servedRequestMask = buildServedRequestMask(this.servedRequests);
        this.cost = requireFinite(cost, "cost");
        this.fleetCoefficient = requireFinite(fleetCoefficient, "fleetCoefficient");
        this.artificial = artificial;
        this.signature = buildSignature();
    }

    public static RouteColumn fromRoute(String name, Route route, Instance instance) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(instance, "instance");
        return new RouteColumn(
                name,
                route,
                route.vertexIds(),
                route.servedRequests(instance),
                route.cost(instance),
                1.0,
                false
        );
    }

    public static RouteColumn artificial(String name, int requestId, double penalty) {
        if (requestId < 1) {
            throw new IllegalArgumentException("requestId must be positive: " + requestId);
        }
        requireFinite(penalty, "penalty");
        if (penalty <= 0.0) {
            throw new IllegalArgumentException("penalty must be positive: " + penalty);
        }
        return new RouteColumn(
                name,
                null,
                List.of(),
                Set.of(requestId),
                penalty,
                0.0,
                true
        );
    }

    public String name() {
        return name;
    }

    public Route route() {
        if (artificial) {
            throw new IllegalStateException("Artificial column has no PDPTW route");
        }
        return route;
    }

    public List<Integer> vertexIds() {
        return vertexIds;
    }

    public Set<Integer> servedRequests() {
        return servedRequests;
    }

    public boolean covers(int requestId) {
        if (requestId > 0 && requestId < Long.SIZE) {
            return (servedRequestMask & (1L << requestId)) != 0L;
        }
        return servedRequests.contains(requestId);
    }

    public double cost() {
        return cost;
    }

    public double fleetCoefficient() {
        return fleetCoefficient;
    }

    public boolean isArtificial() {
        return artificial;
    }

    public boolean isRealRoute() {
        return !artificial;
    }

    public String signature() {
        return signature;
    }

    private String buildSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(artificial ? "artificial" : "route").append('|');
        for (int requestId : servedRequests) {
            sb.append(requestId).append(',');
        }
        sb.append('|');
        for (int vertexId : vertexIds) {
            sb.append(vertexId).append(',');
        }
        return sb.toString();
    }

    private static Set<Integer> normalizedRequests(Collection<Integer> requestIds) {
        Objects.requireNonNull(requestIds, "requestIds");
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer requestId : requestIds) {
            if (requestId == null || requestId < 1) {
                throw new IllegalArgumentException("request ids must be positive: " + requestId);
            }
            sorted.add(requestId);
        }
        return new LinkedHashSet<>(sorted);
    }

    private static long buildServedRequestMask(Set<Integer> requestIds) {
        long mask = 0L;
        for (int requestId : requestIds) {
            if (requestId < Long.SIZE) {
                mask |= 1L << requestId;
            }
        }
        return mask;
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
}
