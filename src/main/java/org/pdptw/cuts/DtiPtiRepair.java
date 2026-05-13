package org.pdptw.cuts;

import org.pdptw.core.BitSetOps;
import org.pdptw.core.Instance;
import org.pdptw.core.Request;
import org.pdptw.core.Vertex;
import org.pdptw.pricing.ReducedCostMatrices;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DtiPtiRepair {
    public static final double DEFAULT_TOLERANCE = 1.0e-7;

    private DtiPtiRepair() {
    }

    public static double[][] forwardMatrixWithRobustCuts(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> cuts) {
        Objects.requireNonNull(matrices, "matrices");
        return addRobustArcPrices(matrices.instance(), matrices.forwardArcReducedCosts(), cuts);
    }

    public static double[][] backwardMatrixWithRobustCuts(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> cuts) {
        Objects.requireNonNull(matrices, "matrices");
        return addRobustArcPrices(matrices.instance(), matrices.backwardArcReducedCosts(), cuts);
    }

    public static double[][] addRobustArcPrices(
            Instance instance,
            double[][] baseMatrix,
            List<? extends RobustCut> cuts) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(cuts, "cuts");
        double[][] matrix = copyAndValidate(instance, baseMatrix);
        List<Vertex> vertices = instance.vertices();
        for (Vertex from : vertices) {
            for (Vertex to : vertices) {
                matrix[from.id()][to.id()] += robustArcPrice(instance, cuts, from.id(), to.id());
            }
        }
        return matrix;
    }

    public static double robustArcPrice(
            Instance instance,
            List<? extends RobustCut> cuts,
            int from,
            int to) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(cuts, "cuts");
        if (!instance.hasVertex(from) || !instance.hasVertex(to)) {
            throw new IllegalArgumentException("Unknown robust-cut arc: " + from + "->" + to);
        }
        double price = 0.0;
        for (RobustCut cut : cuts) {
            price += Objects.requireNonNull(cut, "cuts contains null").arcPrice(instance, from, to);
        }
        return requireFinite(price, "robustArcPrice");
    }

    public static RepairResult repairForwardDti(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> cuts) {
        Objects.requireNonNull(matrices, "matrices");
        return repairForwardDti(matrices.instance(), forwardMatrixWithRobustCuts(matrices, cuts));
    }

    public static RepairResult repairBackwardPti(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> cuts) {
        Objects.requireNonNull(matrices, "matrices");
        return repairBackwardPti(matrices.instance(), backwardMatrixWithRobustCuts(matrices, cuts));
    }

    public static RepairResult repairForwardDti(Instance instance, double[][] forwardMatrix) {
        Objects.requireNonNull(instance, "instance");
        double[][] repaired = copyAndValidate(instance, forwardMatrix);
        double[] theta = forwardTheta(instance, forwardMatrix);
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            Request request = instance.request(requestId);
            double half = 0.5 * theta[requestId];
            applyIncidentShift(instance, repaired, request.pickupVertexId(), -half);
            applyIncidentShift(instance, repaired, request.deliveryVertexId(), half);
        }
        return new RepairResult(Direction.FORWARD_DTI, repaired, theta);
    }

    public static RepairResult repairBackwardPti(Instance instance, double[][] backwardMatrix) {
        Objects.requireNonNull(instance, "instance");
        double[][] repaired = copyAndValidate(instance, backwardMatrix);
        double[] theta = backwardTheta(instance, backwardMatrix);
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            Request request = instance.request(requestId);
            double half = 0.5 * theta[requestId];
            applyIncidentShift(instance, repaired, request.pickupVertexId(), half);
            applyIncidentShift(instance, repaired, request.deliveryVertexId(), -half);
        }
        return new RepairResult(Direction.BACKWARD_PTI, repaired, theta);
    }

    public static double[] forwardTheta(Instance instance, double[][] forwardMatrix) {
        Objects.requireNonNull(instance, "instance");
        copyAndValidate(instance, forwardMatrix);
        double[] theta = new double[instance.nRequests() + 1];
        List<Vertex> vertices = instance.vertices();
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            int delivery = instance.request(requestId).deliveryVertexId();
            double maxViolation = 0.0;
            for (Vertex from : vertices) {
                for (Vertex to : vertices) {
                    double violation = forwardMatrix[from.id()][to.id()]
                            - forwardMatrix[from.id()][delivery]
                            - forwardMatrix[delivery][to.id()];
                    maxViolation = Math.max(maxViolation, violation);
                }
            }
            theta[requestId] = Math.max(0.0, maxViolation);
        }
        return theta;
    }

    public static double[] backwardTheta(Instance instance, double[][] backwardMatrix) {
        Objects.requireNonNull(instance, "instance");
        copyAndValidate(instance, backwardMatrix);
        double[] theta = new double[instance.nRequests() + 1];
        List<Vertex> vertices = instance.vertices();
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            int pickup = instance.request(requestId).pickupVertexId();
            double maxViolation = 0.0;
            for (Vertex from : vertices) {
                for (Vertex to : vertices) {
                    double violation = backwardMatrix[from.id()][to.id()]
                            - backwardMatrix[from.id()][pickup]
                            - backwardMatrix[pickup][to.id()];
                    maxViolation = Math.max(maxViolation, violation);
                }
            }
            theta[requestId] = Math.max(0.0, maxViolation);
        }
        return theta;
    }

    public static boolean satisfiesForwardDti(Instance instance, double[][] forwardMatrix) {
        return satisfiesForwardDti(instance, forwardMatrix, DEFAULT_TOLERANCE);
    }

    public static boolean satisfiesForwardDti(Instance instance, double[][] forwardMatrix, double tolerance) {
        return maxForwardDtiViolation(instance, forwardMatrix) <= requireTolerance(tolerance);
    }

    public static boolean satisfiesBackwardPti(Instance instance, double[][] backwardMatrix) {
        return satisfiesBackwardPti(instance, backwardMatrix, DEFAULT_TOLERANCE);
    }

    public static boolean satisfiesBackwardPti(Instance instance, double[][] backwardMatrix, double tolerance) {
        return maxBackwardPtiViolation(instance, backwardMatrix) <= requireTolerance(tolerance);
    }

    public static double maxForwardDtiViolation(Instance instance, double[][] forwardMatrix) {
        Objects.requireNonNull(instance, "instance");
        copyAndValidate(instance, forwardMatrix);
        double maxViolation = 0.0;
        List<Vertex> vertices = instance.vertices();
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            int delivery = instance.request(requestId).deliveryVertexId();
            for (Vertex from : vertices) {
                for (Vertex to : vertices) {
                    double violation = forwardMatrix[from.id()][to.id()]
                            - forwardMatrix[from.id()][delivery]
                            - forwardMatrix[delivery][to.id()];
                    maxViolation = Math.max(maxViolation, violation);
                }
            }
        }
        return maxViolation;
    }

    public static double maxBackwardPtiViolation(Instance instance, double[][] backwardMatrix) {
        Objects.requireNonNull(instance, "instance");
        copyAndValidate(instance, backwardMatrix);
        double maxViolation = 0.0;
        List<Vertex> vertices = instance.vertices();
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            int pickup = instance.request(requestId).pickupVertexId();
            for (Vertex from : vertices) {
                for (Vertex to : vertices) {
                    double violation = backwardMatrix[from.id()][to.id()]
                            - backwardMatrix[from.id()][pickup]
                            - backwardMatrix[pickup][to.id()];
                    maxViolation = Math.max(maxViolation, violation);
                }
            }
        }
        return maxViolation;
    }

    public static double arcReducedCostSum(double[][] matrix, List<Integer> vertexIds) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(vertexIds, "vertexIds");
        double sum = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            int from = vertexIds.get(i).intValue();
            int to = vertexIds.get(i + 1).intValue();
            if (from < 0 || from >= matrix.length || to < 0 || to >= matrix[from].length) {
                throw new IllegalArgumentException("route uses vertex outside matrix: " + from + "->" + to);
            }
            sum += matrix[from][to];
        }
        return sum;
    }

    public static double robustArcPriceSum(
            Instance instance,
            List<? extends RobustCut> cuts,
            List<Integer> vertexIds) {
        Objects.requireNonNull(vertexIds, "vertexIds");
        double sum = 0.0;
        for (int i = 0; i + 1 < vertexIds.size(); i++) {
            sum += robustArcPrice(instance, cuts, vertexIds.get(i).intValue(), vertexIds.get(i + 1).intValue());
        }
        return requireFinite(sum, "robustArcPriceSum");
    }

    public static double robustDirectReducedCost(
            ReducedCostMatrices matrices,
            List<? extends RobustCut> cuts,
            List<Integer> vertexIds) {
        Objects.requireNonNull(matrices, "matrices");
        return matrices.directReducedCost(vertexIds)
                + robustArcPriceSum(matrices.instance(), cuts, vertexIds);
    }

    public static double robustMergeCorrection(
            ReducedCostMatrices matrices,
            long forwardOpenMask,
            long backwardOpenMask,
            RepairResult forwardRepair,
            RepairResult backwardRepair) {
        Objects.requireNonNull(matrices, "matrices");
        requireDirection(forwardRepair, Direction.FORWARD_DTI, "forwardRepair");
        requireDirection(backwardRepair, Direction.BACKWARD_PTI, "backwardRepair");
        double correction = 0.0;
        long intersection = forwardOpenMask & backwardOpenMask;
        long symmetricDifference = (forwardOpenMask | backwardOpenMask) & ~intersection;
        for (int requestId = 1; requestId <= matrices.instance().nRequests(); requestId++) {
            double openPrice = matrices.requestDual(requestId)
                    + forwardRepair.theta(requestId)
                    + backwardRepair.theta(requestId);
            if (BitSetOps.contains(intersection, requestId)) {
                correction += openPrice;
            } else if (BitSetOps.contains(symmetricDifference, requestId)) {
                correction += 0.5 * openPrice;
            }
        }
        return correction;
    }

    public static double robustMergedReducedCost(
            double forwardReducedCost,
            double backwardReducedCost,
            ReducedCostMatrices matrices,
            long forwardOpenMask,
            long backwardOpenMask,
            RepairResult forwardRepair,
            RepairResult backwardRepair) {
        return forwardReducedCost
                + backwardReducedCost
                + robustMergeCorrection(matrices, forwardOpenMask, backwardOpenMask, forwardRepair, backwardRepair);
    }

    private static void requireDirection(RepairResult repair, Direction expected, String name) {
        Objects.requireNonNull(repair, name);
        if (repair.direction() != expected) {
            throw new IllegalArgumentException(name + " must be " + expected + " but was " + repair.direction());
        }
    }

    private static void applyIncidentShift(Instance instance, double[][] matrix, int vertexId, double shift) {
        if (Math.abs(shift) == 0.0) {
            return;
        }
        List<Vertex> vertices = instance.vertices();
        for (Vertex from : vertices) {
            for (Vertex to : vertices) {
                if (from.id() == vertexId || to.id() == vertexId) {
                    matrix[from.id()][to.id()] += shift;
                }
            }
        }
    }

    private static double[][] copyAndValidate(Instance instance, double[][] matrix) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(matrix, "matrix");
        int dimension = dimension(instance);
        if (matrix.length < dimension) {
            throw new IllegalArgumentException("matrix length " + matrix.length
                    + " is smaller than required dimension " + dimension);
        }
        double[][] copy = new double[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            if (matrix[row] == null) {
                throw new IllegalArgumentException("matrix row " + row + " is null");
            }
            if (matrix[row].length < dimension) {
                throw new IllegalArgumentException("matrix row " + row + " length " + matrix[row].length
                        + " is smaller than required dimension " + dimension);
            }
            copy[row] = matrix[row].clone();
        }
        for (Vertex from : instance.vertices()) {
            for (Vertex to : instance.vertices()) {
                requireFinite(copy[from.id()][to.id()], "matrix[" + from.id() + "][" + to.id() + "]");
            }
        }
        return copy;
    }

    private static double[][] copyMatrix(double[][] matrix) {
        double[][] copy = new double[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            copy[row] = matrix[row].clone();
        }
        return copy;
    }

    private static int dimension(Instance instance) {
        int dimension = 0;
        for (Vertex vertex : instance.vertices()) {
            dimension = Math.max(dimension, vertex.id() + 1);
        }
        return dimension;
    }

    private static double requireTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative: " + tolerance);
        }
        return tolerance;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    public enum Direction {
        FORWARD_DTI,
        BACKWARD_PTI
    }

    public static final class RepairResult {
        private final Direction direction;
        private final double[][] matrix;
        private final double[] theta;

        private RepairResult(Direction direction, double[][] matrix, double[] theta) {
            this.direction = Objects.requireNonNull(direction, "direction");
            this.matrix = copyMatrix(matrix);
            this.theta = theta.clone();
        }

        public Direction direction() {
            return direction;
        }

        public double[][] matrix() {
            return copyMatrix(matrix);
        }

        public double arcReducedCost(int from, int to) {
            return matrix[from][to];
        }

        public double arcReducedCostSum(List<Integer> vertexIds) {
            return DtiPtiRepair.arcReducedCostSum(matrix, vertexIds);
        }

        public double theta(int requestId) {
            if (requestId < 1 || requestId >= theta.length) {
                throw new IllegalArgumentException("requestId out of range: " + requestId);
            }
            return theta[requestId];
        }

        public double[] thetaOneIndexed() {
            return theta.clone();
        }

        public List<Double> thetaValues() {
            ArrayList<Double> values = new ArrayList<Double>();
            for (int requestId = 1; requestId < theta.length; requestId++) {
                values.add(Double.valueOf(theta[requestId]));
            }
            return List.copyOf(values);
        }
    }
}
