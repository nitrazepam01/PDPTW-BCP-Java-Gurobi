package org.pdptw.cuts;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SubsetRowCut {
    private final String name;
    private final Set<Integer> requests;
    private final long requestMask;
    private final int l;
    private final double sigma;

    private SubsetRowCut(String name, Collection<Integer> requests, int l, double sigma) {
        this.name = requireNonBlank(name, "name");
        this.requests = Set.copyOf(copyRequests(requests));
        this.requestMask = toMask(this.requests);
        this.l = requirePositive(l, "l");
        if (this.requests.size() < this.l) {
            throw new IllegalArgumentException("request set size must be at least l");
        }
        this.sigma = requireFinite(sigma, "sigma");
    }

    public static SubsetRowCut of(String name, Collection<Integer> requests, int l, double sigma) {
        return new SubsetRowCut(name, requests, l, sigma);
    }

    public static SubsetRowCut ofL2Triple(String name, int first, int second, int third, double sigma) {
        return new SubsetRowCut(name, List.of(
                Integer.valueOf(first),
                Integer.valueOf(second),
                Integer.valueOf(third)), 2, sigma);
    }

    public String name() {
        return name;
    }

    public Set<Integer> requests() {
        return requests;
    }

    public long requestMask() {
        return requestMask;
    }

    public int l() {
        return l;
    }

    public double sigma() {
        return sigma;
    }

    public int rhs() {
        return requests.size() / l;
    }

    public boolean containsRequest(int requestId) {
        return requests.contains(Integer.valueOf(requestId));
    }

    public boolean containsRequestMask(long mask) {
        return (requestMask & mask) != 0L;
    }

    public int countInRequestMask(long mask) {
        return BitSetOps.size(requestMask & mask);
    }

    public int coefficientForServedRequests(Collection<Integer> servedRequests) {
        Objects.requireNonNull(servedRequests, "servedRequests");
        LinkedHashSet<Integer> unique = new LinkedHashSet<Integer>();
        for (Integer requestId : servedRequests) {
            if (requestId != null && containsRequest(requestId.intValue())) {
                unique.add(requestId);
            }
        }
        return unique.size() / l;
    }

    public int coefficientForRoute(Instance instance, List<Integer> vertexIds) {
        return coefficientForServedRequests(servedRequests(instance, vertexIds));
    }

    public Set<Integer> servedRequests(Instance instance, List<Integer> vertexIds) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(vertexIds, "vertexIds");
        LinkedHashSet<Integer> pickups = new LinkedHashSet<Integer>();
        LinkedHashSet<Integer> deliveries = new LinkedHashSet<Integer>();
        for (Integer id : vertexIds) {
            Vertex vertex = instance.vertex(id.intValue());
            if (vertex.isPickup() && containsRequest(vertex.requestId())) {
                pickups.add(Integer.valueOf(vertex.requestId()));
            } else if (vertex.isDelivery() && containsRequest(vertex.requestId())) {
                deliveries.add(Integer.valueOf(vertex.requestId()));
            }
        }
        pickups.retainAll(deliveries);
        return Set.copyOf(pickups);
    }

    private static LinkedHashSet<Integer> copyRequests(Collection<Integer> source) {
        Objects.requireNonNull(source, "requests");
        LinkedHashSet<Integer> copy = new LinkedHashSet<Integer>();
        for (Integer requestId : source) {
            Objects.requireNonNull(requestId, "requests contains null");
            if (requestId.intValue() <= 0 || requestId.intValue() > 63) {
                throw new IllegalArgumentException("request id must be in 1..63: " + requestId);
            }
            if (!copy.add(requestId)) {
                throw new IllegalArgumentException("duplicate request id: " + requestId);
            }
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("requests must not be empty");
        }
        return copy;
    }

    private static long toMask(Set<Integer> requests) {
        long mask = 0L;
        for (Integer requestId : requests) {
            mask = BitSetOps.add(mask, requestId.intValue());
        }
        return mask;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
