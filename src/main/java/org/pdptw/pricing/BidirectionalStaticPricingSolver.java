package org.pdptw.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BidirectionalStaticPricingSolver implements PricingSolver {
    private final double tolerance;
    private final BidirectionalPricingSolver solver;

    public BidirectionalStaticPricingSolver() {
        this(1.0e-7);
    }

    public BidirectionalStaticPricingSolver(double tolerance) {
        this(tolerance, new BidirectionalPricingSolver());
    }

    BidirectionalStaticPricingSolver(double tolerance, BidirectionalPricingSolver solver) {
        this.tolerance = PricingAdapterSupport.requireTolerance(tolerance);
        this.solver = Objects.requireNonNull(solver, "solver");
    }

    @Override
    public PricingResult price(ReducedCostMatrices matrices) {
        return price(PricingContext.noCuts(matrices));
    }

    @Override
    public PricingResult price(PricingContext context) {
        Objects.requireNonNull(context, "context");
        BidirectionalPricingSolver.Result result = solver.solve(context);
        List<List<Integer>> routes = new ArrayList<List<Integer>>();
        for (BidirectionalMerger.MergeResult merge : result.merges()) {
            routes.add(merge.route());
        }
        return PricingAdapterSupport.exactFromRoutes(
                "bidir_static",
                context,
                routes,
                result.bestReducedCost(),
                tolerance,
                PricingResult.Stats.of(
                        result.forwardResult().partialLabels().size()
                                + result.forwardResult().completeLabels().size(),
                        result.backwardResult().partialLabels().size()
                                + result.backwardResult().completeLabels().size(),
                        result.forwardResult().completeLabels().size(),
                        result.backwardResult().completeLabels().size(),
                        result.forwardResult().completeLabels().size()
                                + result.backwardResult().completeLabels().size(),
                        result.merges().size(),
                        0));
    }
}
