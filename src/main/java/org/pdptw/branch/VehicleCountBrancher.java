package org.pdptw.branch;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class VehicleCountBrancher {
    public static final double DEFAULT_TOLERANCE = 1.0e-7;

    private final double tolerance;

    public VehicleCountBrancher() {
        this(DEFAULT_TOLERANCE);
    }

    public VehicleCountBrancher(double tolerance) {
        this.tolerance = requireTolerance(tolerance);
    }

    public Optional<BranchDecision> branch(
            BranchNode parent,
            List<BranchDecision.RouteValue> solution) {
        Objects.requireNonNull(parent, "parent");
        double value = vehicleCount(solution);
        if (!isFractional(value, tolerance)) {
            return Optional.empty();
        }
        int floor = (int) Math.floor(value);
        int ceil = (int) Math.ceil(value);
        VehicleCountConstraint left = VehicleCountConstraint.lessOrEqual(floor);
        VehicleCountConstraint right = VehicleCountConstraint.greaterOrEqual(ceil);
        return Optional.of(new BranchDecision(
                BranchConstraint.Type.VEHICLE_COUNT,
                value,
                parent.child(left),
                parent.child(right)));
    }

    public boolean isFractional(double value) {
        return isFractional(value, tolerance);
    }

    public static double vehicleCount(List<BranchDecision.RouteValue> solution) {
        Objects.requireNonNull(solution, "solution");
        double count = 0.0;
        for (BranchDecision.RouteValue routeValue : solution) {
            count += routeValue.lambda() * routeValue.fleetCoefficient();
        }
        return requireFinite(count, "vehicleCount");
    }

    public static boolean isFractional(double value, double tolerance) {
        requireFinite(value, "value");
        requireTolerance(tolerance);
        return Math.abs(value - Math.rint(value)) > tolerance;
    }

    private static double requireTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        return tolerance;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
