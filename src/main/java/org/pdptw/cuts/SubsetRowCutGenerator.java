package org.pdptw.cuts;

import org.pdptw.core.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SubsetRowCutGenerator {
    private SubsetRowCutGenerator() {
    }

    public static SubsetRowCut l2Triple(String name, int first, int second, int third, double sigma) {
        return SubsetRowCut.ofL2Triple(name, first, second, third, sigma);
    }

    public static SubsetRowCut tinyCReference(double sigma) {
        return SubsetRowCut.ofL2Triple("sr-U123-l2", 1, 2, 3, sigma);
    }

    public static List<SubsetRowCut> allL2Triples(Instance instance, double sigma) {
        Objects.requireNonNull(instance, "instance");
        ArrayList<SubsetRowCut> cuts = new ArrayList<SubsetRowCut>();
        List<Integer> requestIds = instance.requestIds();
        for (int a = 0; a < requestIds.size(); a++) {
            for (int b = a + 1; b < requestIds.size(); b++) {
                for (int c = b + 1; c < requestIds.size(); c++) {
                    int first = requestIds.get(a).intValue();
                    int second = requestIds.get(b).intValue();
                    int third = requestIds.get(c).intValue();
                    cuts.add(SubsetRowCut.ofL2Triple(
                            "sr-U" + first + second + third + "-l2",
                            first,
                            second,
                            third,
                            sigma));
                }
            }
        }
        return List.copyOf(cuts);
    }
}
