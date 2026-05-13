package org.pdptw.cli;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) throws Exception {
        try {
            if (args.length == 0) {
                System.out.println("PDPTW BCP Java/Gurobi reproduction."
                        + System.lineSeparator()
                        + "Commands: pricing-audit, root-cg, bcp, compare-pricing, benchmark, instance-smoke,"
                        + " root-lp-pricing-smoke, root-finite-cg-smoke, benchmark-subinstance."
                        + System.lineSeparator()
                        + "benchmark is a Tiny smoke/report runner only, not a paper benchmark reproduction."
                        + System.lineSeparator()
                        + "instance-smoke parses RC/LL benchmark files and reports metadata only."
                        + System.lineSeparator()
                        + "root-lp-pricing-smoke solves a restricted root LP and scans a finite candidate pool only."
                        + System.lineSeparator()
                        + "root-finite-cg-smoke repeats LP solves over that finite candidate pool only."
                        + System.lineSeparator()
                        + "Default bcp uses route-universe exact pricing and reports label counters as NA;"
                        + " use bcp --pricing bidir-dynamic --trace for explicit labeling counters."
                        + System.lineSeparator()
                        + "CLI --cuts sr|robust|robust,sr open only on tiny explicit-labeling "
                        + "non-route-universe pricing.");
                return 0;
            }
            String command = args[0].trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            String[] tail = java.util.Arrays.copyOfRange(args, 1, args.length);
            switch (command) {
                case "pricing-audit" -> RunPricingAudit.main(tail);
                case "root-cg" -> RunRootCg.main(tail);
                case "bcp" -> RunBcp.main(tail);
                case "compare-pricing" -> {
                    String[] forwarded = new String[tail.length + 1];
                    forwarded[0] = "--compare-pricing=true";
                    System.arraycopy(tail, 0, forwarded, 1, tail.length);
                    BenchmarkRunner.main(forwarded);
                }
                case "benchmark" -> BenchmarkRunner.main(tail);
                case "instance-smoke" -> RunInstanceSmoke.main(tail);
                case "root-lp-pricing-smoke" -> RunRootLpPricingSmoke.main(tail);
                case "root-finite-cg-smoke" -> RunRootFiniteCgSmoke.main(tail);
                case "benchmark-subinstance" -> RunBenchmarkSubinstance.main(tail);
                default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
            }
            return 0;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("ERROR: " + exception.getMessage());
            return 2;
        }
    }
}
