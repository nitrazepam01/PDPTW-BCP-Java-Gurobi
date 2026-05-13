package org.pdptw.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ForwardPricingSolver implements PricingSolver {
    private final double tolerance;
    private final ForwardLabeler labeler;

    public ForwardPricingSolver() {
        this(1.0e-7);
    }

    public ForwardPricingSolver(double tolerance) {
        this(tolerance, new ForwardLabeler());
    }

    ForwardPricingSolver(double tolerance, ForwardLabeler labeler) {
        this.tolerance = PricingAdapterSupport.requireTolerance(tolerance);
        this.labeler = Objects.requireNonNull(labeler, "labeler");
    }

    @Override
    public PricingResult price(ReducedCostMatrices matrices) {
        return price(PricingContext.noCuts(matrices));
    }

    @Override
    public PricingResult price(PricingContext context) {
        Objects.requireNonNull(context, "context");
        ForwardLabeler.Result result = labeler.solve(context);
        List<List<Integer>> routes = new ArrayList<List<Integer>>();
        for (ForwardLabel label : result.completeLabels()) {
            routes.add(label.vertexIds());
        }
        return PricingAdapterSupport.exactFromRoutes(
                "forward",
                context,
                routes,
                result.bestReducedCost(),
                tolerance,
                PricingResult.Stats.of(
                        result.partialLabels().size() + result.completeLabels().size(),
                        0,
                        result.completeLabels().size(),
                        0,
                        result.completeLabels().size(),
                        0,
                        0));
    }
}
