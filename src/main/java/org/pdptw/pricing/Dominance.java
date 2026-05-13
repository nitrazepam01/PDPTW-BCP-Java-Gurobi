package org.pdptw.pricing;

import org.pdptw.core.BitSetOps;

import java.util.Arrays;

public final class Dominance {
    public static final double DEFAULT_TOLERANCE = 1.0e-9;

    private Dominance() {
    }

    public static boolean forwardStrongDominates(ForwardLabel left, ForwardLabel right) {
        throw new IllegalStateException("Forward strong dominance requires an explicit DTI gate.");
    }

    public static boolean forwardStrongDominates(ForwardLabel left, ForwardLabel right, boolean dtiVerified) {
        return forwardStrongDominates(left, right, dtiVerified, DEFAULT_TOLERANCE);
    }

    public static boolean forwardStrongDominates(
            ForwardLabel left,
            ForwardLabel right,
            boolean dtiVerified,
            double tolerance) {
        if (!dtiVerified) {
            throw new IllegalStateException("Forward strong dominance requires a verified DTI matrix.");
        }
        return forwardStrongDominatesAfterDti(left, right, tolerance);
    }

    private static boolean forwardStrongDominatesAfterDti(
            ForwardLabel left,
            ForwardLabel right,
            double tolerance) {
        if (left.lastVertexId() != right.lastVertexId()) {
            return false;
        }
        return left.reducedCost() <= right.reducedCost() + tolerance
                && left.time() <= right.time() + tolerance
                && BitSetOps.isSubset(left.completedMask(), right.completedMask())
                && BitSetOps.isSubset(left.openMask(), right.openMask())
                && Arrays.equals(left.subsetRowRelevantVisitCountsRaw(), right.subsetRowRelevantVisitCountsRaw())
                && Arrays.equals(left.setOutflowStatesRaw(), right.setOutflowStatesRaw());
    }

    public static boolean backwardStrongDominates(BackwardLabel left, BackwardLabel right) {
        throw new IllegalStateException("Backward strong dominance requires an explicit PTI gate.");
    }

    public static boolean backwardStrongDominates(BackwardLabel left, BackwardLabel right, boolean ptiVerified) {
        return backwardStrongDominates(left, right, ptiVerified, DEFAULT_TOLERANCE);
    }

    public static boolean backwardStrongDominates(
            BackwardLabel left,
            BackwardLabel right,
            boolean ptiVerified,
            double tolerance) {
        if (!ptiVerified) {
            throw new IllegalStateException("Backward strong dominance requires a verified PTI matrix.");
        }
        return backwardStrongDominatesAfterPti(left, right, tolerance);
    }

    private static boolean backwardStrongDominatesAfterPti(
            BackwardLabel left,
            BackwardLabel right,
            double tolerance) {
        if (left.firstVertexId() != right.firstVertexId()) {
            return false;
        }
        return left.reducedCost() <= right.reducedCost() + tolerance
                && left.time() + tolerance >= right.time()
                && BitSetOps.isSubset(left.completedMask(), right.completedMask())
                && BitSetOps.isSubset(left.openMask(), right.openMask())
                && Arrays.equals(left.subsetRowRelevantVisitCountsRaw(), right.subsetRowRelevantVisitCountsRaw())
                && Arrays.equals(left.setOutflowStatesRaw(), right.setOutflowStatesRaw());
    }
}
