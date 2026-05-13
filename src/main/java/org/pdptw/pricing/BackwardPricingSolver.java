package org.pdptw.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BackwardPricingSolver implements PricingSolver {
    private final double tolerance;
    private final BackwardLabeler labeler;

    public BackwardPricingSolver() {
        this(1.0e-7);
    }

    public BackwardPricingSolver(double tolerance) {
        this(tolerance, new BackwardLabeler());
    }

    BackwardPricingSolver(double tolerance, BackwardLabeler labeler) {
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
        BackwardLabeler.Result result = labeler.solve(context);
        List<List<Integer>> routes = new ArrayList<List<Integer>>();
        for (BackwardLabel label : result.completeLabels()) {
            routes.add(label.vertexIds());
        }
        return PricingAdapterSupport.exactFromRoutes(
                "backward",
                context,
                routes,
                result.bestReducedCost(),
                tolerance,
                PricingResult.Stats.of(
                        0,
                        result.partialLabels().size() + result.completeLabels().size(),
                        0,
                        result.completeLabels().size(),
                        result.completeLabels().size(),
                        0,
                        0));
    }
}
