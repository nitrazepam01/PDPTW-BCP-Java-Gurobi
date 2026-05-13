package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.master.RouteColumn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Master-row representation for robust cuts whose pricing effect is an arc-price shift.
 */
public final class RobustCutRow implements MasterCutRow {
    private final String name;
    private final MasterCutRow.Sense sense;
    private final double rhs;
    private final Map<RobustCut.Arc, Double> arcCoefficients;

    private RobustCutRow(
            String name,
            MasterCutRow.Sense sense,
            double rhs,
            Map<RobustCut.Arc, Double> arcCoefficients) {
        this.name = MasterCutRow.requireNonBlank(name, "name");
        this.sense = Objects.requireNonNull(sense, "sense");
        this.rhs = MasterCutRow.requireFinite(rhs, "rhs");
        this.arcCoefficients = Collections.unmodifiableMap(copyCoefficients(arcCoefficients));
    }

    public static RobustCutRow ofArcCoefficients(
            String name,
            MasterCutRow.Sense sense,
            double rhs,
            Map<RobustCut.Arc, Double> arcCoefficients) {
        return new RobustCutRow(name, sense, rhs, arcCoefficients);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public MasterCutRow.Sense sense() {
        return sense;
    }

    @Override
    public double rhs() {
        return rhs;
    }

    public Map<RobustCut.Arc, Double> arcCoefficients() {
        return arcCoefficients;
    }

    public double arcCoefficient(Instance instance, int from, int to) {
        Objects.requireNonNull(instance, "instance");
        if (!instance.hasVertex(from) || !instance.hasVertex(to)) {
            throw new IllegalArgumentException("Unknown robust-cut arc: " + from + "->" + to);
        }
        return arcCoefficients.getOrDefault(new RobustCut.Arc(from, to), 0.0);
    }

    public double routeCoefficient(Instance instance, RouteColumn column) {
        return coefficient(instance, column);
    }

    @Override
    public double coefficient(Instance instance, RouteColumn column) {
        Objects.requireNonNull(column, "column");
        if (column.isArtificial()) {
            return 0.0;
        }
        return routeCoefficient(instance, column.vertexIds());
    }

    public double routeCoefficient(Instance instance, Route route) {
        Objects.requireNonNull(route, "route");
        return routeCoefficient(instance, route.vertexIds());
    }

    public double routeCoefficient(Instance instance, List<Integer> vertexIds) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(vertexIds, "vertexIds");
        double coefficient = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            int from = vertexIds.get(i).intValue();
            int to = vertexIds.get(i + 1).intValue();
            coefficient += arcCoefficient(instance, from, to);
        }
        return MasterCutRow.requireFinite(coefficient, "routeCoefficient");
    }

    public RobustCut toPricingCut(double lpDualValue) {
        return new DualizedRobustCut(name, pricingDualFromRawPi(lpDualValue), arcCoefficients);
    }

    public double pricingDualValue(double lpDualValue) {
        return pricingDualFromRawPi(lpDualValue);
    }

    public void validateForInstance(Instance instance) {
        Objects.requireNonNull(instance, "instance");
        for (RobustCut.Arc arc : arcCoefficients.keySet()) {
            if (!instance.hasVertex(arc.from()) || !instance.hasVertex(arc.to())) {
                throw new IllegalArgumentException("Unknown robust-cut arc in row " + name
                        + ": " + arc.from() + "->" + arc.to());
            }
        }
    }

    private static Map<RobustCut.Arc, Double> copyCoefficients(Map<RobustCut.Arc, Double> source) {
        Objects.requireNonNull(source, "arcCoefficients");
        LinkedHashMap<RobustCut.Arc, Double> copy = new LinkedHashMap<RobustCut.Arc, Double>();
        for (Map.Entry<RobustCut.Arc, Double> entry : source.entrySet()) {
            RobustCut.Arc arc = Objects.requireNonNull(entry.getKey(), "arc coefficient key");
            double coefficient = requireFinite(entry.getValue(), "arcCoefficient");
            if (Math.abs(coefficient) > 0.0) {
                copy.put(arc, coefficient);
            }
        }
        return copy;
    }

    private static double requireFinite(Double value, String name) {
        Objects.requireNonNull(value, name);
        return MasterCutRow.requireFinite(value.doubleValue(), name);
    }

    private static final class DualizedRobustCut implements RobustCut {
        private final String name;
        private final double dualValue;
        private final Map<Arc, Double> arcCoefficients;

        private DualizedRobustCut(String name, double dualValue, Map<Arc, Double> arcCoefficients) {
            this.name = MasterCutRow.requireNonBlank(name, "name");
            this.dualValue = MasterCutRow.requireFinite(dualValue, "dualValue");
            this.arcCoefficients = Collections.unmodifiableMap(copyCoefficients(arcCoefficients));
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public double dualValue() {
            return dualValue;
        }

        @Override
        public double arcCoefficient(Instance instance, int from, int to) {
            Objects.requireNonNull(instance, "instance");
            if (!instance.hasVertex(from) || !instance.hasVertex(to)) {
                throw new IllegalArgumentException("Unknown robust-cut arc: " + from + "->" + to);
            }
            return arcCoefficients.getOrDefault(new Arc(from, to), 0.0);
        }
    }
}
