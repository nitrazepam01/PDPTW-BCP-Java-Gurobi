package org.pdptw.cuts;

import org.pdptw.core.Instance;
import org.pdptw.core.Request;
import org.pdptw.core.Vertex;
import org.pdptw.master.GurobiRmp;
import org.pdptw.validation.BruteForcePricingOracle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tiny-only robust cut candidate generation.
 *
 * <p>The current generator implements a conservative request-set two-path
 * subset: it only emits a row when exhaustive tiny enumeration proves no
 * feasible route can visit all pickup/delivery vertices induced by the request
 * set.</p>
 */
public final class RobustCutCandidateGenerator {
    private static final int MAX_EXACT_REQUESTS = 6;
    private static final double TWO_PATH_RHS = 2.0;

    private RobustCutCandidateGenerator() {
    }

    public static List<RobustCutRow> violatedTwoPathRequestSetRows(
            Instance instance,
            Collection<GurobiRmp.ColumnValue> solution,
            Collection<? extends MasterCutRow> activeCutRows,
            double tolerance) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        if (instance.nRequests() > MAX_EXACT_REQUESTS) {
            throw new IllegalArgumentException("two-path robust candidate generation is tiny-only; nRequests="
                    + instance.nRequests());
        }

        List<BruteForcePricingOracle.RouteEvaluation> feasibleRoutes =
                new BruteForcePricingOracle().enumerate(instance);
        ArrayList<RobustCutRow> candidates = new ArrayList<RobustCutRow>();
        List<Integer> requestIds = instance.requestIds();
        int maskLimit = 1 << requestIds.size();
        for (int mask = 1; mask < maskLimit; mask++) {
            if (Integer.bitCount(mask) < 2) {
                continue;
            }
            List<Integer> selectedRequests = selectedRequests(requestIds, mask);
            Set<Integer> vertexSet = requestVertexSet(instance, selectedRequests);
            if (!hasSingleFeasibleRouteCovering(feasibleRoutes, vertexSet)) {
                candidates.add(twoPathRequestSetRow(instance, selectedRequests));
            }
        }
        return RobustCutSeparator.violatedRows(instance, solution, activeCutRows, candidates, tolerance);
    }

    public static List<RobustCutRow> violatedRoundedCapacityRequestSetRows(
            Instance instance,
            Collection<GurobiRmp.ColumnValue> solution,
            Collection<? extends MasterCutRow> activeCutRows,
            double tolerance) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(activeCutRows, "activeCutRows");
        if (instance.nRequests() > MAX_EXACT_REQUESTS) {
            throw new IllegalArgumentException("rounded-capacity robust candidate generation is tiny-only; nRequests="
                    + instance.nRequests());
        }

        ArrayList<RobustCutRow> candidates = new ArrayList<RobustCutRow>();
        List<Integer> requestIds = instance.requestIds();
        int maskLimit = 1 << requestIds.size();
        for (int mask = 1; mask < maskLimit; mask++) {
            if (Integer.bitCount(mask) < 2) {
                continue;
            }
            List<Integer> selectedRequests = selectedRequests(requestIds, mask);
            if (RoundedCapacityCut.requiredVehicleCount(instance, selectedRequests) <= 1) {
                continue;
            }
            candidates.add(RoundedCapacityCut.requestSetRow(instance, selectedRequests));
        }
        return RobustCutSeparator.violatedRows(instance, solution, activeCutRows, candidates, tolerance);
    }

    public static RobustCutRow twoPathRequestSetRow(
            Instance instance,
            Collection<Integer> requestIds) {
        Objects.requireNonNull(instance, "instance");
        List<Integer> sortedRequests = sortedUniqueRequests(instance, requestIds);
        if (sortedRequests.size() < 2) {
            throw new IllegalArgumentException("two-path request set must contain at least two requests");
        }
        Set<Integer> vertexSet = requestVertexSet(instance, sortedRequests);
        Map<RobustCut.Arc, Double> coefficients = new LinkedHashMap<RobustCut.Arc, Double>();
        for (Integer from : vertexSet) {
            for (Vertex to : instance.vertices()) {
                if (!vertexSet.contains(Integer.valueOf(to.id()))) {
                    coefficients.put(new RobustCut.Arc(from.intValue(), to.id()), Double.valueOf(1.0));
                }
            }
        }
        return RobustCutRow.ofArcCoefficients(
                "robust-2path-R" + joinRequestIds(sortedRequests),
                MasterCutRow.Sense.GREATER_EQUAL,
                TWO_PATH_RHS,
                coefficients);
    }

    private static List<Integer> selectedRequests(List<Integer> requestIds, int mask) {
        ArrayList<Integer> selected = new ArrayList<Integer>();
        for (int index = 0; index < requestIds.size(); index++) {
            if ((mask & (1 << index)) != 0) {
                selected.add(requestIds.get(index));
            }
        }
        return selected;
    }

    private static boolean hasSingleFeasibleRouteCovering(
            List<BruteForcePricingOracle.RouteEvaluation> feasibleRoutes,
            Set<Integer> vertexSet) {
        for (BruteForcePricingOracle.RouteEvaluation route : feasibleRoutes) {
            HashSet<Integer> routeVertices = new HashSet<Integer>(route.vertexIds());
            if (routeVertices.containsAll(vertexSet)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Integer> requestVertexSet(Instance instance, Collection<Integer> requestIds) {
        LinkedHashSet<Integer> vertices = new LinkedHashSet<Integer>();
        for (Integer requestId : requestIds) {
            Request request = instance.request(requestId.intValue());
            vertices.add(Integer.valueOf(request.pickupVertexId()));
            vertices.add(Integer.valueOf(request.deliveryVertexId()));
        }
        return vertices;
    }

    private static List<Integer> sortedUniqueRequests(Instance instance, Collection<Integer> requestIds) {
        Objects.requireNonNull(requestIds, "requestIds");
        ArrayList<Integer> sorted = new ArrayList<Integer>();
        for (Integer requestId : requestIds) {
            Objects.requireNonNull(requestId, "requestIds contains null");
            instance.request(requestId.intValue());
            if (!sorted.contains(requestId)) {
                sorted.add(requestId);
            }
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
}
