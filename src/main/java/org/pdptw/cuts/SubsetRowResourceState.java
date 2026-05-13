package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.util.Objects;

public final class SubsetRowResourceState {
    private final SubsetRowCut cut;
    private final int relevantVisitCount;

    private SubsetRowResourceState(SubsetRowCut cut, int relevantVisitCount) {
        this.cut = Objects.requireNonNull(cut, "cut");
        if (relevantVisitCount < 0) {
            throw new IllegalArgumentException("relevantVisitCount must be non-negative: " + relevantVisitCount);
        }
        this.relevantVisitCount = relevantVisitCount;
    }

    public static SubsetRowResourceState empty(SubsetRowCut cut) {
        return new SubsetRowResourceState(cut, 0);
    }

    public static SubsetRowResourceState of(SubsetRowCut cut, int relevantVisitCount) {
        return new SubsetRowResourceState(cut, relevantVisitCount);
    }

    public SubsetRowCut cut() {
        return cut;
    }

    public int relevantVisitCount() {
        return relevantVisitCount;
    }

    public int coefficient() {
        return relevantVisitCount / cut.l();
    }

    public int sr() {
        return relevantVisitCount % cut.l();
    }

    public int parity() {
        return sr();
    }

    public SubsetRowResourceState afterRelevantVisit() {
        return new SubsetRowResourceState(cut, relevantVisitCount + 1);
    }

    public SubsetRowResourceState afterForwardPickup(Instance instance, int vertexId) {
        Objects.requireNonNull(instance, "instance");
        Vertex vertex = instance.vertex(vertexId);
        if (vertex.isPickup() && cut.containsRequest(vertex.requestId())) {
            return afterRelevantVisit();
        }
        return this;
    }

    public SubsetRowResourceState afterBackwardDelivery(Instance instance, int vertexId) {
        Objects.requireNonNull(instance, "instance");
        Vertex vertex = instance.vertex(vertexId);
        if (vertex.isDelivery() && cut.containsRequest(vertex.requestId())) {
            return afterRelevantVisit();
        }
        return this;
    }

    public int nextRelevantVisitCoefficientDelta() {
        return afterRelevantVisit().coefficient() - coefficient();
    }
}
