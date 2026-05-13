package org.pdptw.branch;

import java.util.List;

public interface BranchConstraint {
    Type type();

    Sense sense();

    int rhs();

    String name();

    String expression();

    boolean isSatisfied(List<BranchDecision.RouteValue> solution, double tolerance);

    enum Type {
        VEHICLE_COUNT,
        SET_OUTFLOW
    }

    enum Sense {
        LESS_OR_EQUAL("<="),
        GREATER_OR_EQUAL(">=");

        private final String symbol;

        Sense(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public boolean accepts(double value, double rhs, double tolerance) {
            if (tolerance < 0.0 || !Double.isFinite(tolerance)) {
                throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
            }
            return switch (this) {
                case LESS_OR_EQUAL -> value <= rhs + tolerance;
                case GREATER_OR_EQUAL -> value + tolerance >= rhs;
            };
        }
    }
}
