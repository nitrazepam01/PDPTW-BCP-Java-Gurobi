package org.pdptw.cli;

import org.pdptw.core.Instance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BenchmarkRunner {
    private static final double COMPARE_TOLERANCE = 1.0e-7;

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        if (options.containsKey("compare_pricing")) {
            LinkedHashMap<String, String> visibleOptions = new LinkedHashMap<String, String>(options);
            visibleOptions.remove("compare_pricing");
            CliSupport.requireKnownOptions(
                    visibleOptions,
                    "compare-pricing",
                    "instance",
                    "cuts");
            CliSupport.requirePositionalCount(
                    visibleOptions,
                    "compare-pricing",
                    CliSupport.instanceOnlyPositionalLimit(visibleOptions),
                    "[instance]");
            requireNoComparePricingCuts(CliSupport.option(options, "cuts", "none"));
            Instance instance = CliSupport.readInstance(options, "tiny-a-wide.json");
            System.out.println(BenchmarkCsv.rowsToCsv(comparePricing(instance)));
            return;
        }
        CliSupport.requireKnownOptions(
                options,
                "benchmark",
                "instance",
                "pricing",
                "cuts",
                "branching");
        CliSupport.requirePositionalCount(
                options,
                "benchmark",
                CliSupport.instanceOnlyPositionalLimit(options),
                "[instance]");
        Instance instance = CliSupport.readInstance(options, "tiny-a-wide.json");
        String branching = CliSupport.option(options, "branching", "timo");
        if (!BcpRunner.acceptsTimoBranching(branching)) {
            throw new IllegalArgumentException("Only --branching timo is supported in the tiny branch tree runner");
        }
        BcpRunner runner = BcpRunner.fromPricingOption(CliSupport.option(options, "pricing", "route-universe"));
        System.out.println(BenchmarkCsv.rowsToCsv(List.of(runner.run(
                instance,
                CliSupport.option(options, "cuts", "none"),
                branching))));
    }

    public static List<BenchmarkCsv.Row> comparePricing(Instance instance) {
        List<BenchmarkCsv.Row> rows = RunPricingAudit.auditAllModes(instance);
        assertConsistentBestReducedCost(rows);
        return rows;
    }

    private static void assertConsistentBestReducedCost(List<BenchmarkCsv.Row> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("compare-pricing requires at least one pricing row");
        }
        double expected = rows.get(0).rootLb();
        for (BenchmarkCsv.Row row : rows) {
            if (Math.abs(expected - row.rootLb()) > COMPARE_TOLERANCE) {
                throw new IllegalStateException("pricing modes disagree: expected " + expected
                        + " but " + row.mode() + " returned " + row.rootLb());
            }
        }
    }

    private static void requireNoComparePricingCuts(String cuts) {
        if (CliSupport.enabledCsvCount(cuts) > 0) {
            throw new IllegalArgumentException("BenchmarkRunner compare-pricing currently supports no-cut pricing "
                    + "audits only; active cut rows are covered by programmatic pricing tests");
        }
    }
}
