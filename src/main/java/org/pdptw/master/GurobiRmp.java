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
import org.pdptw.core.Route;
import org.pdptw.cuts.MasterCutRow;
import org.pdptw.cuts.RobustCut;
import org.pdptw.cuts.RobustCutRow;
import org.pdptw.cuts.SubsetRowCut;
import org.pdptw.cuts.SubsetRowCutRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class GurobiRmp implements AutoCloseable {
    private final Instance instance;
    private final GRBEnv env;
    private final GRBModel model;
    private final GRBConstr[] coverRows;
    private final GRBConstr fleetRow;
    private final ColumnPool pool = new ColumnPool();
    private final List<GRBVar> vars = new ArrayList<>();
    private final List<MasterCutRow> cutRows = new ArrayList<>();
    private final List<GRBConstr> cutConstrs = new ArrayList<>();
    private boolean lpSolved;
    private double objectiveValue = Double.NaN;

    public GurobiRmp(Instance instance) throws GRBException {
        this(instance, ArtificialColumnFactory.DEFAULT_PENALTY);
    }

    public GurobiRmp(Instance instance, double artificialPenalty) throws GRBException {
        this.instance = Objects.requireNonNull(instance, "instance");
        validateInstance(instance);
        this.env = new GRBEnv(true);
        env.set(GRB.IntParam.OutputFlag, 0);
        env.start();
        this.model = new GRBModel(env);
        model.set(GRB.IntParam.OutputFlag, 0);
        model.set(GRB.IntParam.Threads, 1);
        model.set(GRB.IntParam.Presolve, 0);

        int n = instance.nRequests();
        this.coverRows = new GRBConstr[n + 1];
        for (int requestId = 1; requestId <= n; requestId++) {
            coverRows[requestId] = model.addConstr(
                    new GRBLinExpr(),
                    GRB.GREATER_EQUAL,
                    1.0,
                    "cover_" + requestId
            );
        }
        this.fleetRow = model.addConstr(
                new GRBLinExpr(),
                GRB.LESS_EQUAL,
                instance.maxVehicles(),
                "fleet_limit"
        );
        model.update();

        addColumnsInternal(new ArtificialColumnFactory(artificialPenalty).forInstance(instance));
        model.update();
    }

    public boolean addRoute(Route route) throws GRBException {
        Objects.requireNonNull(route, "route");
        return addColumn(RouteColumn.fromRoute("route_" + pool.size(), route, instance));
    }

    public int addRoutes(Collection<Route> routes) throws GRBException {
        Objects.requireNonNull(routes, "routes");
        int added = 0;
        for (Route route : routes) {
            if (addRoute(route)) {
                added++;
            }
        }
        return added;
    }

    public boolean addColumn(RouteColumn column) throws GRBException {
        Objects.requireNonNull(column, "column");
        boolean added = addColumnInternal(column);
        if (added) {
            model.update();
        }
        return added;
    }

    public void addCutRow(MasterCutRow cutRow) throws GRBException {
        Objects.requireNonNull(cutRow, "cutRow");
        if (cutRow instanceof RobustCutRow robustCutRow) {
            robustCutRow.validateForInstance(instance);
        }
        if (cutRow instanceof SubsetRowCutRow subsetRowCutRow) {
            subsetRowCutRow.validateForInstance(instance);
        }
        ensureUniqueCutName(cutRow.name());
        GRBLinExpr expression = new GRBLinExpr();
        List<RouteColumn> columns = pool.columns();
        for (int index = 0; index < columns.size(); index++) {
            double coefficient = cutRow.coefficient(instance, columns.get(index));
            if (Math.abs(coefficient) > 1.0e-12) {
                expression.addTerm(coefficient, vars.get(index));
            }
        }
        GRBConstr constraint = model.addConstr(
                expression,
                grbSense(cutRow.sense()),
                cutRow.rhs(),
                cutRow.name());
        cutRows.add(cutRow);
        cutConstrs.add(constraint);
        model.update();
        lpSolved = false;
        objectiveValue = Double.NaN;
    }

    public SolveResult solveLp() throws GRBException {
        model.update();
        model.optimize();
        int status = model.get(GRB.IntAttr.Status);
        lpSolved = status == GRB.OPTIMAL;
        objectiveValue = lpSolved ? model.get(GRB.DoubleAttr.ObjVal) : Double.NaN;
        return new SolveResult(status, objectiveValue);
    }

    public DualSolution dualSolution() throws GRBException {
        requireSolvedLp();
        double[] requestDuals = new double[instance.nRequests() + 1];
        for (int requestId = 1; requestId <= instance.nRequests(); requestId++) {
            requestDuals[requestId] = coverRows[requestId].get(GRB.DoubleAttr.Pi);
        }
        double fleetDual = fleetRow.get(GRB.DoubleAttr.Pi);
        return DualSolution.ofOneIndexed(requestDuals, fleetDual, robustCutsFromDuals(), subsetRowCutsFromDuals());
    }

    public List<CutDual> cutDuals() throws GRBException {
        requireSolvedLp();
        ArrayList<CutDual> duals = new ArrayList<CutDual>();
        for (int index = 0; index < cutRows.size(); index++) {
            MasterCutRow row = cutRows.get(index);
            double rawPi = cutConstrs.get(index).get(GRB.DoubleAttr.Pi);
            duals.add(new CutDual(row, rawPi, row.pricingDualFromRawPi(rawPi)));
        }
        return Collections.unmodifiableList(duals);
    }

    public List<RobustCut> robustCutsFromDuals() throws GRBException {
        requireSolvedLp();
        ArrayList<RobustCut> cuts = new ArrayList<RobustCut>();
        for (int index = 0; index < cutRows.size(); index++) {
            MasterCutRow row = cutRows.get(index);
            if (row instanceof RobustCutRow) {
                RobustCutRow robustRow = (RobustCutRow) row;
                cuts.add(robustRow.toPricingCut(cutConstrs.get(index).get(GRB.DoubleAttr.Pi)));
            }
        }
        return Collections.unmodifiableList(cuts);
    }

    public List<SubsetRowCut> subsetRowCutsFromDuals() throws GRBException {
        requireSolvedLp();
        ArrayList<SubsetRowCut> cuts = new ArrayList<SubsetRowCut>();
        for (int index = 0; index < cutRows.size(); index++) {
            MasterCutRow row = cutRows.get(index);
            if (row instanceof SubsetRowCutRow) {
                SubsetRowCutRow subsetRow = (SubsetRowCutRow) row;
                cuts.add(subsetRow.toPricingCut(cutConstrs.get(index).get(GRB.DoubleAttr.Pi)));
            }
        }
        return Collections.unmodifiableList(cuts);
    }

    public double objectiveValue() {
        requireSolvedLp();
        return objectiveValue;
    }

    public List<ColumnValue> solutionValues() throws GRBException {
        requireSolvedLp();
        List<RouteColumn> columns = pool.columns();
        List<ColumnValue> values = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            double x = vars.get(i).get(GRB.DoubleAttr.X);
            if (x > 1.0e-8) {
                values.add(new ColumnValue(columns.get(i), x));
            }
        }
        return Collections.unmodifiableList(values);
    }

    public boolean hasPositiveArtificial() throws GRBException {
        requireSolvedLp();
        List<RouteColumn> columns = pool.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).isArtificial() && vars.get(i).get(GRB.DoubleAttr.X) > 1.0e-7) {
                return true;
            }
        }
        return false;
    }

    public List<RouteColumn> columns() {
        return pool.columns();
    }

    public List<RouteColumn> realColumns() {
        return pool.realColumns();
    }

    public List<RouteColumn> artificialColumns() {
        return pool.artificialColumns();
    }

    public List<MasterCutRow> cutRows() {
        return Collections.unmodifiableList(new ArrayList<MasterCutRow>(cutRows));
    }

    public int columnCount() {
        return pool.size();
    }

    public Instance instance() {
        return instance;
    }

    private void addColumnsInternal(Collection<RouteColumn> columns) throws GRBException {
        for (RouteColumn column : columns) {
            addColumnInternal(column);
        }
    }

    private boolean addColumnInternal(RouteColumn column) throws GRBException {
        validateColumn(column);
        if (!pool.add(column)) {
            return false;
        }
        GRBColumn grbColumn = new GRBColumn();
        for (int requestId : column.servedRequests()) {
            grbColumn.addTerm(1.0, coverRows[requestId]);
        }
        if (Math.abs(column.fleetCoefficient()) > 1.0e-12) {
            grbColumn.addTerm(column.fleetCoefficient(), fleetRow);
        }
        for (int index = 0; index < cutRows.size(); index++) {
            double coefficient = cutRows.get(index).coefficient(instance, column);
            if (Math.abs(coefficient) > 1.0e-12) {
                grbColumn.addTerm(coefficient, cutConstrs.get(index));
            }
        }
        GRBVar var = model.addVar(
                0.0,
                GRB.INFINITY,
                column.cost(),
                GRB.CONTINUOUS,
                grbColumn,
                column.name()
        );
        vars.add(var);
        lpSolved = false;
        objectiveValue = Double.NaN;
        return true;
    }

    private void ensureUniqueCutName(String name) {
        for (MasterCutRow row : cutRows) {
            if (row.name().equals(name)) {
                throw new IllegalArgumentException("duplicate cut row name: " + name);
            }
        }
    }

    private static char grbSense(MasterCutRow.Sense sense) {
        Objects.requireNonNull(sense, "sense");
        return switch (sense) {
            case LESS_EQUAL -> GRB.LESS_EQUAL;
            case GREATER_EQUAL -> GRB.GREATER_EQUAL;
            case EQUAL -> GRB.EQUAL;
        };
    }

    private void validateColumn(RouteColumn column) {
        for (int requestId : column.servedRequests()) {
            if (requestId < 1 || requestId > instance.nRequests()) {
                throw new IllegalArgumentException("column request id out of range: " + requestId);
            }
        }
        if (column.isArtificial() && Math.abs(column.fleetCoefficient()) > 1.0e-12) {
            throw new IllegalArgumentException("artificial columns must have fleet coefficient 0");
        }
        if (column.isRealRoute() && Math.abs(column.fleetCoefficient() - 1.0) > 1.0e-12) {
            throw new IllegalArgumentException("real route columns must have fleet coefficient 1");
        }
    }

    private void requireSolvedLp() {
        if (!lpSolved) {
            throw new IllegalStateException("LP relaxation duals are available only after an optimal solveLp()");
        }
    }

    private static void validateInstance(Instance instance) {
        if (instance.nRequests() < 0) {
            throw new IllegalArgumentException("nRequests must be non-negative: " + instance.nRequests());
        }
        if (instance.maxVehicles() < 0) {
            throw new IllegalArgumentException("maxVehicles must be non-negative: " + instance.maxVehicles());
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

    public static final class SolveResult {
        private final int status;
        private final double objectiveValue;

        private SolveResult(int status, double objectiveValue) {
            this.status = status;
            this.objectiveValue = objectiveValue;
        }

        public int status() {
            return status;
        }

        public boolean isOptimal() {
            return status == GRB.OPTIMAL;
        }

        public double objectiveValue() {
            return objectiveValue;
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

    public static final class CutDual {
        private final MasterCutRow row;
        private final double rawPi;
        private final double pricingDual;

        private CutDual(MasterCutRow row, double rawPi, double pricingDual) {
            this.row = Objects.requireNonNull(row, "row");
            this.rawPi = requireFinite(rawPi, "rawPi");
            this.pricingDual = requireFinite(pricingDual, "pricingDual");
        }

        public MasterCutRow row() {
            return row;
        }

        public double rawPi() {
            return rawPi;
        }

        public double pricingDual() {
            return pricingDual;
        }
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
