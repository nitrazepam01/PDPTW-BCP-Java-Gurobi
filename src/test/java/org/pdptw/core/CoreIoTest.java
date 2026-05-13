package org.pdptw.core;

import org.pdptw.TestSupport;
import org.pdptw.io.BenchmarkInstanceReader;
import org.pdptw.io.TinyJsonReader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

public final class CoreIoTest {
    private CoreIoTest() {
    }

    public static void run() throws Exception {
        TinyJsonReader reader = new TinyJsonReader();
        Instance tinyA = reader.read(Path.of("..", "references", "tiny", "tiny-a-wide.json"));
        Instance tinyB = reader.read(Path.of("..", "references", "tiny", "tiny-b-capacity-precedence.json"));
        RouteChecker checker = new RouteChecker();

        Route tinyAWide = Route.of(0, 1, 2, 3, 4, 5);
        RouteChecker.Result tinyAResult = checker.check(tinyA, tinyAWide);
        TestSupport.check(tinyAResult.feasible(), "Tiny-A key route must be feasible");
        TestSupport.equalsDouble(8.0, tinyAResult.cost(), 1e-9, "Tiny-A key route cost");

        Route tinyBCapacity = Route.of(0, 1, 2, 3, 4, 5);
        RouteChecker.Result capacity = checker.check(tinyB, tinyBCapacity);
        TestSupport.check(!capacity.feasible(), "Tiny-B capacity route must be infeasible");
        TestSupport.check(RouteChecker.CAPACITY.equals(capacity.reason()), "Tiny-B capacity reason");

        Route deliveryFirstRoute = Route.of(0, 3, 1, 2, 4, 5);
        RouteChecker.Result deliveryFirst = checker.check(tinyB, deliveryFirstRoute);
        TestSupport.check(!deliveryFirst.feasible(), "Tiny-B delivery-before-pickup route must be infeasible");
        TestSupport.check(RouteChecker.DELIVERY_BEFORE_PICKUP.equals(deliveryFirst.reason()), "Tiny-B delivery-before-pickup reason");

        Route feasibleRoute = Route.of(0, 1, 3, 2, 4, 5);
        RouteChecker.Result feasible = checker.check(tinyB, feasibleRoute);
        TestSupport.check(feasible.feasible(), "Tiny-B expected feasible route");
        TestSupport.equalsDouble(18.0, feasible.cost(), 1e-9, "Tiny-B feasible route cost");

        Route pickupOnly = Route.of(0, 1, 5);
        RouteChecker.Result pairing = checker.check(tinyA, pickupOnly);
        TestSupport.check(!pairing.feasible(), "Pickup-only route must be infeasible");
        TestSupport.check(RouteChecker.OPEN_REQUESTS_AT_SINK.equals(pairing.reason()), "Pickup-only pairing reason");

        RouteChecker.Result late = checker.check(timeWindowInstance(), Route.of(0, 1, 2, 3));
        TestSupport.check(!late.feasible(), "Late route must be infeasible");
        TestSupport.check(RouteChecker.TIME_WINDOW.equals(late.reason()), "Late route reason");

        Route interiorDepot = Route.of(0, 1, 5, 3, 5);
        RouteChecker.Result depot = checker.check(tinyA, interiorDepot);
        TestSupport.check(!depot.feasible(), "Interior depot route must be infeasible");
        TestSupport.check(RouteChecker.DEPOT_SHAPE.equals(depot.reason()), "Interior depot reason");

        long mask = BitSetOps.add(BitSetOps.add(0L, 1), 3);
        TestSupport.check(BitSetOps.contains(mask, 1), "BitSetOps contains request 1");
        TestSupport.check(!BitSetOps.contains(mask, 2), "BitSetOps does not contain request 2");
        TestSupport.equalsInt(2, BitSetOps.size(mask), "BitSetOps size");

        assertBenchmarkReaders();
    }

    private static void assertBenchmarkReaders() throws Exception {
        BenchmarkInstanceReader reader = new BenchmarkInstanceReader();
        Instance aa30 = reader.read(Path.of("..", "PDPTW_instances", "RC", "AA30"));
        TestSupport.equalsInt(30, aa30.nRequests(), "AA30 request count");
        TestSupport.equalsInt(62, aa30.vertices().size(), "AA30 vertex count");
        TestSupport.equalsInt(15, aa30.vehicleCapacity(), "AA30 capacity");
        TestSupport.equalsInt(61, aa30.endDepotId(), "AA30 end depot");
        TestSupport.equalsInt(1, aa30.request(1).pickupVertexId(), "AA30 request 1 pickup");
        TestSupport.equalsInt(31, aa30.request(1).deliveryVertexId(), "AA30 request 1 delivery");
        TestSupport.equalsDouble(0.0, aa30.travelCost(0, 61), 1e-9, "AA30 depot distance");
        TestSupport.check("RC".equals(BenchmarkInstanceReader.paperGroup(Path.of("AA30"))), "AA30 paper group");
        TestSupport.check("RC_reverse".equals(BenchmarkInstanceReader.paperGroup(Path.of("AA30_reverse"))),
                "AA30 reverse paper group");

        Instance lc101 = reader.read(Path.of("..", "PDPTW_instances", "LL", "lc101.txt"));
        TestSupport.equalsInt(53, lc101.nRequests(), "lc101 request count");
        TestSupport.equalsInt(108, lc101.vertices().size(), "lc101 vertex count");
        TestSupport.equalsInt(200, lc101.vehicleCapacity(), "lc101 capacity");
        TestSupport.equalsInt(25, lc101.maxVehicles(), "lc101 max vehicles");
        TestSupport.equalsInt(107, lc101.endDepotId(), "lc101 generated end depot");
        Request paired = null;
        for (int requestId : lc101.requestIds()) {
            Request request = lc101.request(requestId);
            if (request.pickupVertexId() == 11 && request.deliveryVertexId() == 1) {
                paired = request;
                break;
            }
        }
        TestSupport.check(paired != null, "lc101 must preserve pickup 11 / delivery 1 pair");
        TestSupport.check("LL".equals(BenchmarkInstanceReader.paperGroup(Path.of("lc101.txt"))), "lc101 paper group");

        Instance lc201 = reader.read(Path.of("..", "PDPTW_instances", "LL", "lc201.txt"));
        TestSupport.equalsInt(51, lc201.nRequests(), "lc201 request count");
        TestSupport.equalsInt(104, lc201.vertices().size(), "lc201 vertex count");
        TestSupport.equalsInt(700, lc201.vehicleCapacity(), "lc201 capacity");
        TestSupport.equalsInt(25, lc201.maxVehicles(), "lc201 max vehicles");
    }

    private static Instance timeWindowInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.DELIVERY, 1, 2.0, 0.0, 0.0, 1.0, 0.0, -1),
                new Vertex(3, Vertex.Type.DEPOT_END, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0)
        );
        double[][] travel = {
                {0.0, 1.0, 2.0, 0.0},
                {1.0, 0.0, 5.0, 1.0},
                {2.0, 5.0, 0.0, 2.0},
                {0.0, 1.0, 2.0, 0.0}
        };
        return new Instance("time-window-test", 1, 1, 1, vertices, travel, travel, new LinkedHashMap<>(), 0.0);
    }
}
