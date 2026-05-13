package org.pdptw.cli;

import org.pdptw.core.Instance;
import org.pdptw.core.Request;
import org.pdptw.core.Route;
import org.pdptw.core.RouteChecker;
import org.pdptw.io.BenchmarkInstanceReader;

import java.nio.file.Path;
import java.util.Map;

public final class RunInstanceSmoke {
    private RunInstanceSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = CliSupport.parseOptions(args);
        CliSupport.requireKnownOptions(options, "instance-smoke", "instance");
        CliSupport.requirePositionalCount(
                options,
                "instance-smoke",
                CliSupport.instanceOnlyPositionalLimit(options),
                "[instance]");
        if (!options.containsKey("instance") && !options.containsKey("arg0")) {
            throw new IllegalArgumentException("instance-smoke requires --instance or a positional instance path");
        }
        Path path = CliSupport.instancePath(options, null);
        Instance instance = new BenchmarkInstanceReader().read(path);
        SmokeRow row = smoke(instance, path);
        System.out.println(SmokeRow.HEADER);
        System.out.println(row.toCsv());
    }

    static SmokeRow smoke(Instance instance, Path path) {
        int feasiblePairs = 0;
        Double firstFeasibleCost = null;
        RouteChecker checker = new RouteChecker();
        for (int requestId : instance.requestIds()) {
            Request request = instance.request(requestId);
            Route route = Route.of(
                    instance.startDepotId(),
                    request.pickupVertexId(),
                    request.deliveryVertexId(),
                    instance.endDepotId());
            RouteChecker.Result result = checker.check(instance, route);
            if (result.feasible()) {
                feasiblePairs++;
                if (firstFeasibleCost == null) {
                    firstFeasibleCost = Double.valueOf(result.cost());
                }
            }
        }
        return new SmokeRow(
                path.getFileName().toString(),
                BenchmarkInstanceReader.paperGroup(path),
                BenchmarkInstanceReader.formatName(path),
                instance.nRequests(),
                instance.vertices().size(),
                instance.vehicleCapacity(),
                instance.maxVehicles(),
                instance.startDepotId(),
                instance.endDepotId(),
                feasiblePairs,
                firstFeasibleCost == null ? Double.NaN : firstFeasibleCost.doubleValue());
    }

    record SmokeRow(
            String instance,
            String paperGroup,
            String format,
            int requests,
            int vertices,
            int capacity,
            int maxVehicles,
            int startDepot,
            int endDepot,
            int feasiblePairs,
            double firstFeasibleRouteCost) {
        static final String HEADER = "mode,instance,paperGroup,format,requests,vertices,capacity,maxVehicles,"
                + "startDepot,endDepot,feasibleSinglePairRoutes,firstFeasibleSinglePairCost";

        String toCsv() {
            return String.join(",",
                    "instance-smoke",
                    instance,
                    paperGroup,
                    format,
                    Integer.toString(requests),
                    Integer.toString(vertices),
                    Integer.toString(capacity),
                    Integer.toString(maxVehicles),
                    Integer.toString(startDepot),
                    Integer.toString(endDepot),
                    Integer.toString(feasiblePairs),
                    Double.toString(firstFeasibleRouteCost));
        }
    }
}
