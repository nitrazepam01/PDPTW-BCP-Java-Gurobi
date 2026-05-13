package org.pdptw.pricing;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LabelExtender {
    public static final double FORWARD_ALPHA = ReducedCostMatrices.FORWARD_ALPHA;
    public static final double BACKWARD_ALPHA = ReducedCostMatrices.BACKWARD_ALPHA;
    private static final double EPS = 1.0e-9;

    private LabelExtender() {
    }

    public static Optional<ForwardLabel> forwardExtend(
            Instance instance,
            ForwardLabel label,
            int nextVertexId) {
        return forwardExtend(ReducedCostMatrices.fromInstanceDuals(instance), label, nextVertexId);
    }

    public static Optional<ForwardLabel> forwardExtend(
            ReducedCostMatrices matrices,
            ForwardLabel label,
            int nextVertexId) {
        return forwardExtend(PricingContext.noCuts(matrices), label, nextVertexId);
    }

    public static Optional<ForwardLabel> forwardExtend(
            PricingContext context,
            ForwardLabel label,
            int nextVertexId) {
        Objects.requireNonNull(context, "context");
        ReducedCostMatrices matrices = context.matrices();
        Objects.requireNonNull(matrices, "matrices");
        Instance instance = matrices.instance();
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(label, "label");
        if (!instance.hasVertex(nextVertexId)) {
            return Optional.empty();
        }
        if (nextVertexId == instance.startDepotId()) {
            return Optional.empty();
        }

        Vertex current = instance.vertex(label.lastVertexId());
        Vertex next = instance.vertex(nextVertexId);
        long completed = label.completedMask();
        long open = label.openMask();

        if (nextVertexId == instance.endDepotId()) {
            if (open != 0L || completed == 0L) {
                return Optional.empty();
            }
        } else if (next.isPickup()) {
            int requestId = next.requestId();
            if (BitSetOps.contains(open, requestId) || BitSetOps.contains(completed, requestId)) {
                return Optional.empty();
            }
            open = BitSetOps.add(open, requestId);
        } else if (next.isDelivery()) {
            int requestId = next.requestId();
            if (!BitSetOps.contains(open, requestId)) {
                return Optional.empty();
            }
            open = BitSetOps.remove(open, requestId);
            completed = BitSetOps.add(completed, requestId);
        } else {
            return Optional.empty();
        }

        double arrival = label.time()
                + current.serviceTime()
                + instance.travelTime(label.lastVertexId(), nextVertexId);
        double time = Math.max(arrival, next.readyTime());
        if (time > next.dueTime() + EPS) {
            return Optional.empty();
        }

        int load = label.load() + next.demand();
        if (load < 0 || load > instance.vehicleCapacity()) {
            return Optional.empty();
        }

        double reducedCost = label.reducedCost()
                + context.forwardArcReducedCost(label.lastVertexId(), nextVertexId);
        PricingContext.SrTransition srTransition =
                context.forwardSubsetRowTransition(label.subsetRowRelevantVisitCounts(), nextVertexId);
        reducedCost += srTransition.reducedCostAdjustment();
        PricingContext.SetOutflowTransition setOutflowTransition =
                context.forwardSetOutflowTransition(label.setOutflowStates(), nextVertexId);
        reducedCost += setOutflowTransition.reducedCostAdjustment();
        List<Integer> vertexIds = new ArrayList<Integer>(label.vertexIds());
        vertexIds.add(Integer.valueOf(nextVertexId));
        return Optional.of(new ForwardLabel(
                nextVertexId,
                reducedCost,
                time,
                load,
                completed,
                open,
                srTransition.counts(),
                setOutflowTransition.states(),
                vertexIds));
    }

    public static Optional<BackwardLabel> backwardExtend(
            Instance instance,
            BackwardLabel label,
            int previousVertexId) {
        return backwardExtend(ReducedCostMatrices.fromInstanceDuals(instance), label, previousVertexId);
    }

    public static Optional<BackwardLabel> backwardExtend(
            ReducedCostMatrices matrices,
            BackwardLabel label,
            int previousVertexId) {
        return backwardExtend(PricingContext.noCuts(matrices), label, previousVertexId);
    }

    public static Optional<BackwardLabel> backwardExtend(
            PricingContext context,
            BackwardLabel label,
            int previousVertexId) {
        Objects.requireNonNull(context, "context");
        ReducedCostMatrices matrices = context.matrices();
        Objects.requireNonNull(matrices, "matrices");
        Instance instance = matrices.instance();
        Objects.requireNonNull(label, "label");
        if (!instance.hasVertex(previousVertexId)) {
            return Optional.empty();
        }
        if (previousVertexId == instance.endDepotId()) {
            return Optional.empty();
        }

        Vertex previous = instance.vertex(previousVertexId);
        long completed = label.completedMask();
        long open = label.openMask();

        if (previousVertexId == instance.startDepotId()) {
            if (open != 0L || completed == 0L) {
                return Optional.empty();
            }
        } else if (previous.isDelivery()) {
            int requestId = previous.requestId();
            if (BitSetOps.contains(open, requestId) || BitSetOps.contains(completed, requestId)) {
                return Optional.empty();
            }
            open = BitSetOps.add(open, requestId);
        } else if (previous.isPickup()) {
            int requestId = previous.requestId();
            if (!BitSetOps.contains(open, requestId)) {
                return Optional.empty();
            }
            open = BitSetOps.remove(open, requestId);
            completed = BitSetOps.add(completed, requestId);
        } else {
            return Optional.empty();
        }

        double latestServiceStart = Math.min(
                previous.dueTime(),
                label.time()
                        - previous.serviceTime()
                        - instance.travelTime(previousVertexId, label.firstVertexId()));
        if (latestServiceStart < previous.readyTime() - EPS) {
            return Optional.empty();
        }

        int load = label.load() - previous.demand();
        if (load < 0 || load > instance.vehicleCapacity()) {
            return Optional.empty();
        }

        double reducedCost = label.reducedCost()
                + context.backwardArcReducedCost(previousVertexId, label.firstVertexId());
        PricingContext.SrTransition srTransition =
                context.backwardSubsetRowTransition(label.subsetRowRelevantVisitCounts(), previousVertexId);
        reducedCost += srTransition.reducedCostAdjustment();
        PricingContext.SetOutflowTransition setOutflowTransition =
                context.backwardSetOutflowTransition(label.setOutflowStates(), previousVertexId);
        reducedCost += setOutflowTransition.reducedCostAdjustment();
        List<Integer> vertexIds = new ArrayList<Integer>();
        vertexIds.add(Integer.valueOf(previousVertexId));
        vertexIds.addAll(label.vertexIds());
        return Optional.of(new BackwardLabel(
                previousVertexId,
                reducedCost,
                latestServiceStart,
                load,
                completed,
                open,
                srTransition.counts(),
                setOutflowTransition.states(),
                vertexIds));
    }

    public static double arcReducedCost(Instance instance, int from, int to, double alpha) {
        return instance.travelCost(from, to)
                - 0.5 * splitVertexDual(instance, from, alpha)
                - 0.5 * splitVertexDual(instance, to, alpha);
    }

    public static double splitVertexDual(Instance instance, int vertexId, double alpha) {
        if (vertexId == instance.startDepotId() || vertexId == instance.endDepotId()) {
            return instance.fleetDual();
        }
        Vertex vertex = instance.vertex(vertexId);
        if (vertex.isPickup()) {
            return alpha * instance.requestDual(vertex.requestId());
        }
        if (vertex.isDelivery()) {
            return (1.0 - alpha) * instance.requestDual(vertex.requestId());
        }
        return 0.0;
    }
}
