package org.pdptw.branch;

import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.master.RouteColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BranchMasterRow implements MasterCutRow {
    private final BranchConstraint constraint;

    private BranchMasterRow(BranchConstraint constraint) {
        this.constraint = Objects.requireNonNull(constraint, "constraint");
    }

    public static BranchMasterRow of(BranchConstraint constraint) {
        return new BranchMasterRow(constraint);
    }

    public BranchConstraint constraint() {
        return constraint;
    }

    @Override
    public String name() {
        return "branch_" + constraint.name();
    }

    @Override
    public Sense sense() {
        return switch (constraint.sense()) {
            case LESS_OR_EQUAL -> Sense.LESS_EQUAL;
            case GREATER_OR_EQUAL -> Sense.GREATER_EQUAL;
        };
    }

    @Override
    public double rhs() {
        return constraint.rhs();
    }

    @Override
    public double coefficient(Instance instance, RouteColumn column) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(column, "column");
        if (column.isArtificial()) {
            return 0.0;
        }
        if (constraint instanceof VehicleCountConstraint) {
            return column.fleetCoefficient();
        }
        if (constraint instanceof SetOutflowConstraint setOutflow) {
            return SetOutflowConstraint.routeCoefficient(
                    BranchDecision.RouteValue.withRequestVisits(
                            column.name(),
                            1.0,
                            column.fleetCoefficient(),
                            requestVisitSequence(instance, column)),
                    setOutflow.requestSet());
        }
        throw new IllegalArgumentException("unsupported branch constraint type: " + constraint.type());
    }

    public double routeCoefficient(Instance instance, RouteColumn column) {
        return coefficient(instance, column);
    }

    public double routePrice(Instance instance, RouteColumn column, double pricingDual) {
        if (!Double.isFinite(pricingDual)) {
            throw new IllegalArgumentException("pricingDual must be finite: " + pricingDual);
        }
        return pricingDual * coefficient(instance, column);
    }

    static List<Integer> requestVisitSequence(Instance instance, RouteColumn column) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(column, "column");
        ArrayList<Integer> visits = new ArrayList<Integer>();
        for (int vertexId : column.vertexIds()) {
            Vertex vertex = instance.vertex(vertexId);
            if (vertex.isPickup() || vertex.isDelivery()) {
                visits.add(Integer.valueOf(vertex.requestId()));
            }
        }
        return List.copyOf(visits);
    }
}
