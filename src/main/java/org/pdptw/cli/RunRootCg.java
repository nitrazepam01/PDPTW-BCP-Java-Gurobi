package org.pdptw.cli;

import org.pdptw.core.Instance;
import org.pdptw.pricing.PricingMode;

import java.util.List;
import java.util.Map;

public final class RunRootCg {
    private RunRootCg() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        CliSupport.requireKnownOptions(options, "root-cg", "instance", "pricing", "mode", "cuts", "trace");
        CliSupport.requirePositionalCount(
                options,
                "root-cg",
                CliSupport.instanceAndPricingPositionalLimit(options),
                "[instance] [pricing-mode]");
        PricingMode mode = CliSupport.pricingMode(options, PricingMode.BIDIR_STATIC);
        requireNoCliCuts(CliSupport.option(options, "cuts", "none"));
        Instance instance = CliSupport.readInstance(options, "tiny-a-wide.json");
        long start = System.nanoTime();
        RootCgResult result = new RootColumnGenerationRunner().run(instance, mode);
        BenchmarkCsv.Row record = summaryRow(instance, mode, result, CliSupport.elapsedMs(start));
        System.out.println(BenchmarkCsv.rowsToCsv(List.of(record)));
        if (CliSupport.enabled(options, "trace")) {
            System.out.println(TraceCsv.rootCg(instance.name(), result));
        }
    }

    static BenchmarkCsv.Row summaryRow(
            Instance instance,
            PricingMode mode,
            RootCgResult result,
            long totalTimeMs) {
        return new BenchmarkCsv.Row(
                instance.name(),
                "root-cg-" + RunPricingAudit.modeName(mode),
                result.terminationReason(),
                result.finalObjectiveValue(),
                Double.NaN,
                Double.NaN,
                1,
                result.finalColumnCount(),
                result.finalCutsActive(),
                result.iterationCount(),
                result.totalForwardLabels(),
                result.totalBackwardLabels(),
                result.totalDominatedLabels(),
                result.totalPricingTimeMs(),
                totalTimeMs);
    }

    private static void requireNoCliCuts(String cuts) {
        if (CliSupport.enabledCsvCount(cuts) > 0) {
            throw new IllegalArgumentException("RunRootCg CLI currently supports no-cut root CG only; "
                    + "programmatic RootColumnGenerationRunner cutRows tests cover active cut rows");
        }
    }
}
