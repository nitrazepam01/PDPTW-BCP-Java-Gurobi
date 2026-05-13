package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.master.RouteColumn;

import java.util.Collection;
import java.util.Objects;

/**
 * Master-row representation for request subset-row cuts.
 */
public final class SubsetRowCutRow implements MasterCutRow {
    private final String name;
    private final SubsetRowCut template;

    private SubsetRowCutRow(String name, Collection<Integer> requests, int l) {
        this.name = MasterCutRow.requireNonBlank(name, "name");
        this.template = SubsetRowCut.of(name, requests, l, 0.0);
    }

    public static SubsetRowCutRow of(String name, Collection<Integer> requests, int l) {
        return new SubsetRowCutRow(name, requests, l);
    }

    public static SubsetRowCutRow of(SubsetRowCut cut) {
        Objects.requireNonNull(cut, "cut");
        return new SubsetRowCutRow(cut.name(), cut.requests(), cut.l());
    }

    public static SubsetRowCutRow ofL2Triple(String name, int first, int second, int third) {
        return new SubsetRowCutRow(name, java.util.List.of(
                Integer.valueOf(first),
                Integer.valueOf(second),
                Integer.valueOf(third)), 2);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Sense sense() {
        return Sense.LESS_EQUAL;
    }

    @Override
    public double rhs() {
        return template.rhs();
    }

    @Override
    public double coefficient(Instance instance, RouteColumn column) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(column, "column");
        if (column.isArtificial()) {
            return 0.0;
        }
        return template.coefficientForRoute(instance, column.vertexIds());
    }

    @Override
    public double pricingDualFromRawPi(double rawPi) {
        return MasterCutRow.requireFinite(rawPi, "rawPi");
    }

    public SubsetRowCut toPricingCut(double rawPi) {
        return SubsetRowCut.of(name, template.requests(), template.l(), pricingDualFromRawPi(rawPi));
    }

    public SubsetRowCut template() {
        return template;
    }

    public void validateForInstance(Instance instance) {
        Objects.requireNonNull(instance, "instance");
        for (int requestId : template.requests()) {
            if (requestId < 1 || requestId > instance.nRequests()) {
                throw new IllegalArgumentException("subset-row request id out of range in row "
                        + name + ": " + requestId);
            }
        }
    }
}
