package org.pdptw.branch;

import org.pdptw.core.Instance;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingContext;

import java.util.List;
import java.util.Objects;

final class BranchNodePricingRequest {
    private final Instance instance;
    private final PricingContext pricingContext;
    private final List<GurobiRmp.CutDual> cutDuals;
    private final List<RouteColumn> routeUniverse;
    private final List<RouteColumn> currentColumns;
    private final double tolerance;

    BranchNodePricingRequest(
            Instance instance,
            PricingContext pricingContext,
            List<GurobiRmp.CutDual> cutDuals,
            List<RouteColumn> routeUniverse,
            List<RouteColumn> currentColumns,
            double tolerance) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.pricingContext = Objects.requireNonNull(pricingContext, "pricingContext");
        this.cutDuals = List.copyOf(Objects.requireNonNull(cutDuals, "cutDuals"));
        this.routeUniverse = List.copyOf(Objects.requireNonNull(routeUniverse, "routeUniverse"));
        this.currentColumns = List.copyOf(Objects.requireNonNull(currentColumns, "currentColumns"));
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        this.tolerance = tolerance;
    }

    Instance instance() {
        return instance;
    }

    PricingContext pricingContext() {
        return pricingContext;
    }

    List<GurobiRmp.CutDual> cutDuals() {
        return cutDuals;
    }

    List<RouteColumn> routeUniverse() {
        return routeUniverse;
    }

    List<RouteColumn> currentColumns() {
        return currentColumns;
    }

    double tolerance() {
        return tolerance;
    }
}
