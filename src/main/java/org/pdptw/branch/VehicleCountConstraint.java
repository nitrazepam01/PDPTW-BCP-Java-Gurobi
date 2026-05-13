package org.pdptw.branch;

import java.util.List;

public final class VehicleCountConstraint implements BranchConstraint {
    private final Sense sense;
    private final int rhs;

    private VehicleCountConstraint(Sense sense, int rhs) {
        this.sense = sense;
        this.rhs = requireNonNegative(rhs, "rhs");
    }

    public static VehicleCountConstraint lessOrEqual(int rhs) {
        return new VehicleCountConstraint(Sense.LESS_OR_EQUAL, rhs);
    }

    public static VehicleCountConstraint greaterOrEqual(int rhs) {
        return new VehicleCountConstraint(Sense.GREATER_OR_EQUAL, rhs);
    }

    @Override
    public Type type() {
        return Type.VEHICLE_COUNT;
    }

    @Override
    public Sense sense() {
        return sense;
    }

    @Override
    public int rhs() {
        return rhs;
    }

    @Override
    public String name() {
        return "vehicle_count_" + (sense == Sense.LESS_OR_EQUAL ? "le_" : "ge_") + rhs;
    }

    @Override
    public String expression() {
        return "sum_lambda " + sense.symbol() + " " + rhs;
    }

    @Override
    public boolean isSatisfied(List<BranchDecision.RouteValue> solution, double tolerance) {
        return sense.accepts(VehicleCountBrancher.vehicleCount(solution), rhs, tolerance);
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }
}
