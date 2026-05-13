package org.pdptw.master;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.cuts.RobustCut;
import org.pdptw.cuts.SRPricingAdjuster;
import org.pdptw.cuts.SubsetRowCut;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pricing duals in the normalized contract:
 * rc(route) = route_cost - mu - sum(pi_i for served requests i).
 */
public final class DualSolution {
    private final double[] requestDuals;
    private final double fleetDual;
    private final List<RobustCut> robustCuts;
    private final List<SubsetRowCut> subsetRowCuts;

    private DualSolution(
            double[] requestDuals,
            double fleetDual,
            Collection<? extends RobustCut> robustCuts,
            Collection<? extends SubsetRowCut> subsetRowCuts) {
        if (requestDuals.length == 0) {
            throw new IllegalArgumentException("requestDuals must be one-indexed and have length n + 1");
        }
        this.requestDuals = requestDuals.clone();
        this.fleetDual = requireFinite(fleetDual, "fleetDual");
        this.robustCuts = Collections.unmodifiableList(copyRobustCuts(robustCuts));
        this.subsetRowCuts = Collections.unmodifiableList(copySubsetRowCuts(subsetRowCuts));
        for (int i = 1; i < this.requestDuals.length; i++) {
            requireFinite(this.requestDuals[i], "requestDuals[" + i + "]");
        }
    }

    public static DualSolution ofOneIndexed(double[] requestDuals, double fleetDual) {
        Objects.requireNonNull(requestDuals, "requestDuals");
        return new DualSolution(requestDuals, fleetDual, List.of(), List.of());
    }

    public static DualSolution ofOneIndexed(
            double[] requestDuals,
            double fleetDual,
            Collection<? extends RobustCut> robustCuts) {
        Objects.requireNonNull(requestDuals, "requestDuals");
        return new DualSolution(requestDuals, fleetDual, robustCuts, List.of());
    }

    public static DualSolution ofOneIndexed(
            double[] requestDuals,
            double fleetDual,
            Collection<? extends RobustCut> robustCuts,
            Collection<? extends SubsetRowCut> subsetRowCuts) {
        Objects.requireNonNull(requestDuals, "requestDuals");
        return new DualSolution(requestDuals, fleetDual, robustCuts, subsetRowCuts);
    }

    public int nRequests() {
        return requestDuals.length - 1;
    }

    public double requestDual(int requestId) {
        if (requestId < 1 || requestId >= requestDuals.length) {
            throw new IllegalArgumentException("requestId out of range: " + requestId);
        }
        return requestDuals[requestId];
    }

    public double fleetDual() {
        return fleetDual;
    }

    public double[] requestDualsOneIndexed() {
        return requestDuals.clone();
    }

    public List<RobustCut> robustCuts() {
        return robustCuts;
    }

    public List<SubsetRowCut> subsetRowCuts() {
        return subsetRowCuts;
    }

    public boolean hasRobustCuts() {
        return !robustCuts.isEmpty();
    }

    public boolean hasSubsetRowCuts() {
        return !subsetRowCuts.isEmpty();
    }

    public double directReducedCost(Route route, Instance instance) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(instance, "instance");
        return directReducedCost(route.cost(instance), route.servedRequests(instance));
    }

    public double directReducedCost(RouteColumn column) {
        Objects.requireNonNull(column, "column");
        return directReducedCost(column.cost(), column.servedRequests());
    }

    public double directReducedCost(double routeCost, Set<Integer> servedRequests) {
        requireFinite(routeCost, "routeCost");
        Objects.requireNonNull(servedRequests, "servedRequests");
        double reducedCost = routeCost - fleetDual;
        for (int requestId : servedRequests) {
            reducedCost -= requestDual(requestId);
        }
        return reducedCost;
    }

    public double directReducedCostWithRobustCuts(Route route, Instance instance) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(instance, "instance");
        return directReducedCost(route, instance) + robustArcPriceSum(instance, route.vertexIds());
    }

    public double directReducedCostWithRobustCuts(RouteColumn column, Instance instance) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(instance, "instance");
        if (column.isArtificial()) {
            return directReducedCost(column);
        }
        return directReducedCost(column) + robustArcPriceSum(instance, column.vertexIds());
    }

    public double directReducedCostWithCuts(Route route, Instance instance) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(instance, "instance");
        return directReducedCost(route, instance)
                + robustArcPriceSum(instance, route.vertexIds())
                + subsetRowPriceSum(route, instance);
    }

    public double subsetRowPriceSum(Route route, Instance instance) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(instance, "instance");
        double sum = 0.0;
        for (SubsetRowCut cut : subsetRowCuts) {
            sum += SRPricingAdjuster.routePricingAdjustment(cut, instance, route.vertexIds());
        }
        return requireFinite(sum, "subsetRowPriceSum");
    }

    public double robustArcPriceSum(Instance instance, List<Integer> vertexIds) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(vertexIds, "vertexIds");
        double sum = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            sum += robustArcPrice(instance, vertexIds.get(i).intValue(), vertexIds.get(i + 1).intValue());
        }
        return requireFinite(sum, "robustArcPriceSum");
    }

    public double robustArcPrice(Instance instance, int from, int to) {
        Objects.requireNonNull(instance, "instance");
        if (!instance.hasVertex(from) || !instance.hasVertex(to)) {
            throw new IllegalArgumentException("Unknown robust-cut arc: " + from + "->" + to);
        }
        double price = 0.0;
        for (RobustCut cut : robustCuts) {
            price += cut.arcPrice(instance, from, to);
        }
        return requireFinite(price, "robustArcPrice");
    }

    @Override
    public String toString() {
        return "DualSolution{requestDuals=" + Arrays.toString(requestDuals)
                + ", fleetDual=" + fleetDual
                + ", robustCuts=" + robustCuts.size()
                + ", subsetRowCuts=" + subsetRowCuts.size() + '}';
    }

    private static List<RobustCut> copyRobustCuts(Collection<? extends RobustCut> source) {
        Objects.requireNonNull(source, "robustCuts");
        ArrayList<RobustCut> copy = new ArrayList<RobustCut>();
        for (RobustCut cut : source) {
            copy.add(Objects.requireNonNull(cut, "robustCuts contains null"));
        }
        return copy;
    }

    private static List<SubsetRowCut> copySubsetRowCuts(Collection<? extends SubsetRowCut> source) {
        Objects.requireNonNull(source, "subsetRowCuts");
        ArrayList<SubsetRowCut> copy = new ArrayList<SubsetRowCut>();
        for (SubsetRowCut cut : source) {
            copy.add(Objects.requireNonNull(cut, "subsetRowCuts contains null"));
        }
        return copy;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
