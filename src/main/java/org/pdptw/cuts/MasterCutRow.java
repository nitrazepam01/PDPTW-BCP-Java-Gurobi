package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.master.RouteColumn;

import java.util.Objects;

public interface MasterCutRow {
    String name();

    Sense sense();

    double rhs();

    double coefficient(Instance instance, RouteColumn column);

    default double pricingDualFromRawPi(double rawPi) {
        if (!Double.isFinite(rawPi)) {
            throw new IllegalArgumentException("rawPi must be finite: " + rawPi);
        }
        return -rawPi;
    }

    enum Sense {
        LESS_EQUAL,
        GREATER_EQUAL,
        EQUAL
    }

    static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
