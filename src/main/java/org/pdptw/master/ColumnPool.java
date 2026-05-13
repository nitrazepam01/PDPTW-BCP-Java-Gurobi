package org.pdptw.master;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ColumnPool {
    private final Map<String, RouteColumn> bySignature = new LinkedHashMap<>();
    private List<RouteColumn> columnsView;

    public boolean add(RouteColumn column) {
        Objects.requireNonNull(column, "column");
        if (bySignature.containsKey(column.signature())) {
            return false;
        }
        bySignature.put(column.signature(), column);
        columnsView = null;
        return true;
    }

    public int addAll(Collection<RouteColumn> columns) {
        Objects.requireNonNull(columns, "columns");
        int added = 0;
        for (RouteColumn column : columns) {
            if (add(column)) {
                added++;
            }
        }
        return added;
    }

    public boolean contains(RouteColumn column) {
        Objects.requireNonNull(column, "column");
        return bySignature.containsKey(column.signature());
    }

    public int size() {
        return bySignature.size();
    }

    public boolean isEmpty() {
        return bySignature.isEmpty();
    }

    public List<RouteColumn> columns() {
        if (columnsView == null) {
            columnsView = Collections.unmodifiableList(new ArrayList<>(bySignature.values()));
        }
        return columnsView;
    }

    public List<RouteColumn> realColumns() {
        List<RouteColumn> result = new ArrayList<>();
        for (RouteColumn column : bySignature.values()) {
            if (column.isRealRoute()) {
                result.add(column);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<RouteColumn> artificialColumns() {
        List<RouteColumn> result = new ArrayList<>();
        for (RouteColumn column : bySignature.values()) {
            if (column.isArtificial()) {
                result.add(column);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
