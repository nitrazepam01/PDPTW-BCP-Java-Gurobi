package org.pdptw.pricing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DynamicHalfwayController {
    private static final double EPS = 1.0e-9;

    private double backwardLowerBound;
    private double forwardUpperBound;
    private int processedForwardLabels;
    private int processedBackwardLabels;
    private int generatedForwardLabels;
    private int generatedBackwardLabels;
    private int unprocessedForwardLabels;
    private int unprocessedBackwardLabels;
    private final ArrayList<Decision> decisions = new ArrayList<Decision>();

    public DynamicHalfwayController(
            double backwardLowerBound,
            double forwardUpperBound,
            int unprocessedForwardLabels,
            int unprocessedBackwardLabels) {
        this.backwardLowerBound = requireFinite(backwardLowerBound, "backwardLowerBound");
        this.forwardUpperBound = requireFinite(forwardUpperBound, "forwardUpperBound");
        if (this.backwardLowerBound > this.forwardUpperBound + EPS) {
            throw new IllegalArgumentException("dynamic half-way bounds require HB <= HF");
        }
        this.unprocessedForwardLabels = requireNonNegative(unprocessedForwardLabels, "unprocessedForwardLabels");
        this.unprocessedBackwardLabels = requireNonNegative(unprocessedBackwardLabels, "unprocessedBackwardLabels");
        this.generatedForwardLabels = unprocessedForwardLabels;
        this.generatedBackwardLabels = unprocessedBackwardLabels;
    }

    public static Direction chooseDirection(int unprocessedForwardLabels, int unprocessedBackwardLabels) {
        requireNonNegative(unprocessedForwardLabels, "unprocessedForwardLabels");
        requireNonNegative(unprocessedBackwardLabels, "unprocessedBackwardLabels");
        if (unprocessedForwardLabels == 0 && unprocessedBackwardLabels == 0) {
            return Direction.DONE;
        }
        if (unprocessedForwardLabels == 0) {
            return Direction.BACKWARD;
        }
        if (unprocessedBackwardLabels == 0) {
            return Direction.FORWARD;
        }
        return unprocessedForwardLabels <= unprocessedBackwardLabels
                ? Direction.FORWARD
                : Direction.BACKWARD;
    }

    public Direction nextDirection() {
        return chooseDirection(unprocessedForwardLabels, unprocessedBackwardLabels);
    }

    public Decision process(Direction direction, double labelTime) {
        return process(direction, labelTime, 0, 0);
    }

    public Decision process(
            Direction direction,
            double labelTime,
            int generatedForwardByStep,
            int generatedBackwardByStep) {
        return process(
                direction,
                labelTime,
                generatedForwardByStep,
                generatedForwardByStep,
                generatedBackwardByStep,
                generatedBackwardByStep);
    }

    public Decision process(
            Direction direction,
            double labelTime,
            int generatedForwardByStep,
            int queuedForwardByStep,
            int generatedBackwardByStep,
            int queuedBackwardByStep) {
        if (direction == null || direction == Direction.DONE) {
            throw new IllegalArgumentException("direction must be FORWARD or BACKWARD");
        }
        requireFinite(labelTime, "labelTime");
        requireNonNegative(generatedForwardByStep, "generatedForwardByStep");
        requireNonNegative(queuedForwardByStep, "queuedForwardByStep");
        requireNonNegative(generatedBackwardByStep, "generatedBackwardByStep");
        requireNonNegative(queuedBackwardByStep, "queuedBackwardByStep");
        if (queuedForwardByStep > generatedForwardByStep) {
            throw new IllegalArgumentException("queuedForwardByStep cannot exceed generatedForwardByStep");
        }
        if (queuedBackwardByStep > generatedBackwardByStep) {
            throw new IllegalArgumentException("queuedBackwardByStep cannot exceed generatedBackwardByStep");
        }

        Snapshot before = snapshot();
        if (direction == Direction.FORWARD) {
            if (unprocessedForwardLabels == 0) {
                throw new IllegalStateException("cannot process forward: no unprocessed forward labels");
            }
            unprocessedForwardLabels--;
            processedForwardLabels++;
            backwardLowerBound = Math.max(backwardLowerBound, Math.min(labelTime, forwardUpperBound));
        } else {
            if (unprocessedBackwardLabels == 0) {
                throw new IllegalStateException("cannot process backward: no unprocessed backward labels");
            }
            unprocessedBackwardLabels--;
            processedBackwardLabels++;
            forwardUpperBound = Math.min(forwardUpperBound, Math.max(labelTime, backwardLowerBound));
        }

        generatedForwardLabels += generatedForwardByStep;
        generatedBackwardLabels += generatedBackwardByStep;
        unprocessedForwardLabels += queuedForwardByStep;
        unprocessedBackwardLabels += queuedBackwardByStep;
        if (backwardLowerBound > forwardUpperBound + EPS) {
            throw new IllegalStateException("dynamic half-way bounds violated HB <= HF");
        }

        Decision decision = new Decision(direction, before, snapshot());
        decisions.add(decision);
        return decision;
    }

    public Snapshot pruneUnprocessed(Direction direction, int count) {
        if (direction == null || direction == Direction.DONE) {
            throw new IllegalArgumentException("direction must be FORWARD or BACKWARD");
        }
        requireNonNegative(count, "count");
        if (direction == Direction.FORWARD) {
            if (count > unprocessedForwardLabels) {
                throw new IllegalArgumentException("cannot prune more unprocessed forward labels than queued");
            }
            unprocessedForwardLabels -= count;
        } else {
            if (count > unprocessedBackwardLabels) {
                throw new IllegalArgumentException("cannot prune more unprocessed backward labels than queued");
            }
            unprocessedBackwardLabels -= count;
        }
        return snapshot();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                backwardLowerBound,
                forwardUpperBound,
                processedForwardLabels,
                processedBackwardLabels,
                generatedForwardLabels,
                generatedBackwardLabels,
                unprocessedForwardLabels,
                unprocessedBackwardLabels);
    }

    public List<Decision> decisions() {
        return Collections.unmodifiableList(decisions);
    }

    public boolean terminated() {
        return nextDirection() == Direction.DONE;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    public enum Direction {
        FORWARD,
        BACKWARD,
        DONE
    }

    public record Decision(Direction direction, Snapshot before, Snapshot after) {
        public Decision {
            if (direction == null || direction == Direction.DONE) {
                throw new IllegalArgumentException("direction must be FORWARD or BACKWARD");
            }
            if (before == null || after == null) {
                throw new IllegalArgumentException("decision snapshots must not be null");
            }
        }
    }

    public record Snapshot(
            double backwardLowerBound,
            double forwardUpperBound,
            int processedForwardLabels,
            int processedBackwardLabels,
            int generatedForwardLabels,
            int generatedBackwardLabels,
            int unprocessedForwardLabels,
            int unprocessedBackwardLabels) {
        public Snapshot {
            requireFinite(backwardLowerBound, "backwardLowerBound");
            requireFinite(forwardUpperBound, "forwardUpperBound");
            if (backwardLowerBound > forwardUpperBound + EPS) {
                throw new IllegalArgumentException("snapshot requires HB <= HF");
            }
            requireNonNegative(processedForwardLabels, "processedForwardLabels");
            requireNonNegative(processedBackwardLabels, "processedBackwardLabels");
            requireNonNegative(generatedForwardLabels, "generatedForwardLabels");
            requireNonNegative(generatedBackwardLabels, "generatedBackwardLabels");
            requireNonNegative(unprocessedForwardLabels, "unprocessedForwardLabels");
            requireNonNegative(unprocessedBackwardLabels, "unprocessedBackwardLabels");
        }
    }
}
