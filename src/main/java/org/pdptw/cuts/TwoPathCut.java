package org.pdptw.cuts;

import org.pdptw.core.Instance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal 2-path robust-cut carrier. Separation is intentionally out of scope.
 */
public final class TwoPathCut implements RobustCut {
    private final String name;
    private final double dualValue;
    private final Map<Arc, Double> arcCoefficients;

    private TwoPathCut(String name, double dualValue, Map<Arc, Double> arcCoefficients) {
        this.name = requireNonBlank(name, "name");
        this.dualValue = requireFinite(dualValue, "dualValue");
        this.arcCoefficients = Collections.unmodifiableMap(copyCoefficients(arcCoefficients));
    }

    public static TwoPathCut ofArcCoefficients(
            String name,
            double dualValue,
            Map<Arc, Double> arcCoefficients) {
        return new TwoPathCut(name, dualValue, arcCoefficients);
    }

    public static TwoPathCut placeholder(String name) {
        return new TwoPathCut(name, 0.0, Collections.<Arc, Double>emptyMap());
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

    public Map<Arc, Double> arcCoefficients() {
        return arcCoefficients;
    }

    private static Map<Arc, Double> copyCoefficients(Map<Arc, Double> source) {
        Objects.requireNonNull(source, "arcCoefficients");
        LinkedHashMap<Arc, Double> copy = new LinkedHashMap<Arc, Double>();
        for (Map.Entry<Arc, Double> entry : source.entrySet()) {
            Arc arc = Objects.requireNonNull(entry.getKey(), "arc coefficient key");
            double coefficient = requireFinite(entry.getValue(), "arcCoefficient");
            if (Math.abs(coefficient) > 0.0) {
                copy.put(arc, coefficient);
            }
        }
        return copy;
    }

    private static double requireFinite(Double value, String name) {
        Objects.requireNonNull(value, name);
        return requireFinite(value.doubleValue(), name);
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
