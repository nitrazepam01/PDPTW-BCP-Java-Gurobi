package org.pdptw.cuts;

import org.pdptw.core.Instance;

/**
 * Robust cut pricing contribution represented as signed arc reduced-cost prices.
 */
public interface RobustCut {
    String name();

    double dualValue();

    double arcCoefficient(Instance instance, int from, int to);

    default double arcPrice(Instance instance, int from, int to) {
        return dualValue() * arcCoefficient(instance, from, to);
    }

    record Arc(int from, int to) {
        public Arc {
            if (from < 0 || to < 0) {
                throw new IllegalArgumentException("arc endpoints must be non-negative: " + from + "->" + to);
            }
        }
    }
}
