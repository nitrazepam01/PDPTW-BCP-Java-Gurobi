package org.pdptw.branch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BranchNode {
    private final String id;
    private final int depth;
    private final List<BranchConstraint> constraints;
    private final BranchConstraint inheritedBy;
    private final double lowerBound;

    private BranchNode(
            String id,
            int depth,
            List<BranchConstraint> constraints,
            BranchConstraint inheritedBy,
            double lowerBound) {
        this.id = requireNonBlank(id, "id");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be non-negative: " + depth);
        }
        this.depth = depth;
        this.constraints = Collections.unmodifiableList(new ArrayList<BranchConstraint>(constraints));
        for (BranchConstraint constraint : this.constraints) {
            Objects.requireNonNull(constraint, "constraints contains null");
        }
        this.inheritedBy = inheritedBy;
        this.lowerBound = requireFinite(lowerBound, "lowerBound");
    }

    public static BranchNode root() {
        return new BranchNode("root", 0, List.of(), null, 0.0);
    }

    public static BranchNode root(String id) {
        return new BranchNode(id, 0, List.of(), null, 0.0);
    }

    public BranchNode child(BranchConstraint constraint) {
        Objects.requireNonNull(constraint, "constraint");
        ArrayList<BranchConstraint> childConstraints = new ArrayList<BranchConstraint>(constraints);
        childConstraints.add(constraint);
        return new BranchNode(id + "/" + constraint.name(), depth + 1, childConstraints, constraint, lowerBound);
    }

    public BranchNode withLowerBound(double value) {
        return new BranchNode(id, depth, constraints, inheritedBy, value);
    }

    public String id() {
        return id;
    }

    public int depth() {
        return depth;
    }

    public List<BranchConstraint> constraints() {
        return constraints;
    }

    public BranchConstraint inheritedBy() {
        if (inheritedBy == null) {
            throw new IllegalStateException("root node has no inherited constraint");
        }
        return inheritedBy;
    }

    public boolean isRoot() {
        return inheritedBy == null;
    }

    public double lowerBound() {
        return lowerBound;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
