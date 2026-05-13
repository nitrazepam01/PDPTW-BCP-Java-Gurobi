package org.pdptw.branch;

interface BranchNodePricingBackend {
    String name();

    BranchNodePricingResult price(BranchNodePricingRequest request);
}
