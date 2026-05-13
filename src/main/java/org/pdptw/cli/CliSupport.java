package org.pdptw.cli;

import org.pdptw.core.Instance;
import org.pdptw.io.BenchmarkInstanceReader;
import org.pdptw.io.TinyJsonReader;
import org.pdptw.pricing.PricingMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CliSupport {
    private CliSupport() {
    }

    static Map<String, String> parseOptions(String[] args) {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        int positional = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = normalizeKey(arg.substring(2));
                String value = "true";
                int eq = key.indexOf('=');
                if (eq >= 0) {
                    value = key.substring(eq + 1);
                    key = key.substring(0, eq);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                }
                options.put(key, value);
            } else {
                options.put("arg" + positional, arg);
                positional++;
            }
        }
        return options;
    }

    static void requireKnownOptions(
            Map<String, String> options,
            String command,
            String... allowedOptionNames) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(command, "command");
        LinkedHashSet<String> allowed = new LinkedHashSet<String>();
        for (String name : allowedOptionNames) {
            allowed.add(normalizeKey(name));
        }
        for (String key : options.keySet()) {
            if (key.startsWith("arg")) {
                continue;
            }
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown option for " + command + ": --"
                        + key.replace('_', '-')
                        + ". Supported options: " + supportedOptions(allowed));
            }
        }
    }

    static void requirePositionalCount(
            Map<String, String> options,
            String command,
            int maxPositionals,
            String usage) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(command, "command");
        for (Map.Entry<String, String> entry : options.entrySet()) {
            int index = positionalIndex(entry.getKey());
            if (index >= maxPositionals) {
                throw new IllegalArgumentException("Unexpected positional argument for " + command + ": "
                        + entry.getValue()
                        + ". Positional usage: " + usage);
            }
        }
    }

    static int instanceOnlyPositionalLimit(Map<String, String> options) {
        return options.containsKey("instance") ? 0 : 1;
    }

    static int instanceAndPricingPositionalLimit(Map<String, String> options) {
        int limit = instanceOnlyPositionalLimit(options);
        if (!hasNamedPricing(options)) {
            limit++;
        }
        return limit;
    }

    static boolean hasPricingSelection(Map<String, String> options) {
        return hasNamedPricing(options) || positionalPricing(options) != null;
    }

    private static String supportedOptions(Set<String> allowed) {
        if (allowed.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (String option : allowed) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("--").append(option.replace('_', '-'));
        }
        return builder.toString();
    }

    private static int positionalIndex(String key) {
        if (!key.startsWith("arg")) {
            return -1;
        }
        return Integer.parseInt(key.substring(3));
    }

    static Instance readInstance(Map<String, String> options, String defaultFixture) throws Exception {
        return new TinyJsonReader().read(instancePath(options, defaultFixture));
    }

    static Instance readBenchmarkInstance(Map<String, String> options, String defaultFixture) throws Exception {
        return new BenchmarkInstanceReader().read(instancePath(options, defaultFixture));
    }

    static Path instancePath(Map<String, String> options, String defaultFixture) {
        String value = options.get("instance");
        if (value == null) {
            value = options.get("arg0");
        }
        return value == null ? referencePath(defaultFixture) : Path.of(value);
    }

    static PricingMode pricingMode(Map<String, String> options, PricingMode defaultMode) {
        String value = options.get("pricing");
        if (value == null) {
            value = options.get("mode");
        }
        if (value == null) {
            value = positionalPricing(options);
        }
        return value == null ? defaultMode : PricingMode.fromString(value);
    }

    private static boolean hasNamedPricing(Map<String, String> options) {
        return options.get("pricing") != null || options.get("mode") != null;
    }

    private static String positionalPricing(Map<String, String> options) {
        return options.containsKey("instance") ? options.get("arg0") : options.get("arg1");
    }

    static String option(Map<String, String> options, String name, String defaultValue) {
        return options.getOrDefault(normalizeKey(name), defaultValue);
    }

    static boolean enabled(Map<String, String> options, String name) {
        String value = options.get(normalizeKey(name));
        return value != null
                && !"false".equalsIgnoreCase(value)
                && !"0".equals(value)
                && !"none".equalsIgnoreCase(value);
    }

    static int enabledCsvCount(String csv) {
        if (csv == null || csv.isBlank() || "none".equalsIgnoreCase(csv.trim())) {
            return 0;
        }
        int count = 0;
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                count++;
            }
        }
        return count;
    }

    static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    static double gap(double lowerBound, double upperBound) {
        if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound)) {
            return Double.NaN;
        }
        double denominator = Math.max(1.0, Math.abs(upperBound));
        return (upperBound - lowerBound) / denominator;
    }

    static Path referencePath(String name) {
        Path[] candidates = new Path[] {
                Path.of("..", "references", "tiny", name),
                Path.of("references", "tiny", name),
                Path.of("G:\\bid\\references\\tiny", name)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate tiny reference file: " + name);
    }

    private static String normalizeKey(String value) {
        Objects.requireNonNull(value, "value");
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
