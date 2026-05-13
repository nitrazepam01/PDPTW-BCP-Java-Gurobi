package org.pdptw.core;

import java.util.LinkedHashSet;
import java.util.Set;

public final class BitSetOps {
    private BitSetOps() {
    }

    public static long bit(int requestId) {
        if (requestId <= 0 || requestId > 63) {
            throw new IllegalArgumentException("Request id must be in 1..63: " + requestId);
        }
        return 1L << (requestId - 1);
    }

    public static long add(long mask, int requestId) {
        return mask | bit(requestId);
    }

    public static long remove(long mask, int requestId) {
        return mask & ~bit(requestId);
    }

    public static boolean contains(long mask, int requestId) {
        return (mask & bit(requestId)) != 0L;
    }

    public static boolean isSubset(long maybeSubset, long maybeSuperset) {
        return (maybeSubset & ~maybeSuperset) == 0L;
    }

    public static int size(long mask) {
        return Long.bitCount(mask);
    }

    public static Set<Integer> toSet(long mask) {
        Set<Integer> values = new LinkedHashSet<>();
        for (int requestId = 1; requestId <= 63; requestId++) {
            if (contains(mask, requestId)) {
                values.add(requestId);
            }
        }
        return values;
    }
}
