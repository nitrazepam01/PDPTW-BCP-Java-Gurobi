package org.pdptw.cli;

import org.pdptw.core.Instance;
import org.pdptw.core.Request;
import org.pdptw.core.Vertex;
import org.pdptw.io.BenchmarkInstanceReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RunBenchmarkSubinstance {
    private RunBenchmarkSubinstance() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        CliSupport.requireKnownOptions(options, "benchmark-subinstance", "instance", "output", "requests");
        CliSupport.requirePositionalCount(
                options,
                "benchmark-subinstance",
                CliSupport.instanceOnlyPositionalLimit(options),
                "[instance]");
        if (!options.containsKey("output")) {
            throw new IllegalArgumentException("benchmark-subinstance requires --output");
        }
        int requests = parseRequests(options);
        Path input = CliSupport.instancePath(options, null);
        Path output = Path.of(options.get("output"));
        Instance instance = new BenchmarkInstanceReader().read(input);
        if (requests > instance.nRequests()) {
            throw new IllegalArgumentException("requested subinstance size exceeds source requests: "
                    + requests + " > " + instance.nRequests());
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, toRcStyle(instance, requests), StandardCharsets.UTF_8);
        System.out.println("source,output,requests,vertices,capacity,maxVehicles");
        System.out.println(input.getFileName()
                + ","
                + output.getFileName()
                + ","
                + requests
                + ","
                + (2 * requests + 2)
                + ","
                + instance.vehicleCapacity()
                + ","
                + requests);
    }

    private static int parseRequests(Map<String, String> options) {
        String value = CliSupport.option(options, "requests", "6");
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("requests must be positive: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("requests must be an integer: " + value, exception);
        }
    }

    private static String toRcStyle(Instance instance, int requests) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(String.format(
                Locale.ROOT,
                "%d %d 1000 %d 1000",
                requests,
                requests,
                instance.vehicleCapacity()));
        lines.add(formatNode(
                0,
                instance.vertex(instance.startDepotId()),
                instance.vertex(instance.startDepotId()).demand()));
        for (int requestId = 1; requestId <= requests; requestId++) {
            Request request = instance.request(requestId);
            lines.add(formatNode(requestId, instance.vertex(request.pickupVertexId()), positiveDemand(request)));
        }
        for (int requestId = 1; requestId <= requests; requestId++) {
            Request request = instance.request(requestId);
            lines.add(formatNode(requests + requestId, instance.vertex(request.deliveryVertexId()), negativeDemand(request)));
        }
        lines.add(formatNode(
                2 * requests + 1,
                instance.vertex(instance.endDepotId()),
                instance.vertex(instance.endDepotId()).demand()));
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private static int positiveDemand(Request request) {
        return Math.max(1, Math.abs(request.demand()));
    }

    private static int negativeDemand(Request request) {
        return -positiveDemand(request);
    }

    private static String formatNode(int id, Vertex vertex, int demand) {
        return String.format(
                Locale.ROOT,
                "%3d %8.3f %8.3f %4.0f %4d %8.0f %8.0f",
                id,
                vertex.x(),
                vertex.y(),
                vertex.serviceTime(),
                demand,
                vertex.readyTime(),
                vertex.dueTime());
    }
}
