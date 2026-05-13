package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.master.GurobiRmp;
import org.pdptw.master.RouteColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SubsetRowCutSeparator {
    private SubsetRowCutSeparator() {
    }

    public static List<SubsetRowCutRow> violatedL2Triples(
            Instance instance,
            Collection<GurobiRmp.ColumnValue> solution,
            Collection<? extends MasterCutRow> activeCutRows,
            double tolerance) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        double tol = requireTolerance(tolerance);
        Set<String> activeNames = activeNames(activeCutRows);
        Set<String> activeSubsetRowKeys = activeSubsetRowKeys(activeCutRows);
        ArrayList<SubsetRowCutRow> violated = new ArrayList<SubsetRowCutRow>();
        List<Integer> requestIds = instance.requestIds();
        for (int a = 0; a < requestIds.size(); a++) {
            for (int b = a + 1; b < requestIds.size(); b++) {
                for (int c = b + 1; c < requestIds.size(); c++) {
                    SubsetRowCutRow row = SubsetRowCutRow.ofL2Triple(
                            cutName(requestIds.get(a), requestIds.get(b), requestIds.get(c)),
                            requestIds.get(a).intValue(),
                            requestIds.get(b).intValue(),
                            requestIds.get(c).intValue());
                    if (!activeNames.contains(row.name())
                            && !activeSubsetRowKeys.contains(subsetRowKey(row))
                            && activity(instance, row, solution) > row.rhs() + tol) {
                        violated.add(row);
                    }
                }
            }
        }
        return List.copyOf(violated);
    }

    public static double activity(
            Instance instance,
            MasterCutRow row,
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
        return MasterCutRow.requireFinite(lhs, "subset-row activity");
    }

    private static String cutName(Integer first, Integer second, Integer third) {
        return "sr-U" + first + "-" + second + "-" + third + "-l2";
    }

    private static Set<String> activeNames(Collection<? extends MasterCutRow> activeCutRows) {
        HashSet<String> names = new HashSet<String>();
        for (MasterCutRow row : activeCutRows) {
            names.add(Objects.requireNonNull(row, "activeCutRows contains null").name());
        }
        return names;
    }

    private static Set<String> activeSubsetRowKeys(Collection<? extends MasterCutRow> activeCutRows) {
        HashSet<String> keys = new HashSet<String>();
        for (MasterCutRow row : activeCutRows) {
            if (row instanceof SubsetRowCutRow subsetRow) {
                keys.add(subsetRowKey(subsetRow));
            }
        }
        return keys;
    }

    private static String subsetRowKey(SubsetRowCutRow row) {
        SubsetRowCut template = Objects.requireNonNull(row, "row").template();
        return template.l() + ":" + template.requestMask();
    }

    private static double requireTolerance(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + value);
        }
        return value;
    }
}
