package org.pdptw.master;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBColumn;
import com.gurobi.gurobi.GRBConstr;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBModel;
import com.gurobi.gurobi.GRBVar;
import org.pdptw.core.Instance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class FinalIntegerMaster implements AutoCloseable {
    private final Instance instance;
    private final List<RouteColumn> columns;
    private final GRBEnv env;
    private final GRBModel model;
    private final List<GRBVar> vars = new ArrayList<>();

    public FinalIntegerMaster(Instance instance, Collection<RouteColumn> columns) throws GRBException {
        this.instance = Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(columns, "columns");
        this.columns = Collections.unmodifiableList(realColumnsOnly(columns));
        this.env = new GRBEnv(true);
        env.set(GRB.IntParam.OutputFlag, 0);
        env.start();
        this.model = new GRBModel(env);
        model.set(GRB.IntParam.OutputFlag, 0);
        model.set(GRB.IntParam.Threads, 1);
        buildModel();
    }

    public IntegerSolution solve() throws GRBException {
        return solve(0.0);
    }

    public IntegerSolution solve(double timeLimitSeconds) throws GRBException {
        if (timeLimitSeconds > 0.0) {
            model.set(GRB.DoubleParam.TimeLimit, timeLimitSeconds);
        }
        model.update();
        model.optimize();
        int status = model.get(GRB.IntAttr.Status);
        List<ColumnValue> solution = new ArrayList<>();
        double objective = Double.POSITIVE_INFINITY;
        if (status == GRB.OPTIMAL || status == GRB.TIME_LIMIT || status == GRB.SUBOPTIMAL) {
            if (model.get(GRB.IntAttr.SolCount) > 0) {
                objective = model.get(GRB.DoubleAttr.ObjVal);
                for (int i = 0; i < vars.size(); i++) {
                    double value = vars.get(i).get(GRB.DoubleAttr.X);
                    if (value > 0.5) {
                        solution.add(new ColumnValue(columns.get(i), value));
                    }
                }
            }
        }
        return new IntegerSolution(status, objective, solution);
    }

    public List<RouteColumn> columns() {
        return columns;
    }

    private void buildModel() throws GRBException {
        int n = instance.nRequests();
        GRBConstr[] coverRows = new GRBConstr[n + 1];
        for (int requestId = 1; requestId <= n; requestId++) {
            coverRows[requestId] = model.addConstr(
                    new GRBLinExpr(),
                    GRB.GREATER_EQUAL,
                    1.0,
                    "cover_" + requestId
            );
        }
        GRBConstr fleetRow = model.addConstr(
                new GRBLinExpr(),
                GRB.LESS_EQUAL,
                instance.maxVehicles(),
                "fleet_limit"
        );
        model.update();

        for (RouteColumn column : columns) {
            validateRealColumn(column);
            GRBColumn grbColumn = new GRBColumn();
            for (int requestId : column.servedRequests()) {
                if (requestId < 1 || requestId > n) {
                    throw new IllegalArgumentException("column request id out of range: " + requestId);
                }
                grbColumn.addTerm(1.0, coverRows[requestId]);
            }
            grbColumn.addTerm(1.0, fleetRow);
            vars.add(model.addVar(
                    0.0,
                    1.0,
                    column.cost(),
                    GRB.BINARY,
                    grbColumn,
                    "x_" + vars.size()
            ));
        }
    }

    private static List<RouteColumn> realColumnsOnly(Collection<RouteColumn> columns) {
        List<RouteColumn> result = new ArrayList<>();
        for (RouteColumn column : columns) {
            Objects.requireNonNull(column, "columns contains null");
            if (column.isRealRoute()) {
                result.add(column);
            }
        }
        return result;
    }

    private static void validateRealColumn(RouteColumn column) {
        if (column.isArtificial()) {
            throw new IllegalArgumentException("final integer master accepts only real route columns");
        }
        if (Math.abs(column.fleetCoefficient() - 1.0) > 1.0e-12) {
            throw new IllegalArgumentException("real route columns must have fleet coefficient 1");
        }
    }

    @Override
    public void close() {
        model.dispose();
        try {
            env.dispose();
        } catch (GRBException ignored) {
            // Nothing useful can be done during cleanup.
        }
    }

    public static final class IntegerSolution {
        private final int status;
        private final double objectiveValue;
        private final List<ColumnValue> selectedColumns;

        private IntegerSolution(int status, double objectiveValue, List<ColumnValue> selectedColumns) {
            this.status = status;
            this.objectiveValue = objectiveValue;
            this.selectedColumns = Collections.unmodifiableList(new ArrayList<>(selectedColumns));
        }

        public int status() {
            return status;
        }

        public boolean hasIncumbent() {
            return !selectedColumns.isEmpty() || Double.isFinite(objectiveValue);
        }

        public boolean isOptimal() {
            return status == GRB.OPTIMAL;
        }

        public double objectiveValue() {
            return objectiveValue;
        }

        public List<ColumnValue> selectedColumns() {
            return selectedColumns;
        }
    }

    public static final class ColumnValue {
        private final RouteColumn column;
        private final double value;

        private ColumnValue(RouteColumn column, double value) {
            this.column = column;
            this.value = value;
        }

        public RouteColumn column() {
            return column;
        }

        public double value() {
            return value;
        }
    }
}
