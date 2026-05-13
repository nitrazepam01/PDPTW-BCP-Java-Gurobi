package org.pdptw.validation;

import org.pdptw.core.Instance;
import org.pdptw.core.Route;
import org.pdptw.io.TinyJsonReader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ReducedCostAuditorPlainTest {
    private ReducedCostAuditorPlainTest() {
    }

    public static void main(String[] args) throws Exception {
        Instance instance = new TinyJsonReader().read(referencePath("tiny-a-wide.json"));
        Route route = Route.of(0, 1, 2, 3, 4, 5);

        assertClose("direct", -2.0, ReducedCostAuditor.directReducedCost(instance, route));
        assertClose("direct-list", -2.0, ReducedCostAuditor.directReducedCost(instance, route.vertexIds()));
        assertClose("alpha=1", -2.0, ReducedCostAuditor.alphaArcReducedCost(instance, route, 1.0));
        assertClose("alpha=0", -2.0, ReducedCostAuditor.alphaArcReducedCost(instance, route, 0.0));

        ReducedCostAuditor.assertDirectEqualsAlpha(instance, route, 1.0);
        ReducedCostAuditor.assertDirectEqualsAlpha(instance, route, 0.0);

        double pickupArcForward = ReducedCostAuditor.arcReducedCost(instance, 1, 2, 1.0);
        double pickupArcBackward = ReducedCostAuditor.arcReducedCost(instance, 1, 2, 0.0);
        if (Math.abs(pickupArcForward - pickupArcBackward) < 1.0e-7) {
            throw new AssertionError("alpha=1 and alpha=0 matrices should differ on pickup-pickup arc 1->2");
        }

        System.out.println("ReducedCostAuditorPlainTest OK");
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
        if (Math.abs(expected - actual) > 1.0e-7) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }
}
