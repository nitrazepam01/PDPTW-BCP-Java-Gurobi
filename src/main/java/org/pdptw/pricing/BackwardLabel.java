package org.pdptw.pricing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BackwardLabel {
    private final int firstVertexId;
    private final double reducedCost;
    private final double time;
    private final int load;
    private final long completedMask;
    private final long openMask;
    private final int[] subsetRowRelevantVisitCounts;
    private final int[] setOutflowStates;
    private final List<Integer> vertexIds;

    public BackwardLabel(
            int firstVertexId,
            double reducedCost,
            double time,
            int load,
            long completedMask,
            long openMask,
            List<Integer> vertexIds) {
        this(firstVertexId, reducedCost, time, load, completedMask, openMask, new int[0], vertexIds);
    }

    public BackwardLabel(
            int firstVertexId,
            double reducedCost,
            double time,
            int load,
            long completedMask,
            long openMask,
            int[] subsetRowRelevantVisitCounts,
            List<Integer> vertexIds) {
        this(firstVertexId, reducedCost, time, load, completedMask, openMask,
                subsetRowRelevantVisitCounts, new int[0], vertexIds);
    }

    public BackwardLabel(
            int firstVertexId,
            double reducedCost,
            double time,
            int load,
            long completedMask,
            long openMask,
            int[] subsetRowRelevantVisitCounts,
            int[] setOutflowStates,
            List<Integer> vertexIds) {
        this.firstVertexId = firstVertexId;
        this.reducedCost = requireFinite(reducedCost, "reducedCost");
        this.time = requireFinite(time, "time");
        this.load = load;
        this.completedMask = completedMask;
        this.openMask = openMask;
        this.subsetRowRelevantVisitCounts = copyCounts(subsetRowRelevantVisitCounts);
        this.setOutflowStates = copySetOutflowStates(setOutflowStates);
        this.vertexIds = Collections.unmodifiableList(new ArrayList<Integer>(vertexIds));
    }

    public int firstVertexId() {
        return firstVertexId;
    }

    public int firstVertex() {
        return firstVertexId;
    }

    public double reducedCost() {
        return reducedCost;
    }

    public double rc() {
        return reducedCost;
    }

    public double time() {
        return time;
    }

    public int load() {
        return load;
    }

    public long completedMask() {
        return completedMask;
    }

    public long openMask() {
        return openMask;
    }

    public List<Integer> vertexIds() {
        return vertexIds;
    }

    public int[] subsetRowRelevantVisitCounts() {
        return subsetRowRelevantVisitCounts.clone();
    }

    int[] subsetRowRelevantVisitCountsRaw() {
        return subsetRowRelevantVisitCounts;
    }

    public int subsetRowRelevantVisitCount(int index) {
        if (index < 0 || index >= subsetRowRelevantVisitCounts.length) {
            throw new IllegalArgumentException("subset-row cut index out of range: " + index);
        }
        return subsetRowRelevantVisitCounts[index];
    }

    public int[] setOutflowStates() {
        return setOutflowStates.clone();
    }

    int[] setOutflowStatesRaw() {
        return setOutflowStates;
    }

    public List<Integer> route() {
        return vertexIds;
    }

    public boolean hasOpenRequests() {
        return openMask != 0L;
    }

    public boolean servesAtLeastOneRequest() {
        return completedMask != 0L;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    private static int[] copyCounts(int[] source) {
        if (source == null) {
            throw new NullPointerException("subsetRowRelevantVisitCounts");
        }
        int[] copy = source.clone();
        for (int count : copy) {
            if (count < 0) {
                throw new IllegalArgumentException("subset-row visit count must be non-negative: "
                        + Arrays.toString(source));
            }
        }
        return copy;
    }

    private static int[] copySetOutflowStates(int[] source) {
        if (source == null) {
            throw new NullPointerException("setOutflowStates");
        }
        int[] copy = source.clone();
        for (int state : copy) {
            SetOutflowPricingRule.requireState(state, "set-outflow state");
        }
        return copy;
    }
}
