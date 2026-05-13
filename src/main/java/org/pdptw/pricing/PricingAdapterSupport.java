package org.pdptw.pricing;

import org.pdptw.core.Route;
import org.pdptw.master.RouteColumn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PricingAdapterSupport {
    private static final double RELATIVE_BEST_RC_AUDIT_TOLERANCE = 1.0e-15;

    private PricingAdapterSupport() {
    }

    static PricingResult exactFromRoutes(
            String columnPrefix,
            ReducedCostMatrices matrices,
            Iterable<List<Integer>> routes,
            double bestReducedCost,
            double tolerance) {
        return exactFromRoutes(
                columnPrefix,
                PricingContext.noCuts(matrices),
                routes,
                bestReducedCost,
                tolerance,
                PricingResult.Stats.empty());
    }

    static PricingResult exactFromRoutes(
            String columnPrefix,
            ReducedCostMatrices matrices,
            Iterable<List<Integer>> routes,
            double bestReducedCost,
            double tolerance,
            PricingResult.Stats stats) {
        return exactFromRoutes(
                columnPrefix,
                PricingContext.noCuts(matrices),
                routes,
                bestReducedCost,
                tolerance,
                stats);
    }

    static PricingResult exactFromRoutes(
            String columnPrefix,
            PricingContext context,
            Iterable<List<Integer>> routes,
            double bestReducedCost,
            double tolerance,
            PricingResult.Stats stats) {
        Objects.requireNonNull(columnPrefix, "columnPrefix");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(stats, "stats");
        requireTolerance(tolerance);

        Map<String, RouteColumn> negativeColumns = new LinkedHashMap<String, RouteColumn>();
        double directBestReducedCost = Double.POSITIVE_INFINITY;
        boolean sawRoute = false;
        for (List<Integer> routeIds : routes) {
            Objects.requireNonNull(routeIds, "routes contains null");
            double reducedCost = context.directReducedCost(routeIds);
            sawRoute = true;
            if (reducedCost < directBestReducedCost) {
                directBestReducedCost = reducedCost;
            }
            if (reducedCost < -tolerance) {
                String key = routeKey(routeIds);
                negativeColumns.putIfAbsent(key,
                        RouteColumn.fromRoute(
                                columnName(columnPrefix, routeIds),
                                new Route(routeIds),
                                context.instance()));
            }
        }

        double resultBestReducedCost = sawRoute ? directBestReducedCost : 0.0;
        auditCallerBestReducedCost(bestReducedCost, sawRoute, resultBestReducedCost, tolerance);
        if (resultBestReducedCost >= -tolerance) {
            return PricingResult.noNegativeColumn(resultBestReducedCost, stats);
        }
        return PricingResult.exact(
                new ArrayList<RouteColumn>(negativeColumns.values()),
                resultBestReducedCost,
                stats);
    }

    static double requireTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        return tolerance;
    }

    private static void auditCallerBestReducedCost(
            double callerBestReducedCost,
            boolean sawRoute,
            double directBestReducedCost,
            double tolerance) {
        if (!Double.isFinite(callerBestReducedCost) || callerBestReducedCost >= -tolerance) {
            return;
        }
        double scale = Math.max(1.0, Math.max(Math.abs(callerBestReducedCost), Math.abs(directBestReducedCost)));
        double allowedError = Math.max(tolerance, RELATIVE_BEST_RC_AUDIT_TOLERANCE * scale);
        if (!sawRoute || directBestReducedCost > callerBestReducedCost + allowedError) {
            throw new IllegalStateException("pricing adapter best reduced-cost mismatch"
                    + " callerBestReducedCost=" + callerBestReducedCost
                    + " directBestReducedCost=" + directBestReducedCost
                    + " tolerance=" + allowedError
                    + " absoluteTolerance=" + tolerance);
        }
    }

    private static String routeKey(List<Integer> routeIds) {
        StringBuilder sb = new StringBuilder();
        for (int vertexId : routeIds) {
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(vertexId);
        }
        return sb.toString();
    }

    private static String columnName(String prefix, List<Integer> routeIds) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int vertexId : routeIds) {
            sb.append('_').append(vertexId);
        }
        return sb.toString();
    }
}
