package org.pdptw.cli;

import java.util.Locale;
import java.util.Objects;

public final class BenchmarkCsv {
    public static final String HEADER = "instance,mode,status,rootLb,integerUb,gap,nodes,columns,cuts,"
            + "pricingCalls,forwardLabels,backwardLabels,dominatedLabels,pricingTimeMs,totalTimeMs";

    private BenchmarkCsv() {
    }

    public static String header() {
        return HEADER;
    }

    public record Row(
            String instance,
            String mode,
            String status,
            double rootLb,
            double integerUb,
            double gap,
            int nodes,
            int columns,
            int cuts,
            int pricingCalls,
            int forwardLabels,
            int backwardLabels,
            int dominatedLabels,
            long pricingTimeMs,
            long totalTimeMs) {

        public Row {
            requireNonBlank(instance, "instance");
            requireNonBlank(mode, "mode");
            requireNonBlank(status, "status");
        }

        public String toCsv() {
            return csv(instance)
                    + "," + csv(mode)
                    + "," + csv(status)
                    + "," + number(rootLb)
                    + "," + number(integerUb)
                    + "," + number(gap)
                    + "," + integer(nodes)
                    + "," + integer(columns)
                    + "," + integer(cuts)
                    + "," + integer(pricingCalls)
                    + "," + integer(forwardLabels)
                    + "," + integer(backwardLabels)
                    + "," + integer(dominatedLabels)
                    + "," + integer(pricingTimeMs)
                    + "," + integer(totalTimeMs);
        }
    }

    public static String rowsToCsv(Iterable<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER);
        for (Row row : rows) {
            sb.append(System.lineSeparator()).append(row.toCsv());
        }
        return sb.toString();
    }

    static String number(double value) {
        if (!Double.isFinite(value)) {
            return "NA";
        }
        return String.format(Locale.ROOT, "%.10f", value);
    }

    private static String integer(long value) {
        return value < 0 ? "NA" : Long.toString(value);
    }

    static String csv(String value) {
        Objects.requireNonNull(value, "value");
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
