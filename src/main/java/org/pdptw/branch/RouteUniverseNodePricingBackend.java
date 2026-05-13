package org.pdptw.branch;

import org.pdptw.core.Instance;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class RouteUniverseNodePricingBackend implements BranchNodePricingBackend {
    static final String BACKEND_NAME = "route_universe_exact";

    @Override
    public String name() {
        return BACKEND_NAME;
    }

    @Override
    public BranchNodePricingResult price(BranchNodePricingRequest request) {
        Objects.requireNonNull(request, "request");
        Set<String> currentSignatures = new LinkedHashSet<String>();
        for (RouteColumn column : request.currentColumns()) {
            currentSignatures.add(column.signature());
        }
        double bestReducedCost = Double.POSITIVE_INFINITY;
        ArrayList<RouteColumn> negativeColumns = new ArrayList<RouteColumn>();
        int pricedColumns = 0;
        for (RouteColumn column : request.routeUniverse()) {
            if (currentSignatures.contains(column.signature())) {
                continue;
            }
            pricedColumns++;
            double reducedCost = request.pricingContext().directReducedCost(column.vertexIds())
                    + branchPrice(request.instance(), column, request.cutDuals());
            if (reducedCost < bestReducedCost) {
                bestReducedCost = reducedCost;
            }
            if (reducedCost < -request.tolerance()) {
                negativeColumns.add(column);
            }
        }
        if (bestReducedCost == Double.POSITIVE_INFINITY) {
            bestReducedCost = 0.0;
        }
        return BranchNodePricingResult.of(bestReducedCost, pricedColumns, negativeColumns);
    }

    private static double branchPrice(
            Instance instance,
            RouteColumn column,
            List<GurobiRmp.CutDual> cutDuals) {
        double price = 0.0;
        for (GurobiRmp.CutDual dual : cutDuals) {
            if (dual.row() instanceof BranchMasterRow branchRow) {
                price += branchRow.routePrice(instance, column, dual.pricingDual());
            }
        }
        return requireFinite(price, "branchPrice");
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
