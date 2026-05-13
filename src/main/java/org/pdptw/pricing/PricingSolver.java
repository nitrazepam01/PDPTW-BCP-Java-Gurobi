package org.pdptw.pricing;

@FunctionalInterface
public interface PricingSolver {
    PricingResult price(ReducedCostMatrices matrices);

    default PricingResult price(PricingContext context) {
        if (context.hasCuts() || context.hasSetOutflowPricingRules()) {
            throw new IllegalStateException("active cuts or branch pricing rules require"
                    + " a PricingSolver that handles PricingContext");
        }
        return price(context.matrices());
    }
}
