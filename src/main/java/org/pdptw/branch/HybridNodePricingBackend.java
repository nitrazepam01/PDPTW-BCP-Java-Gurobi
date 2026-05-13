package org.pdptw.branch;

import org.pdptw.master.GurobiRmp;

import java.util.Objects;

final class HybridNodePricingBackend implements BranchNodePricingBackend {
    static final String BACKEND_NAME = "hybrid_labeling_then_route_universe";

    private final BranchNodePricingBackend rootBackend;
    private final BranchNodePricingBackend fallbackBackend;

    HybridNodePricingBackend(
            BranchNodePricingBackend rootBackend,
            BranchNodePricingBackend fallbackBackend) {
        this.rootBackend = Objects.requireNonNull(rootBackend, "rootBackend");
        this.fallbackBackend = Objects.requireNonNull(fallbackBackend, "fallbackBackend");
    }

    @Override
    public String name() {
        return BACKEND_NAME;
    }

    @Override
    public BranchNodePricingResult price(BranchNodePricingRequest request) {
        Objects.requireNonNull(request, "request");
        return hasUnsupportedLabelingBranchRows(request)
                ? fallbackBackend.price(request)
                : rootBackend.price(request);
    }

    private static boolean hasUnsupportedLabelingBranchRows(BranchNodePricingRequest request) {
        for (GurobiRmp.CutDual dual : request.cutDuals()) {
            if (dual.row() instanceof BranchMasterRow branchRow
                    && !(branchRow.constraint() instanceof VehicleCountConstraint)
                    && !(branchRow.constraint() instanceof SetOutflowConstraint)) {
                return true;
            }
        }
        return false;
    }
}
