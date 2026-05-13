package org.pdptw.cli;

import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RootCgResult {
    public static final String EXACT_NO_NEGATIVE = "exact_no_negative";

    private final String pricingMode;
    private final List<Iteration> iterations;
    private final List<RouteColumn> realColumns;
    private final double finalObjectiveValue;
    private final int initialColumnCount;
    private final int finalColumnCount;
    private final boolean hasPositiveArtificial;
    private final String terminationReason;

    RootCgResult(
            String pricingMode,
            List<Iteration> iterations,
            List<RouteColumn> realColumns,
            double finalObjectiveValue,
            int initialColumnCount,
            int finalColumnCount,
            boolean hasPositiveArtificial,
            String terminationReason) {
        this.pricingMode = requireNonBlank(pricingMode, "pricingMode");
        this.iterations = Collections.unmodifiableList(new ArrayList<Iteration>(
                Objects.requireNonNull(iterations, "iterations")));
        this.realColumns = Collections.unmodifiableList(new ArrayList<RouteColumn>(
                Objects.requireNonNull(realColumns, "realColumns")));
        this.finalObjectiveValue = requireFinite(finalObjectiveValue, "finalObjectiveValue");
        this.initialColumnCount = initialColumnCount;
        this.finalColumnCount = finalColumnCount;
        this.hasPositiveArtificial = hasPositiveArtificial;
        this.terminationReason = requireNonBlank(terminationReason, "terminationReason");
    }

    public String pricingMode() {
        return pricingMode;
    }

    public List<Iteration> iterations() {
        return iterations;
    }

    public int iterationCount() {
        return iterations.size();
    }

    public List<RouteColumn> realColumns() {
        return realColumns;
    }

    public int realColumnCount() {
        return realColumns.size();
    }

    public double finalObjectiveValue() {
        return finalObjectiveValue;
    }

    public int initialColumnCount() {
        return initialColumnCount;
    }

    public int finalColumnCount() {
        return finalColumnCount;
    }

    public boolean hasPositiveArtificial() {
        return hasPositiveArtificial;
    }

    public String terminationReason() {
        return terminationReason;
    }

    public boolean terminatedByExactNoNegative() {
        return EXACT_NO_NEGATIVE.equals(terminationReason);
    }

    public int totalForwardLabels() {
        long total = 0L;
        for (Iteration iteration : iterations) {
            total += iteration.pricingStats().generatedForwardLabels();
        }
        return requireIntRange(total, "totalForwardLabels");
    }

    public int totalBackwardLabels() {
        long total = 0L;
        for (Iteration iteration : iterations) {
            total += iteration.pricingStats().generatedBackwardLabels();
        }
        return requireIntRange(total, "totalBackwardLabels");
    }

    public int totalDominatedLabels() {
        long total = 0L;
        for (Iteration iteration : iterations) {
            total += iteration.pricingStats().dominatedLabels();
        }
        return requireIntRange(total, "totalDominatedLabels");
    }

    public long totalPricingTimeMs() {
        long total = 0L;
        for (Iteration iteration : iterations) {
            total += iteration.pricingTimeMs();
        }
        return total;
    }

    public int finalCutsActive() {
        if (iterations.isEmpty()) {
            return 0;
        }
        return iterations.get(iterations.size() - 1).cutsActive();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    private static int requireIntRange(long value, String name) {
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(name + " exceeds int range: " + value);
        }
        return (int) value;
    }

    public static final class Iteration {
        private final int index;
        private final double lpObjectiveValue;
        private final double bestReducedCost;
        private final int addedColumns;
        private final int columnCount;
        private final int cutsActive;
        private final PricingResult.Stats pricingStats;
        private final long pricingTimeMs;
        private final boolean exactPricing;
        private final String pricingStatus;

        Iteration(
                int index,
                double lpObjectiveValue,
                double bestReducedCost,
                int addedColumns,
                int columnCount,
                int cutsActive,
                PricingResult.Stats pricingStats,
                long pricingTimeMs,
                boolean exactPricing,
                String pricingStatus) {
            if (index < 0) {
                throw new IllegalArgumentException("index must be non-negative: " + index);
            }
            this.index = index;
            this.lpObjectiveValue = requireFinite(lpObjectiveValue, "lpObjectiveValue");
            this.bestReducedCost = requireFinite(bestReducedCost, "bestReducedCost");
            this.addedColumns = addedColumns;
            this.columnCount = columnCount;
            if (cutsActive < 0) {
                throw new IllegalArgumentException("cutsActive must be non-negative: " + cutsActive);
            }
            if (pricingTimeMs < 0) {
                throw new IllegalArgumentException("pricingTimeMs must be non-negative: " + pricingTimeMs);
            }
            this.cutsActive = cutsActive;
            this.pricingStats = Objects.requireNonNull(pricingStats, "pricingStats");
            this.pricingTimeMs = pricingTimeMs;
            this.exactPricing = exactPricing;
            this.pricingStatus = requireNonBlank(pricingStatus, "pricingStatus");
        }

        public int index() {
            return index;
        }

        public double lpObjectiveValue() {
            return lpObjectiveValue;
        }

        public double bestReducedCost() {
            return bestReducedCost;
        }

        public int addedColumns() {
            return addedColumns;
        }

        public int columnCount() {
            return columnCount;
        }

        public int cutsActive() {
            return cutsActive;
        }

        public PricingResult.Stats pricingStats() {
            return pricingStats;
        }

        public long pricingTimeMs() {
            return pricingTimeMs;
        }

        public boolean exactPricing() {
            return exactPricing;
        }

        public String pricingStatus() {
            return pricingStatus;
        }
    }
}
