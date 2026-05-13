package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.core.Request;
import org.pdptw.core.Vertex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tiny request-set rounded-capacity cut carrier.
 *
 * <p>The request-set factory prices exits from selected pickup vertices. This
 * keeps the tiny PDPTW capacity contract aligned with pickup-before-delivery
 * load changes without opening automatic rounded-capacity separation.</p>
 */
public final class RoundedCapacityCut implements RobustCut {
    private final String name;
    private final double dualValue;
    private final Map<Arc, Double> arcCoefficients;

    private RoundedCapacityCut(String name, double dualValue, Map<Arc, Double> arcCoefficients) {
        this.name = requireNonBlank(name, "name");
        this.dualValue = requireFinite(dualValue, "dualValue");
        this.arcCoefficients = Collections.unmodifiableMap(copyCoefficients(arcCoefficients));
    }

    public static RoundedCapacityCut ofArcCoefficients(
            String name,
            double dualValue,
            Map<Arc, Double> arcCoefficients) {
        return new RoundedCapacityCut(name, dualValue, arcCoefficients);
    }

    public static RoundedCapacityCut forRequestSet(
            Instance instance,
            String name,
            double dualValue,
            Collection<Integer> requestIds) {
        return new RoundedCapacityCut(name, dualValue, pickupExitCoefficients(instance, requestIds));
    }

    public static RobustCutRow requestSetRow(
            Instance instance,
            Collection<Integer> requestIds) {
        List<Integer> sortedRequests = sortedUniqueRequests(instance, requestIds);
        return requestSetRow(
                instance,
                "robust-rcap-R" + joinRequestIds(sortedRequests),
                sortedRequests);
    }

    public static RobustCutRow requestSetRow(
            Instance instance,
            String name,
            Collection<Integer> requestIds) {
        List<Integer> sortedRequests = sortedUniqueRequests(instance, requestIds);
        return RobustCutRow.ofArcCoefficients(
                name,
                MasterCutRow.Sense.GREATER_EQUAL,
                requiredVehicleCount(instance, sortedRequests),
                pickupExitCoefficients(instance, sortedRequests));
    }

    public static int requiredVehicleCount(
            Instance instance,
            Collection<Integer> requestIds) {
        Objects.requireNonNull(instance, "instance");
        List<Integer> sortedRequests = sortedUniqueRequests(instance, requestIds);
        if (instance.vehicleCapacity() <= 0) {
            throw new IllegalArgumentException("vehicle capacity must be positive for rounded-capacity cuts");
        }
        int totalDemand = 0;
        for (Integer requestId : sortedRequests) {
            Request request = instance.request(requestId.intValue());
            if (request.demand() <= 0) {
                throw new IllegalArgumentException("request demand must be positive for rounded-capacity cuts: "
                        + request.id());
            }
            totalDemand += request.demand();
        }
        return (int) Math.ceil((double) totalDemand / (double) instance.vehicleCapacity());
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

    private static Map<Arc, Double> pickupExitCoefficients(
            Instance instance,
            Collection<Integer> requestIds) {
        Objects.requireNonNull(instance, "instance");
        List<Integer> sortedRequests = sortedUniqueRequests(instance, requestIds);
        ArrayList<Integer> pickupVertices = new ArrayList<Integer>();
        for (Integer requestId : sortedRequests) {
            pickupVertices.add(Integer.valueOf(instance.request(requestId.intValue()).pickupVertexId()));
        }

        LinkedHashMap<Arc, Double> coefficients = new LinkedHashMap<Arc, Double>();
        for (Integer from : pickupVertices) {
            for (Vertex to : instance.vertices()) {
                if (!pickupVertices.contains(Integer.valueOf(to.id()))) {
                    coefficients.put(new Arc(from.intValue(), to.id()), Double.valueOf(1.0));
                }
            }
        }
        return coefficients;
    }

    private static List<Integer> sortedUniqueRequests(
            Instance instance,
            Collection<Integer> requestIds) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(requestIds, "requestIds");
        ArrayList<Integer> sorted = new ArrayList<Integer>();
        for (Integer requestId : requestIds) {
            Objects.requireNonNull(requestId, "requestIds contains null");
            instance.request(requestId.intValue());
            if (!sorted.contains(requestId)) {
                sorted.add(requestId);
            }
        }
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("rounded-capacity request set must not be empty");
        }
        Collections.sort(sorted);
        return sorted;
    }

    private static String joinRequestIds(List<Integer> requestIds) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < requestIds.size(); index++) {
            if (index > 0) {
                builder.append('-');
            }
            builder.append(requestIds.get(index).intValue());
        }
        return builder.toString();
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
