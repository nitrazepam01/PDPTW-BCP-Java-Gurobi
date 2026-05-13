package org.pdptw.pricing;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.core.RouteChecker;
import org.pdptw.core.Vertex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BidirectionalMerger {
    public static final double DEFAULT_REDUCED_COST_AUDIT_TOLERANCE = 1.0e-7;
    private static final double RELATIVE_REDUCED_COST_AUDIT_TOLERANCE = 1.0e-15;
    private static final double EPS = 1.0e-9;

    private final double reducedCostAuditTolerance;

    public BidirectionalMerger() {
        this(DEFAULT_REDUCED_COST_AUDIT_TOLERANCE);
    }

    public BidirectionalMerger(double reducedCostAuditTolerance) {
        this.reducedCostAuditTolerance = requireTolerance(
                reducedCostAuditTolerance,
                "reducedCostAuditTolerance");
    }

    public Optional<MergeResult> merge(
            ReducedCostMatrices matrices,
            ForwardLabel forward,
            BackwardLabel backward) {
        return merge(
                matrices,
                forward,
                backward,
                this::defaultMergeCorrection,
                matrices::directReducedCost);
    }

    public Optional<MergeResult> merge(
            PricingContext context,
            ForwardLabel forward,
            BackwardLabel backward) {
        Objects.requireNonNull(context, "context");
        return merge(
                context.matrices(),
                forward,
                backward,
                (matrices, activeForward, activeBackward) ->
                        context.mergeCorrection(activeForward, activeBackward),
                context::directReducedCost);
    }

    public Optional<MergeResult> merge(
            ReducedCostMatrices matrices,
            ForwardLabel forward,
            BackwardLabel backward,
            MergeCorrection mergeCorrection,
            RouteReducedCostAudit directReducedCostAudit) {
        Objects.requireNonNull(matrices, "matrices");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(backward, "backward");
        Objects.requireNonNull(mergeCorrection, "mergeCorrection");
        Objects.requireNonNull(directReducedCostAudit, "directReducedCostAudit");
        if (!compatible(matrices.instance(), forward, backward)) {
            return Optional.empty();
        }

        List<Integer> route = reconstructRoute(forward, backward);
        RouteChecker.Result feasibility = new RouteChecker().check(matrices.instance(), new Route(route));
        if (!feasibility.feasible()) {
            return Optional.empty();
        }

        double rawReducedCost = forward.reducedCost() + backward.reducedCost();
        double mergedReducedCost = rawReducedCost + mergeCorrection.correction(matrices, forward, backward);
        double directReducedCost = directReducedCostAudit.directReducedCost(route);
        auditReducedCost(route, mergedReducedCost, directReducedCost);
        return Optional.of(new MergeResult(route, mergedReducedCost, rawReducedCost, directReducedCost));
    }

    public double reducedCostAuditTolerance() {
        return reducedCostAuditTolerance;
    }

    public boolean compatible(Instance instance, ForwardLabel forward, BackwardLabel backward) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(backward, "backward");
        if (forward.lastVertexId() != backward.firstVertexId()) {
            return false;
        }
        if ((forward.completedMask() & backward.completedMask()) != 0L) {
            return false;
        }
        if (forward.time() > backward.time() + EPS) {
            return false;
        }
        return openRequestsCompatible(instance, forward, backward);
    }

    public List<Integer> reconstructRoute(ForwardLabel forward, BackwardLabel backward) {
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(backward, "backward");
        if (forward.lastVertexId() != backward.firstVertexId()) {
            throw new IllegalArgumentException("labels must meet at the same vertex");
        }
        ArrayList<Integer> route = new ArrayList<Integer>(forward.vertexIds());
        List<Integer> suffix = backward.vertexIds();
        for (int i = 1; i < suffix.size(); i++) {
            route.add(suffix.get(i));
        }
        return Collections.unmodifiableList(route);
    }

    public double correctedReducedCost(
            ReducedCostMatrices matrices,
            ForwardLabel forward,
            BackwardLabel backward) {
        Objects.requireNonNull(matrices, "matrices");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(backward, "backward");
        return forward.reducedCost()
                + backward.reducedCost()
                + defaultMergeCorrection(matrices, forward, backward);
    }

    private double defaultMergeCorrection(
            ReducedCostMatrices matrices,
            ForwardLabel forward,
            BackwardLabel backward) {
        long intersection = forward.openMask() & backward.openMask();
        long symmetricDifference = (forward.openMask() | backward.openMask()) & ~intersection;
        return requestDualSum(matrices, intersection)
                + 0.5 * requestDualSum(matrices, symmetricDifference);
    }

    private static boolean openRequestsCompatible(
            Instance instance,
            ForwardLabel forward,
            BackwardLabel backward) {
        Vertex mergeVertex = instance.vertex(forward.lastVertexId());
        if (mergeVertex.isPickup()) {
            return backward.openMask() == BitSetOps.remove(forward.openMask(), mergeVertex.requestId());
        }
        if (mergeVertex.isDelivery()) {
            return forward.openMask() == BitSetOps.remove(backward.openMask(), mergeVertex.requestId());
        }
        return forward.openMask() == backward.openMask();
    }

    private static double requestDualSum(ReducedCostMatrices matrices, long mask) {
        double sum = 0.0;
        for (int requestId = 1; requestId <= matrices.instance().nRequests(); requestId++) {
            if (BitSetOps.contains(mask, requestId)) {
                sum += matrices.requestDual(requestId);
            }
        }
        return sum;
    }

    private void auditReducedCost(
            List<Integer> route,
            double mergedReducedCost,
            double directReducedCost) {
        double error = Math.abs(mergedReducedCost - directReducedCost);
        double scale = Math.max(1.0, Math.max(Math.abs(mergedReducedCost), Math.abs(directReducedCost)));
        double allowedError = Math.max(
                reducedCostAuditTolerance,
                RELATIVE_REDUCED_COST_AUDIT_TOLERANCE * scale);
        if (error > allowedError) {
            throw new IllegalStateException("bidirectional merge reduced-cost audit failed"
                    + " route=" + route
                    + " mergedReducedCost=" + mergedReducedCost
                    + " directReducedCost=" + directReducedCost
                    + " error=" + error
                    + " tolerance=" + allowedError
                    + " absoluteTolerance=" + reducedCostAuditTolerance);
        }
    }

    private static double requireTolerance(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative: " + value);
        }
        return value;
    }

    public static final class MergeResult {
        private final List<Integer> route;
        private final double mergedReducedCost;
        private final double rawReducedCost;
        private final double directReducedCost;

        private MergeResult(
                List<Integer> route,
                double mergedReducedCost,
                double rawReducedCost,
                double directReducedCost) {
            this.route = Collections.unmodifiableList(new ArrayList<Integer>(route));
            this.mergedReducedCost = requireFinite(mergedReducedCost, "mergedReducedCost");
            this.rawReducedCost = requireFinite(rawReducedCost, "rawReducedCost");
            this.directReducedCost = requireFinite(directReducedCost, "directReducedCost");
        }

        public List<Integer> route() {
            return route;
        }

        public List<Integer> vertexIds() {
            return route;
        }

        public double mergedReducedCost() {
            return mergedReducedCost;
        }

        public double reducedCost() {
            return mergedReducedCost;
        }

        public double rawReducedCost() {
            return rawReducedCost;
        }

        public double directReducedCost() {
            return directReducedCost;
        }

        private static double requireFinite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite: " + value);
            }
            return value;
        }
    }

    @FunctionalInterface
    public interface MergeCorrection {
        double correction(ReducedCostMatrices matrices, ForwardLabel forward, BackwardLabel backward);
    }

    @FunctionalInterface
    public interface RouteReducedCostAudit {
        double directReducedCost(List<Integer> route);
    }
}
