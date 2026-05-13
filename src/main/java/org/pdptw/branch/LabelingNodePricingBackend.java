package org.pdptw.branch;

import org.pdptw.master.GurobiRmp;
import org.pdptw.master.DualSolution;
import org.pdptw.master.RouteColumn;
import org.pdptw.pricing.PricingContext;
import org.pdptw.pricing.PricingResult;
import org.pdptw.pricing.PricingSolver;
import org.pdptw.pricing.ReducedCostMatrices;
import org.pdptw.pricing.SetOutflowPricingRule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class LabelingNodePricingBackend implements BranchNodePricingBackend {
    static final String BACKEND_NAME = "labeling_exact_branch_rows";

    private final PricingSolver solver;

    LabelingNodePricingBackend(PricingSolver solver) {
        this.solver = Objects.requireNonNull(solver, "solver");
    }

    @Override
    public String name() {
        return BACKEND_NAME;
    }

    @Override
    public BranchNodePricingResult price(BranchNodePricingRequest request) {
        Objects.requireNonNull(request, "request");
        PricingContext pricingContext = pricingContextWithSupportedBranchRows(request);
        PricingResult pricing = solver.price(pricingContext);
        requireExactConsistentPricing(request, pricingContext, pricing);
        Set<String> currentSignatures = new LinkedHashSet<String>();
        for (RouteColumn column : request.currentColumns()) {
            currentSignatures.add(column.signature());
        }

        ArrayList<RouteColumn> missingColumns = new ArrayList<RouteColumn>();
        double bestMissingReducedCost = Double.POSITIVE_INFINITY;
        for (RouteColumn column : pricing.columns()) {
            if (currentSignatures.contains(column.signature())) {
                continue;
            }
            missingColumns.add(column);
            double reducedCost = pricingContext.directReducedCost(column.vertexIds());
            if (reducedCost < bestMissingReducedCost) {
                bestMissingReducedCost = reducedCost;
            }
        }
        if (missingColumns.isEmpty()) {
            bestMissingReducedCost = pricing.bestReducedCost() < -request.tolerance()
                    ? 0.0
                    : pricing.bestReducedCost();
        }
        return BranchNodePricingResult.of(
                bestMissingReducedCost,
                pricing.columns().size(),
                missingColumns,
                pricing.stats());
    }

    private static void requireExactConsistentPricing(
            BranchNodePricingRequest request,
            PricingContext pricingContext,
            PricingResult pricing) {
        Objects.requireNonNull(pricing, "pricing");
        if (!pricing.exact()) {
            throw new IllegalStateException("labeling node pricing backend requires exact pricing result"
                    + " status=" + pricing.status());
        }
        if (pricing.bestReducedCost() < -request.tolerance() && pricing.columns().isEmpty()) {
            throw new IllegalStateException("labeling node pricing backend reported negative reduced cost"
                    + " but returned no columns"
                    + " bestReducedCost=" + pricing.bestReducedCost());
        }
        if (pricing.columns().isEmpty()) {
            return;
        }
        double bestReturnedDirectReducedCost = Double.POSITIVE_INFINITY;
        for (RouteColumn column : pricing.columns()) {
            double reducedCost = pricingContext.directReducedCost(column.vertexIds());
            if (reducedCost < bestReturnedDirectReducedCost) {
                bestReturnedDirectReducedCost = reducedCost;
            }
        }
        if (Math.abs(pricing.bestReducedCost() - bestReturnedDirectReducedCost) > request.tolerance()) {
            throw new IllegalStateException("labeling node pricing backend result best reduced cost"
                    + " does not match returned columns"
                    + " bestReducedCost=" + pricing.bestReducedCost()
                    + " bestReturnedDirectReducedCost=" + bestReturnedDirectReducedCost);
        }
    }

    private static PricingContext pricingContextWithSupportedBranchRows(BranchNodePricingRequest request) {
        double vehicleCountPricingDual = 0.0;
        ArrayList<SetOutflowPricingRule> setOutflowPricingRules = new ArrayList<SetOutflowPricingRule>();
        for (GurobiRmp.CutDual cutDual : request.cutDuals()) {
            if (cutDual.row() instanceof BranchMasterRow) {
                BranchMasterRow branchRow = (BranchMasterRow) cutDual.row();
                if (branchRow.constraint() instanceof VehicleCountConstraint) {
                    vehicleCountPricingDual += cutDual.pricingDual();
                } else if (branchRow.constraint() instanceof SetOutflowConstraint) {
                    SetOutflowConstraint setOutflow = (SetOutflowConstraint) branchRow.constraint();
                    setOutflowPricingRules.add(SetOutflowPricingRule.of(
                            branchRow.name(),
                            setOutflow.requestSet(),
                            cutDual.pricingDual()));
                } else {
                    throw new IllegalStateException("labeling node pricing backend does not yet support"
                            + " inherited branch row type: " + branchRow.constraint().type());
                }
            }
        }
        PricingContext base = request.pricingContext();
        PricingContext branchContext = base;
        if (Math.abs(vehicleCountPricingDual) > 0.0) {
            DualSolution baseDuals = base.matrices().duals();
            DualSolution adjustedDuals = DualSolution.ofOneIndexed(
                    baseDuals.requestDualsOneIndexed(),
                    baseDuals.fleetDual() - vehicleCountPricingDual,
                    baseDuals.robustCuts(),
                    baseDuals.subsetRowCuts());
            ReducedCostMatrices adjustedMatrices = ReducedCostMatrices.fromDualSolution(
                    request.instance(),
                    adjustedDuals);
            branchContext = PricingContext.withCuts(
                    adjustedMatrices,
                    base.robustCuts(),
                    base.subsetRowCuts());
        }
        if (setOutflowPricingRules.isEmpty()) {
            return branchContext;
        }
        return PricingContext.withSetOutflowPricing(
                branchContext,
                List.copyOf(setOutflowPricingRules));
    }
}
