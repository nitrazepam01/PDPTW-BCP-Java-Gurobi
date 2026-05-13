package org.pdptw.master;

import com.gurobi.gurobi.GRB;
import org.pdptw.core.Instance;
import org.pdptw.core.Route;

import java.lang.reflect.Method;
import java.util.List;

public final class GurobiRmpSmokeTest {
    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        Instance instance = MasterTestSupport.tinyTwoRequestInstance(10.0, 1);
        Route route = MasterTestSupport.routeServingBothRequests();

        try (GurobiRmp rmp = new GurobiRmp(instance, 1000.0)) {
            MasterTestSupport.assertEquals(2, rmp.artificialColumns().size(),
                    "RMP starts with one artificial column per request");
            MasterTestSupport.expectThrows(IllegalStateException.class, () -> {
                try {
                    rmp.dualSolution();
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }, "duals must not be available before LP solve");

            GurobiRmp.SolveResult initial = rmp.solveLp();
            MasterTestSupport.assertTrue(initial.isOptimal(), "initial artificial LP is feasible");
            MasterTestSupport.assertEquals(2000.0, initial.objectiveValue(), 1.0e-7,
                    "artificial LP objective");
            MasterTestSupport.assertTrue(rmp.hasPositiveArtificial(),
                    "initial LP uses artificial columns");

            MasterTestSupport.assertTrue(rmp.addRoute(route), "real route is added");
            GurobiRmp.SolveResult priced = rmp.solveLp();
            MasterTestSupport.assertTrue(priced.isOptimal(), "LP with real route is feasible");
            MasterTestSupport.assertEquals(10.0, priced.objectiveValue(), 1.0e-7,
                    "real route replaces artificial columns");
            MasterTestSupport.assertFalse(rmp.hasPositiveArtificial(),
                    "real route solution no longer uses artificial columns");
            MasterTestSupport.assertEquals(2, rmp.dualSolution().nRequests(), "dual vector length");
            MasterTestSupport.assertEquals(0.0, rmp.dualSolution().directReducedCost(route, instance), 1.0e-7,
                    "normalized LP duals make active route reduced cost zero");
        }

        RouteColumn real = RouteColumn.fromRoute("r12", route, instance);
        RouteColumn artificial = RouteColumn.artificial("art1", 1, 1000.0);
        try (FinalIntegerMaster master = new FinalIntegerMaster(instance, List.of(artificial, real))) {
            FinalIntegerMaster.IntegerSolution solution = master.solve();
            MasterTestSupport.assertTrue(solution.status() == GRB.OPTIMAL,
                    "final integer master solves real route columns");
            MasterTestSupport.assertEquals(1, master.columns().size(),
                    "final integer master excludes artificial columns");
            MasterTestSupport.assertEquals(10.0, solution.objectiveValue(), 1.0e-7,
                    "final integer objective");
            MasterTestSupport.assertEquals(1, solution.selectedColumns().size(),
                    "one selected route");
        }

        for (Method method : FinalIntegerMaster.class.getMethods()) {
            MasterTestSupport.assertFalse(method.getName().toLowerCase().contains("dual"),
                    "final integer master must not expose pricing-dual API");
        }
    }
}
