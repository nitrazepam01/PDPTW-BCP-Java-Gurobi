package org.pdptw.master;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;

import java.util.List;

public final class MasterValueObjectsTest {
    public static void main(String[] args) {
        run();
    }

    public static void run() {
        Instance instance = MasterTestSupport.tinyTwoRequestInstance(8.0, 1);
        Route route = MasterTestSupport.routeServingBothRequests();

        RouteColumn real = RouteColumn.fromRoute("r12", route, instance);
        MasterTestSupport.assertFalse(real.isArtificial(), "real route column must not be artificial");
        MasterTestSupport.assertEquals(1.0, real.fleetCoefficient(), 1.0e-12,
                "real route column fleet coefficient");
        MasterTestSupport.assertTrue(real.covers(1), "real route column covers request 1");
        MasterTestSupport.assertTrue(real.covers(2), "real route column covers request 2");

        RouteColumn artificial = RouteColumn.artificial("art1", 1, 1.0e6);
        MasterTestSupport.assertTrue(artificial.isArtificial(), "artificial column marker");
        MasterTestSupport.assertEquals(0.0, artificial.fleetCoefficient(), 1.0e-12,
                "artificial fleet coefficient");
        MasterTestSupport.assertEquals(1, artificial.servedRequests().size(),
                "artificial serves one request");
        MasterTestSupport.assertTrue(artificial.covers(1), "artificial covers its request");

        ColumnPool pool = new ColumnPool();
        MasterTestSupport.assertTrue(pool.add(real), "first real route is accepted");
        MasterTestSupport.assertFalse(pool.add(RouteColumn.fromRoute("duplicate_name_ignored", route, instance)),
                "same route signature is de-duplicated");
        MasterTestSupport.assertTrue(pool.add(artificial), "artificial route is accepted");
        MasterTestSupport.assertEquals(2, pool.size(), "pool size after de-duplication");
        MasterTestSupport.assertEquals(1, pool.realColumns().size(), "real column view");
        MasterTestSupport.assertEquals(1, pool.artificialColumns().size(), "artificial column view");

        DualSolution duals = DualSolution.ofOneIndexed(new double[]{0.0, 3.0, 5.0}, 2.0);
        MasterTestSupport.assertEquals(-2.0, duals.directReducedCost(real), 1.0e-12,
                "Tiny-A direct reduced cost contract");
        MasterTestSupport.assertEquals(-2.0, duals.directReducedCost(route, instance), 1.0e-12,
                "route direct reduced cost contract");

        ArtificialColumnFactory factory = new ArtificialColumnFactory(99.0);
        List<RouteColumn> artificials = factory.forInstance(instance);
        MasterTestSupport.assertEquals(2, artificials.size(), "one artificial column per request");
        MasterTestSupport.assertEquals(99.0, artificials.get(0).cost(), 1.0e-12,
                "artificial penalty is used as objective coefficient");
    }
}
