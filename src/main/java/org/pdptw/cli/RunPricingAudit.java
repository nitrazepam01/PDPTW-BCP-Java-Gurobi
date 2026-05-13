package org.pdptw.cli;

import org.pdptw.core.Instance;
import org.pdptw.pricing.BackwardLabeler;
import org.pdptw.pricing.BidirectionalDynamicPricingSolver;
import org.pdptw.pricing.BidirectionalPricingSolver;
import org.pdptw.pricing.ForwardLabeler;
import org.pdptw.pricing.PricingMode;
import org.pdptw.pricing.ReducedCostMatrices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RunPricingAudit {
    private RunPricingAudit() {
    }

    public static void main(String[] args) throws Exception {
        List<BenchmarkCsv.Row> rows = run(args);
        System.out.println(BenchmarkCsv.rowsToCsv(rows));
    }

    public static List<BenchmarkCsv.Row> run(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        CliSupport.requireKnownOptions(options, "pricing-audit", "instance", "pricing", "mode", "cuts");
        CliSupport.requirePositionalCount(
                options,
                "pricing-audit",
                CliSupport.instanceAndPricingPositionalLimit(options),
                "[instance] [pricing-mode]");
        requireNoCliCuts(CliSupport.option(options, "cuts", "none"));
        Instance instance = CliSupport.readInstance(options, "tiny-a-wide.json");
        if (!CliSupport.hasPricingSelection(options)) {
            return auditAllModes(instance);
        }
        return List.of(audit(instance, CliSupport.pricingMode(options, PricingMode.BIDIR_DYNAMIC)));
    }

    public static List<BenchmarkCsv.Row> auditAllModes(Instance instance) {
        ArrayList<BenchmarkCsv.Row> rows = new ArrayList<BenchmarkCsv.Row>();
        for (PricingMode mode : PricingMode.values()) {
            rows.add(audit(instance, mode));
        }
        return List.copyOf(rows);
    }

    public static BenchmarkCsv.Row audit(Instance instance, PricingMode mode) {
        long totalStart = System.nanoTime();
        long pricingStart = System.nanoTime();
        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        PricingStats stats = price(matrices, mode);
        long pricingMs = CliSupport.elapsedMs(pricingStart);
        long totalMs = CliSupport.elapsedMs(totalStart);
        return new BenchmarkCsv.Row(
                instance.name(),
                modeName(mode),
                stats.status,
                stats.bestReducedCost,
                Double.NaN,
                Double.NaN,
                0,
                stats.columns,
                0,
                1,
                stats.forwardLabels,
                stats.backwardLabels,
                0,
                pricingMs,
                totalMs);
    }

    private static PricingStats price(ReducedCostMatrices matrices, PricingMode mode) {
        return switch (mode) {
            case FORWARD -> {
                ForwardLabeler.Result result = new ForwardLabeler().solve(matrices);
                yield new PricingStats(
                        "exact",
                        result.bestReducedCost(),
                        result.completeLabels().size(),
                        result.completeLabels().size() + result.partialLabels().size(),
                        0);
            }
            case BACKWARD -> {
                BackwardLabeler.Result result = new BackwardLabeler().solve(matrices);
                yield new PricingStats(
                        "exact",
                        result.bestReducedCost(),
                        result.completeLabels().size(),
                        0,
                        result.completeLabels().size() + result.partialLabels().size());
            }
            case BIDIR_STATIC -> {
                BidirectionalPricingSolver.Result result = new BidirectionalPricingSolver().solve(matrices);
                yield new PricingStats(
                        "exact",
                        result.bestReducedCost(),
                        result.merges().size(),
                        result.forwardResult().completeLabels().size() + result.forwardResult().partialLabels().size(),
                        result.backwardResult().completeLabels().size() + result.backwardResult().partialLabels().size());
            }
            case BIDIR_DYNAMIC -> {
                BidirectionalDynamicPricingSolver.Result result =
                        new BidirectionalDynamicPricingSolver().solve(matrices);
                yield new PricingStats(
                        "exact",
                        result.bestReducedCost(),
                        result.merges().size(),
                        result.forwardLabelCount(),
                        result.backwardLabelCount());
            }
        };
    }

    static String modeName(PricingMode mode) {
        return mode.name().toLowerCase().replace('_', '-');
    }

    private static void requireNoCliCuts(String cuts) {
        if (CliSupport.enabledCsvCount(cuts) > 0) {
            throw new IllegalArgumentException("RunPricingAudit currently supports no-cut pricing audits only; "
                    + "active cut rows are covered by programmatic pricing tests");
        }
    }

    private record PricingStats(
            String status,
            double bestReducedCost,
            int columns,
            int forwardLabels,
            int backwardLabels) {
    }
}
