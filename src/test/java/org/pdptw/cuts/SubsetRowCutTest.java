package org.pdptw.cuts;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Vertex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SubsetRowCutTest {
    private static final double TOLERANCE = 1.0e-7;
    private static final double SIGMA = -4.0;

    private SubsetRowCutTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("SubsetRowCutTest OK");
    }

    public static void run() throws Exception {
        RobustCutsRepairTest.run();
        assertTinyCReferenceFixture();
        assertTinyCExpectedCoefficients();
        assertForwardPickupParityTransitions();
        assertBackwardDeliveryParityTransitions();
        assertMergeCorrection();
        assertGeneratorTriples();
    }

    private static void assertTinyCReferenceFixture() throws Exception {
        String json = Files.readString(referencePath("tiny-c-subset-row.json"));
        if (!json.contains("\"name\": \"sr-U123-l2\"")
                || !json.contains("\"requests\": [1, 2, 3]")
                || !json.contains("\"dualSigmaExample\": -4.0")) {
            throw new AssertionError("Tiny-C subset-row fixture does not expose expected SR cut metadata");
        }
    }

    private static void assertTinyCExpectedCoefficients() throws Exception {
        SubsetRowCut cut = SubsetRowCutGenerator.tinyCReference(SIGMA);
        Instance instance = tinyCInstance();
        List<String> rows = Files.readAllLines(expectedPath("tiny-c-subset-row.csv"));
        if (rows.size() != 4) {
            throw new AssertionError("Tiny-C expected CSV should contain header plus 3 rows");
        }

        assertExpectedCsvRow(cut, rows.get(1), ints(0, 1, 4, 7), instance);
        assertExpectedCsvRow(cut, rows.get(2), ints(0, 1, 2, 4, 5, 7), instance);
        assertExpectedCsvRow(cut, rows.get(3), ints(0, 1, 2, 3, 4, 5, 6, 7), instance);

        assertEquals("Tiny-C rhs floor(|U|/2)", 1, cut.rhs());
        assertClose("Tiny-C serving 3 direct SR pricing adjustment",
                4.0,
                SRPricingAdjuster.routePricingAdjustment(cut, ints(1, 2, 3)));
    }

    private static void assertExpectedCsvRow(
            SubsetRowCut cut,
            String row,
            List<Integer> route,
            Instance instance) {
        List<String> fields = splitCsv(row);
        int count = Integer.parseInt(fields.get(1));
        int expectedCoefficient = Integer.parseInt(fields.get(2));
        List<Integer> served = parseServedRequests(fields.get(0));
        assertEquals("Tiny-C CSV count " + row, count, served.size());
        assertEquals("Tiny-C coefficient from served requests " + served,
                expectedCoefficient,
                cut.coefficientForServedRequests(served));
        assertEquals("Tiny-C coefficient from route " + route,
                expectedCoefficient,
                cut.coefficientForRoute(instance, route));
    }

    private static void assertForwardPickupParityTransitions() {
        SubsetRowCut cut = SubsetRowCutGenerator.tinyCReference(SIGMA);
        Instance instance = tinyCInstance();
        SubsetRowResourceState state = SubsetRowResourceState.empty(cut);

        SRPricingAdjuster.Transition first = SRPricingAdjuster.forwardPickupTransition(state, instance, 1);
        assertEquals("forward first pickup delta", 0, first.coefficientDelta());
        assertClose("forward first pickup rc", 0.0, first.reducedCostAdjustment());
        assertEquals("forward first pickup parity", 1, first.after().parity());

        SRPricingAdjuster.Transition second = SRPricingAdjuster.forwardPickupTransition(first.after(), instance, 2);
        assertEquals("forward second pickup delta", 1, second.coefficientDelta());
        assertClose("forward second pickup rc", 4.0, second.reducedCostAdjustment());
        assertEquals("forward second pickup parity", 0, second.after().parity());

        SRPricingAdjuster.Transition third = SRPricingAdjuster.forwardPickupTransition(second.after(), instance, 3);
        assertEquals("forward third pickup delta", 0, third.coefficientDelta());
        assertClose("forward third pickup rc", 0.0, third.reducedCostAdjustment());
        assertEquals("forward third pickup parity", 1, third.after().parity());
        assertClose("forward total adjustment equals route adjustment",
                SRPricingAdjuster.routePricingAdjustment(cut, ints(1, 2, 3)),
                first.reducedCostAdjustment() + second.reducedCostAdjustment() + third.reducedCostAdjustment());
    }

    private static void assertBackwardDeliveryParityTransitions() {
        SubsetRowCut cut = SubsetRowCutGenerator.tinyCReference(SIGMA);
        Instance instance = tinyCInstance();
        SubsetRowResourceState state = SubsetRowResourceState.empty(cut);

        SRPricingAdjuster.Transition first = SRPricingAdjuster.backwardDeliveryTransition(state, instance, 6);
        assertEquals("backward first delivery delta", 0, first.coefficientDelta());
        assertClose("backward first delivery rc", 0.0, first.reducedCostAdjustment());
        assertEquals("backward first delivery parity", 1, first.after().parity());

        SRPricingAdjuster.Transition second = SRPricingAdjuster.backwardDeliveryTransition(first.after(), instance, 5);
        assertEquals("backward second delivery delta", 1, second.coefficientDelta());
        assertClose("backward second delivery rc", 4.0, second.reducedCostAdjustment());
        assertEquals("backward second delivery parity", 0, second.after().parity());

        SRPricingAdjuster.Transition third = SRPricingAdjuster.backwardDeliveryTransition(second.after(), instance, 4);
        assertEquals("backward third delivery delta", 0, third.coefficientDelta());
        assertClose("backward third delivery rc", 0.0, third.reducedCostAdjustment());
        assertEquals("backward third delivery parity", 1, third.after().parity());
    }

    private static void assertMergeCorrection() {
        SubsetRowCut cut = SubsetRowCutGenerator.tinyCReference(SIGMA);
        long forwardOpen = BitSetOps.add(BitSetOps.add(0L, 1), 2);
        long backwardOpen = BitSetOps.add(0L, 1);
        SubsetRowResourceState forwardState = SubsetRowResourceState.of(cut, 2);
        SubsetRowResourceState backwardState = SubsetRowResourceState.of(cut, 3);

        assertEquals("SR merge correction coefficient",
                1,
                SRPricingAdjuster.mergeCorrectionCoefficient(
                        cut,
                        forwardOpen,
                        backwardOpen,
                        forwardState,
                        backwardState));
        assertClose("SR merge correction sigma term",
                -4.0,
                SRPricingAdjuster.mergeCorrection(cut, forwardOpen, backwardOpen, forwardState, backwardState));

        double forwardPartial = SRPricingAdjuster.pricingAdjustmentForCoefficient(cut, forwardState.coefficient());
        double backwardPartial = SRPricingAdjuster.pricingAdjustmentForCoefficient(cut, backwardState.coefficient());
        double merged = SRPricingAdjuster.correctedMergedReducedCost(
                forwardPartial,
                backwardPartial,
                cut,
                forwardOpen,
                backwardOpen,
                forwardState,
                backwardState);
        double direct = SRPricingAdjuster.routePricingAdjustment(cut, ints(1, 2, 3));
        assertClose("SR corrected merged contribution equals direct route contribution", direct, merged);
    }

    private static void assertGeneratorTriples() {
        List<SubsetRowCut> triples = SubsetRowCutGenerator.allL2Triples(tinyCInstance(), SIGMA);
        assertEquals("Tiny-C all l2 triples", 1, triples.size());
        SubsetRowCut cut = triples.get(0);
        assertEquals("Tiny-C generated l", 2, cut.l());
        assertEquals("Tiny-C generated coefficient", 1, cut.coefficientForServedRequests(ints(1, 2, 3)));
    }

    private static Instance tinyCInstance() {
        List<Vertex> vertices = List.of(
                new Vertex(0, Vertex.Type.DEPOT_START, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0),
                new Vertex(1, Vertex.Type.PICKUP, 1, 1.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(2, Vertex.Type.PICKUP, 2, 2.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(3, Vertex.Type.PICKUP, 3, 3.0, 0.0, 0.0, 100.0, 0.0, 1),
                new Vertex(4, Vertex.Type.DELIVERY, 1, 4.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(5, Vertex.Type.DELIVERY, 2, 5.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(6, Vertex.Type.DELIVERY, 3, 6.0, 0.0, 0.0, 100.0, 0.0, -1),
                new Vertex(7, Vertex.Type.DEPOT_END, 0, 0.0, 0.0, 0.0, 100.0, 0.0, 0));
        double[][] matrix = new double[8][8];
        for (int from = 0; from < matrix.length; from++) {
            for (int to = 0; to < matrix[from].length; to++) {
                matrix[from][to] = Math.abs(from - to);
            }
        }
        return new Instance(
                "tiny-c-subset-row-test",
                3,
                3,
                2,
                vertices,
                matrix,
                matrix,
                Collections.emptyMap(),
                0.0);
    }

    private static Path referencePath(String name) {
        Path path = Path.of("G:\\bid\\references\\tiny", name);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Cannot locate tiny reference file: " + name);
        }
        return path;
    }

    private static Path expectedPath(String name) {
        Path path = Path.of("G:\\bid\\references\\tiny\\expected", name);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Cannot locate tiny expected file: " + name);
        }
        return path;
    }

    private static List<String> splitCsv(String row) {
        ArrayList<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static List<Integer> parseServedRequests(String value) {
        if (value.isBlank()) {
            return Collections.emptyList();
        }
        String[] pieces = value.split(";");
        ArrayList<Integer> requests = new ArrayList<Integer>();
        for (String piece : pieces) {
            requests.add(Integer.valueOf(Integer.parseInt(piece)));
        }
        return requests;
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected " + expected + " but got " + actual);
        }
    }

    private static List<Integer> ints(Integer... values) {
        return Arrays.asList(values);
    }
}
