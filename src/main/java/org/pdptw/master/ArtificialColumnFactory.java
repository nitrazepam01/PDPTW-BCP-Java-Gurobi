package org.pdptw.master;

import org.pdptw.core.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ArtificialColumnFactory {
    public static final double DEFAULT_PENALTY = 1.0e9;

    private final double penalty;

    public ArtificialColumnFactory() {
        this(DEFAULT_PENALTY);
    }

    public ArtificialColumnFactory(double penalty) {
        if (!Double.isFinite(penalty) || penalty <= 0.0) {
            throw new IllegalArgumentException("penalty must be positive and finite: " + penalty);
        }
        this.penalty = penalty;
    }

    public double penalty() {
        return penalty;
    }

    public RouteColumn forRequest(int requestId) {
        return RouteColumn.artificial("art_req_" + requestId, requestId, penalty);
    }

    public List<RouteColumn> forInstance(Instance instance) {
        Objects.requireNonNull(instance, "instance");
        int nRequests = instance.nRequests();
        if (nRequests < 0) {
            throw new IllegalArgumentException("nRequests must be non-negative: " + nRequests);
        }
        List<RouteColumn> columns = new ArrayList<>(nRequests);
        for (int requestId = 1; requestId <= nRequests; requestId++) {
            columns.add(forRequest(requestId));
        }
        return columns;
    }
}
