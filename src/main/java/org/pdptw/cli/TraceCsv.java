package org.pdptw.cli;

import org.pdptw.branch.BranchAndPriceSolver;

import java.util.List;
import java.util.Objects;

public final class TraceCsv {
    public static final String ROOT_CG_HEADER = "traceType,instance,mode,iteration,lpObjective,"
            + "bestReducedCost,addedColumns,columnCount,cutsActive,forwardLabels,backwardLabels,"
            + "dominatedLabels,pricingTimeMs,exactPricing,pricingStatus";
    public static final String BCP_NODE_HEADER = "traceType,instance,nodeId,depth,constraints,activeCutCount,lowerBound,"
            + "incumbentUpperBound,bestReducedCost,pricingCalls,pricedColumns,generatedColumns,"
            + "forwardLabels,backwardLabels,dominatedLabels,pricingTimeMs,pruneReason,branchType";

    private TraceCsv() {
    }

    public static String rootCg(String instance, RootCgResult result) {
        Objects.requireNonNull(result, "result");
        StringBuilder sb = new StringBuilder(ROOT_CG_HEADER);
        for (RootCgResult.Iteration iteration : result.iterations()) {
            sb.append(System.lineSeparator())
                    .append("root-cg")
                    .append(',').append(BenchmarkCsv.csv(instance))
                    .append(',').append(BenchmarkCsv.csv(result.pricingMode()))
                    .append(',').append(iteration.index())
                    .append(',').append(BenchmarkCsv.number(iteration.lpObjectiveValue()))
                    .append(',').append(BenchmarkCsv.number(iteration.bestReducedCost()))
                    .append(',').append(iteration.addedColumns())
                    .append(',').append(iteration.columnCount())
                    .append(',').append(iteration.cutsActive())
                    .append(',').append(iteration.pricingStats().generatedForwardLabels())
                    .append(',').append(iteration.pricingStats().generatedBackwardLabels())
                    .append(',').append(iteration.pricingStats().dominatedLabels())
                    .append(',').append(iteration.pricingTimeMs())
                    .append(',').append(iteration.exactPricing())
                    .append(',').append(BenchmarkCsv.csv(iteration.pricingStatus()));
        }
        return sb.toString();
    }

    public static String bcpNodes(String instance, BranchAndPriceSolver.Result result) {
        Objects.requireNonNull(result, "result");
        StringBuilder sb = new StringBuilder(BCP_NODE_HEADER);
        for (BranchAndPriceSolver.NodeRecord record : result.nodeRecords()) {
            sb.append(System.lineSeparator())
                    .append("bcp-node")
                    .append(',').append(BenchmarkCsv.csv(instance))
                    .append(',').append(BenchmarkCsv.csv(record.nodeId()))
                    .append(',').append(record.depth())
                    .append(',').append(BenchmarkCsv.csv(joinConstraints(record.constraints())))
                    .append(',').append(record.activeCutCount())
                    .append(',').append(BenchmarkCsv.number(record.lowerBound()))
                    .append(',').append(BenchmarkCsv.number(record.incumbentUpperBound()))
                    .append(',').append(BenchmarkCsv.number(record.bestReducedCost()))
                    .append(',').append(record.pricingCalls())
                    .append(',').append(record.pricedColumns())
                    .append(',').append(record.generatedColumns())
                    .append(',').append(integer(record.forwardLabels()))
                    .append(',').append(integer(record.backwardLabels()))
                    .append(',').append(integer(record.dominatedLabels()))
                    .append(',').append(record.pricingTimeMs())
                    .append(',').append(BenchmarkCsv.csv(record.pruneReason()))
                    .append(',').append(BenchmarkCsv.csv(record.branchType()
                            .map(Enum::name)
                            .orElse("")));
        }
        return sb.toString();
    }

    private static String joinConstraints(List<String> constraints) {
        if (constraints.isEmpty()) {
            return "";
        }
        return String.join(";", constraints);
    }

    private static String integer(long value) {
        return value < 0L ? "NA" : Long.toString(value);
    }
}
