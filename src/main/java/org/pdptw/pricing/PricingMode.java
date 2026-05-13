package org.pdptw.pricing;

import java.util.Locale;

public enum PricingMode {
    FORWARD,
    BACKWARD,
    BIDIR_STATIC,
    BIDIR_DYNAMIC;

    public PricingSolver createSolver(double tolerance) {
        return switch (this) {
            case FORWARD -> new ForwardPricingSolver(tolerance);
            case BACKWARD -> new BackwardPricingSolver(tolerance);
            case BIDIR_STATIC -> new BidirectionalStaticPricingSolver(tolerance);
            case BIDIR_DYNAMIC -> new BidirectionalDynamicPricingSolver(tolerance);
        };
    }

    public static PricingMode fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("pricing mode must not be blank");
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("BIDIR".equals(normalized) || "BIDIRECTIONAL".equals(normalized)
                || "BIDIRECTIONAL_STATIC".equals(normalized)) {
            return BIDIR_STATIC;
        }
        if ("BIDIR_DYNAMIC".equals(normalized) || "BIDIRECTIONAL_DYNAMIC".equals(normalized)) {
            return BIDIR_DYNAMIC;
        }
        return PricingMode.valueOf(normalized);
    }
}
