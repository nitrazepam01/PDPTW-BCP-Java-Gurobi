package org.pdptw.pricing;

import org.pdptw.master.RouteColumn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Minimal pricing result contract shared by exact pricing implementations.
 */
public final class PricingResult {
    public static final String OPTIMAL = "optimal";
    public static final String NO_NEGATIVE_COLUMN = "no_negative_column";

    private final List<RouteColumn> columns;
    private final double bestReducedCost;
    private final boolean exact;
    private final String status;
    private final Stats stats;

    private PricingResult(List<RouteColumn> columns, double bestReducedCost, boolean exact, String status) {
        this(columns, bestReducedCost, exact, status, Stats.empty());
    }

    private PricingResult(
            List<RouteColumn> columns,
            double bestReducedCost,
            boolean exact,
            String status,
            Stats stats) {
        Objects.requireNonNull(columns, "columns");
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        for (RouteColumn column : this.columns) {
            Objects.requireNonNull(column, "columns contains null");
        }
        this.bestReducedCost = requireFinite(bestReducedCost, "bestReducedCost");
        this.exact = exact;
        this.status = requireNonBlank(status, "status");
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    public static PricingResult of(List<RouteColumn> columns, double bestReducedCost, boolean exact, String status) {
        return new PricingResult(columns, bestReducedCost, exact, status);
    }

    public static PricingResult of(
            List<RouteColumn> columns,
            double bestReducedCost,
            boolean exact,
            String status,
            Stats stats) {
        return new PricingResult(columns, bestReducedCost, exact, status, stats);
    }

    public static PricingResult exact(List<RouteColumn> columns, double bestReducedCost) {
        return new PricingResult(columns, bestReducedCost, true, OPTIMAL);
    }

    public static PricingResult exact(List<RouteColumn> columns, double bestReducedCost, Stats stats) {
        return new PricingResult(columns, bestReducedCost, true, OPTIMAL, stats);
    }

    public static PricingResult noNegativeColumn(double bestReducedCost) {
        return new PricingResult(List.of(), bestReducedCost, true, NO_NEGATIVE_COLUMN);
    }

    public static PricingResult noNegativeColumn(double bestReducedCost, Stats stats) {
        return new PricingResult(List.of(), bestReducedCost, true, NO_NEGATIVE_COLUMN, stats);
    }

    public List<RouteColumn> columns() {
        return columns;
    }

    public boolean hasColumns() {
        return !columns.isEmpty();
    }

    public RouteColumn bestColumn() {
        if (columns.isEmpty()) {
            throw new IllegalStateException("Pricing result has no columns");
        }
        return columns.get(0);
    }

    public double bestReducedCost() {
        return bestReducedCost;
    }

    public boolean hasNegativeColumn(double tolerance) {
        if (tolerance < 0.0 || !Double.isFinite(tolerance)) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        return bestReducedCost < -tolerance && !columns.isEmpty();
    }

    public boolean exact() {
        return exact;
    }

    public String status() {
        return status;
    }

    public Stats stats() {
        return stats;
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

    public static final class Stats {
        private static final Stats EMPTY = new Stats(
                0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, Double.NaN, Double.NaN, 0, 0);

        private final int generatedForwardLabels;
        private final int generatedBackwardLabels;
        private final int completeForwardLabels;
        private final int completeBackwardLabels;
        private final int completeLabels;
        private final int merges;
        private final int dominatedLabels;
        private final int processedForwardLabels;
        private final int processedBackwardLabels;
        private final int unprocessedForwardLabels;
        private final int unprocessedBackwardLabels;
        private final double finalBackwardLowerBound;
        private final double finalForwardUpperBound;
        private final int dominanceCleanupPrunedLabels;
        private final int dominanceCleanupTriggers;

        private Stats(
                int generatedForwardLabels,
                int generatedBackwardLabels,
                int completeForwardLabels,
                int completeBackwardLabels,
                int completeLabels,
                int merges,
                int dominatedLabels,
                int processedForwardLabels,
                int processedBackwardLabels,
                int unprocessedForwardLabels,
                int unprocessedBackwardLabels,
                double finalBackwardLowerBound,
                double finalForwardUpperBound,
                int dominanceCleanupPrunedLabels,
                int dominanceCleanupTriggers) {
            this.generatedForwardLabels = requireNonNegative(
                    generatedForwardLabels,
                    "generatedForwardLabels");
            this.generatedBackwardLabels = requireNonNegative(
                    generatedBackwardLabels,
                    "generatedBackwardLabels");
            this.completeForwardLabels = requireNonNegative(
                    completeForwardLabels,
                    "completeForwardLabels");
            this.completeBackwardLabels = requireNonNegative(
                    completeBackwardLabels,
                    "completeBackwardLabels");
            this.completeLabels = requireNonNegative(completeLabels, "completeLabels");
            if (this.completeLabels != this.completeForwardLabels + this.completeBackwardLabels) {
                throw new IllegalArgumentException(
                        "completeLabels must equal completeForwardLabels + completeBackwardLabels");
            }
            this.merges = requireNonNegative(merges, "merges");
            this.dominatedLabels = requireNonNegative(dominatedLabels, "dominatedLabels");
            this.processedForwardLabels = requireNonNegative(processedForwardLabels, "processedForwardLabels");
            this.processedBackwardLabels = requireNonNegative(processedBackwardLabels, "processedBackwardLabels");
            this.unprocessedForwardLabels = requireNonNegative(unprocessedForwardLabels, "unprocessedForwardLabels");
            this.unprocessedBackwardLabels = requireNonNegative(unprocessedBackwardLabels, "unprocessedBackwardLabels");
            this.finalBackwardLowerBound = requireOptionalFinite(
                    finalBackwardLowerBound,
                    "finalBackwardLowerBound");
            this.finalForwardUpperBound = requireOptionalFinite(
                    finalForwardUpperBound,
                    "finalForwardUpperBound");
            if (Double.isFinite(finalBackwardLowerBound)
                    && Double.isFinite(finalForwardUpperBound)
                    && finalBackwardLowerBound > finalForwardUpperBound + 1.0e-9) {
                throw new IllegalArgumentException("dynamic half-way stats require HB <= HF");
            }
            this.dominanceCleanupPrunedLabels = requireNonNegative(
                    dominanceCleanupPrunedLabels,
                    "dominanceCleanupPrunedLabels");
            if (this.dominanceCleanupPrunedLabels > this.dominatedLabels) {
                throw new IllegalArgumentException(
                        "dominanceCleanupPrunedLabels cannot exceed dominatedLabels");
            }
            this.dominanceCleanupTriggers = requireNonNegative(
                    dominanceCleanupTriggers,
                    "dominanceCleanupTriggers");
        }

        public static Stats empty() {
            return EMPTY;
        }

        public static Stats of(
                int generatedForwardLabels,
                int generatedBackwardLabels,
                int completeForwardLabels,
                int completeBackwardLabels,
                int completeLabels,
                int merges,
                int dominatedLabels) {
            return new Stats(
                    generatedForwardLabels,
                    generatedBackwardLabels,
                    completeForwardLabels,
                    completeBackwardLabels,
                    completeLabels,
                    merges,
                    dominatedLabels,
                    0,
                    0,
                    0,
                    0,
                    Double.NaN,
                    Double.NaN,
                    0,
                    0);
        }

        public static Stats ofDynamic(
                int generatedForwardLabels,
                int generatedBackwardLabels,
                int completeForwardLabels,
                int completeBackwardLabels,
                int completeLabels,
                int merges,
                int dominatedLabels,
                DynamicHalfwayController.Snapshot snapshot,
                int dominanceCleanupPrunedLabels,
                int dominanceCleanupTriggers) {
            Objects.requireNonNull(snapshot, "snapshot");
            return new Stats(
                    generatedForwardLabels,
                    generatedBackwardLabels,
                    completeForwardLabels,
                    completeBackwardLabels,
                    completeLabels,
                    merges,
                    dominatedLabels,
                    snapshot.processedForwardLabels(),
                    snapshot.processedBackwardLabels(),
                    snapshot.unprocessedForwardLabels(),
                    snapshot.unprocessedBackwardLabels(),
                    snapshot.backwardLowerBound(),
                    snapshot.forwardUpperBound(),
                    dominanceCleanupPrunedLabels,
                    dominanceCleanupTriggers);
        }

        public int generatedForwardLabels() {
            return generatedForwardLabels;
        }

        public int generatedBackwardLabels() {
            return generatedBackwardLabels;
        }

        public int completeForwardLabels() {
            return completeForwardLabels;
        }

        public int completeBackwardLabels() {
            return completeBackwardLabels;
        }

        public int completeLabels() {
            return completeLabels;
        }

        public int merges() {
            return merges;
        }

        public int dominatedLabels() {
            return dominatedLabels;
        }

        public int processedForwardLabels() {
            return processedForwardLabels;
        }

        public int processedBackwardLabels() {
            return processedBackwardLabels;
        }

        public int unprocessedForwardLabels() {
            return unprocessedForwardLabels;
        }

        public int unprocessedBackwardLabels() {
            return unprocessedBackwardLabels;
        }

        public double finalBackwardLowerBound() {
            return finalBackwardLowerBound;
        }

        public double finalForwardUpperBound() {
            return finalForwardUpperBound;
        }

        public int dominanceCleanupTriggers() {
            return dominanceCleanupTriggers;
        }

        public int dominanceCleanupPrunedLabels() {
            return dominanceCleanupPrunedLabels;
        }

        private static int requireNonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be non-negative: " + value);
            }
            return value;
        }

        private static double requireOptionalFinite(double value, String name) {
            if (!Double.isNaN(value) && !Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite or NaN: " + value);
            }
            return value;
        }
    }
}
