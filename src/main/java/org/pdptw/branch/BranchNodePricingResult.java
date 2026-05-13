package org.pdptw.branch;

import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingResult;

import java.util.List;
import java.util.Objects;

final class BranchNodePricingResult {
    private final double bestReducedCost;
    private final int pricedColumns;
    private final List<RouteColumn> columns;
    private final PricingResult.Stats stats;
    private final boolean statsAvailable;

    private BranchNodePricingResult(
            double bestReducedCost,
            int pricedColumns,
            List<RouteColumn> columns,
            PricingResult.Stats stats,
            boolean statsAvailable) {
        List<RouteColumn> safeColumns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        if (!Double.isFinite(bestReducedCost)) {
            throw new IllegalArgumentException("bestReducedCost must be finite: " + bestReducedCost);
        }
        if (pricedColumns < 0) {
            throw new IllegalArgumentException("pricedColumns must be non-negative: " + pricedColumns);
        }
        if (pricedColumns < safeColumns.size()) {
            throw new IllegalArgumentException("pricedColumns must cover returned columns: pricedColumns="
                    + pricedColumns + " columns=" + safeColumns.size());
        }
        this.bestReducedCost = bestReducedCost;
        this.pricedColumns = pricedColumns;
        this.columns = safeColumns;
        this.stats = Objects.requireNonNull(stats, "stats");
        this.statsAvailable = statsAvailable;
    }

    static BranchNodePricingResult of(
            double bestReducedCost,
            int pricedColumns,
            List<RouteColumn> columns) {
        return new BranchNodePricingResult(
                bestReducedCost,
                pricedColumns,
                columns,
                PricingResult.Stats.empty(),
                false);
    }

    static BranchNodePricingResult of(
            double bestReducedCost,
            int pricedColumns,
            List<RouteColumn> columns,
            PricingResult.Stats stats) {
        return new BranchNodePricingResult(bestReducedCost, pricedColumns, columns, stats, true);
    }

    double bestReducedCost() {
        return bestReducedCost;
    }

    int pricedColumns() {
        return pricedColumns;
    }

    boolean hasColumns() {
        return !columns.isEmpty();
    }

    List<RouteColumn> columns() {
        return columns;
    }

    PricingResult.Stats stats() {
        return stats;
    }

    boolean statsAvailable() {
        return statsAvailable;
    }
}
