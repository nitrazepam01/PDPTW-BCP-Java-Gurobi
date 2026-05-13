package org.pdptw.pricing;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.io.TinyJsonReader;
import org.pdptw.master.DualSolution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ReducedCostMatricesTest {
    private static final double TOLERANCE = 1.0e-7;

    private ReducedCostMatricesTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("ReducedCostMatricesTest OK");
    }

    public static void run() throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        Route route = Route.of(0, 1, 2, 3, 4, 5);
        List<Integer> vertexIds = route.vertexIds();

        ReducedCostMatrices matrices = ReducedCostMatrices.fromInstanceDuals(instance);
        assertClose("route cost", 8.0, matrices.routeCost(route));
        assertClose("direct route reduced cost", -2.0, matrices.directReducedCost(route));
        assertClose("direct list reduced cost", -2.0, matrices.directReducedCost(vertexIds));
        assertClose("alpha=1 arc sum", -2.0, matrices.forwardArcReducedCostSum(route));
        assertClose("alpha=0 arc sum", -2.0, matrices.backwardArcReducedCostSum(route));
        assertClose("alpha=1 list arc sum", -2.0, matrices.forwardArcReducedCostSum(vertexIds));
        assertClose("alpha=0 list arc sum", -2.0, matrices.backwardArcReducedCostSum(vertexIds));

        assertClose("alpha=1 start depot split dual", 2.0, matrices.splitVertexDual(0, 1.0));
        assertClose("alpha=1 pickup 1 split dual", 3.0, matrices.splitVertexDual(1, 1.0));
        assertClose("alpha=1 pickup 2 split dual", 5.0, matrices.splitVertexDual(2, 1.0));
        assertClose("alpha=1 delivery 1 split dual", 0.0, matrices.splitVertexDual(3, 1.0));
        assertClose("alpha=1 delivery 2 split dual", 0.0, matrices.splitVertexDual(4, 1.0));
        assertClose("alpha=1 end depot split dual", 2.0, matrices.splitVertexDual(5, 1.0));

        assertClose("alpha=0 pickup 1 split dual", 0.0, matrices.splitVertexDual(1, 0.0));
        assertClose("alpha=0 pickup 2 split dual", 0.0, matrices.splitVertexDual(2, 0.0));
        assertClose("alpha=0 delivery 1 split dual", 3.0, matrices.splitVertexDual(3, 0.0));
        assertClose("alpha=0 delivery 2 split dual", 5.0, matrices.splitVertexDual(4, 0.0));

        assertArcCosts(matrices);
        assertMatricesDiffer(matrices);
        assertDualSolutionConstructor(instance, route);
    }

    private static void assertArcCosts(ReducedCostMatrices matrices) {
        assertClose("alpha=1 0-1", -1.5, matrices.forwardArcReducedCost(0, 1));
        assertClose("alpha=1 1-2", -3.0, matrices.forwardArcReducedCost(1, 2));
        assertClose("alpha=1 2-3", -1.5, matrices.forwardArcReducedCost(2, 3));
        assertClose("alpha=1 3-4", 1.0, matrices.forwardArcReducedCost(3, 4));
        assertClose("alpha=1 4-5", 3.0, matrices.forwardArcReducedCost(4, 5));

        assertClose("alpha=0 0-1", 0.0, matrices.backwardArcReducedCost(0, 1));
        assertClose("alpha=0 1-2", 1.0, matrices.backwardArcReducedCost(1, 2));
        assertClose("alpha=0 2-3", -0.5, matrices.backwardArcReducedCost(2, 3));
        assertClose("alpha=0 3-4", -3.0, matrices.backwardArcReducedCost(3, 4));
        assertClose("alpha=0 4-5", 0.5, matrices.backwardArcReducedCost(4, 5));
    }

    private static void assertMatricesDiffer(ReducedCostMatrices matrices) {
        double[][] forward = matrices.forwardArcReducedCosts();
        double[][] backward = matrices.backwardArcReducedCosts();
        assertClose("forward matrix 1-2", -3.0, forward[1][2]);
        assertClose("backward matrix 1-2", 1.0, backward[1][2]);
        if (Math.abs(forward[1][2] - backward[1][2]) < TOLERANCE) {
            throw new AssertionError("forward alpha=1 and backward alpha=0 matrices should differ");
        }
    }

    private static void assertDualSolutionConstructor(Instance instance, Route route) {
        DualSolution duals = DualSolution.ofOneIndexed(new double[] {0.0, 3.0, 5.0}, 2.0);
        ReducedCostMatrices matrices = ReducedCostMatrices.fromDualSolution(instance, duals);
        assertClose("DualSolution direct", -2.0, matrices.directReducedCost(route));
        assertClose("DualSolution alpha=1", -2.0, matrices.forwardArcReducedCostSum(route));
        assertClose("DualSolution alpha=0", -2.0, matrices.backwardArcReducedCostSum(route));
    }

    private static Path referencePath(String name) {
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

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }
}
