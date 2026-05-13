package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RobustCutSeparator {
    private RobustCutSeparator() {
    }

    public static List<RobustCutRow> violatedRows(
            Instance instance,
            Collection<GurobiRmp.ColumnValue> solution,
            Collection<? extends MasterCutRow> activeCutRows,
            Collection<? extends RobustCutRow> candidateRows,
            double tolerance) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        Objects.requireNonNull(candidateRows, "candidateRows");
        double tol = requireTolerance(tolerance);
        Set<String> activeNames = activeNames(activeCutRows);
        Set<String> activeSemanticKeys = activeSemanticKeys(activeCutRows);
        ArrayList<RobustCutRow> violated = new ArrayList<RobustCutRow>();
        for (RobustCutRow row : candidateRows) {
            RobustCutRow candidate = Objects.requireNonNull(row, "candidateRows contains null");
            candidate.validateForInstance(instance);
            String semanticKey = semanticKey(candidate);
            if (!activeNames.contains(candidate.name())
                    && !activeSemanticKeys.contains(semanticKey)
                    && violates(candidate.sense(), activity(instance, candidate, solution), candidate.rhs(), tol)) {
                violated.add(candidate);
                activeNames.add(candidate.name());
                activeSemanticKeys.add(semanticKey);
            }
        }
        return List.copyOf(violated);
    }

    public static double activity(
            Instance instance,
            RobustCutRow row,
            Collection<GurobiRmp.ColumnValue> solution) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(solution, "solution");
        double lhs = 0.0;
        for (GurobiRmp.ColumnValue value : solution) {
            Objects.requireNonNull(value, "solution contains null");
            RouteColumn column = value.column();
            if (column.isRealRoute()) {
                lhs += row.coefficient(instance, column) * value.value();
            }
        }
        return MasterCutRow.requireFinite(lhs, "robust-cut activity");
    }

    private static boolean violates(
            MasterCutRow.Sense sense,
            double activity,
            double rhs,
            double tolerance) {
        if (sense == MasterCutRow.Sense.LESS_EQUAL) {
            return activity > rhs + tolerance;
        }
        if (sense == MasterCutRow.Sense.GREATER_EQUAL) {
            return activity < rhs - tolerance;
        }
        return Math.abs(activity - rhs) > tolerance;
    }

    private static Set<String> activeNames(Collection<? extends MasterCutRow> activeCutRows) {
        HashSet<String> names = new HashSet<String>();
        for (MasterCutRow row : activeCutRows) {
            names.add(Objects.requireNonNull(row, "activeCutRows contains null").name());
        }
        return names;
    }

    private static Set<String> activeSemanticKeys(Collection<? extends MasterCutRow> activeCutRows) {
        HashSet<String> keys = new HashSet<String>();
        for (MasterCutRow row : activeCutRows) {
            MasterCutRow active = Objects.requireNonNull(row, "activeCutRows contains null");
            if (active instanceof RobustCutRow) {
                keys.add(semanticKey((RobustCutRow) active));
            }
        }
        return keys;
    }

    private static String semanticKey(RobustCutRow row) {
        StringBuilder key = new StringBuilder();
        key.append(row.sense())
                .append('|')
                .append(Double.doubleToLongBits(row.rhs()));
        ArrayList<Map.Entry<RobustCut.Arc, Double>> entries =
                new ArrayList<Map.Entry<RobustCut.Arc, Double>>(row.arcCoefficients().entrySet());
        entries.sort((left, right) -> {
            int byFrom = Integer.compare(left.getKey().from(), right.getKey().from());
            if (byFrom != 0) {
                return byFrom;
            }
            return Integer.compare(left.getKey().to(), right.getKey().to());
        });
        for (Map.Entry<RobustCut.Arc, Double> entry : entries) {
            RobustCut.Arc arc = entry.getKey();
            key.append('|')
                    .append(arc.from())
                    .append("->")
                    .append(arc.to())
                    .append('=')
                    .append(Double.doubleToLongBits(entry.getValue().doubleValue()));
        }
        return key.toString();
    }

    private static double requireTolerance(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + value);
        }
        return value;
    }
}
