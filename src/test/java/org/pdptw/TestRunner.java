package org.pdptw;

import org.pdptw.branch.BranchingTest;
import org.pdptw.cli.BenchmarkRunnerTest;
import org.pdptw.core.CoreIoTest;
import org.pdptw.cuts.RobustCutsRepairTest;
import org.pdptw.cuts.SubsetRowCutTest;
import org.pdptw.cuts.SubsetRowPricingIntegrationTest;
import org.pdptw.cli.RootColumnGenerationTest;
import org.pdptw.master.GurobiRmpSmokeTest;
import org.pdptw.master.GurobiRmpCutRowsTest;
import org.pdptw.master.MasterValueObjectsTest;
import org.pdptw.pricing.BackwardLabelerTest;
import org.pdptw.pricing.BidirectionalMergeTest;
import org.pdptw.pricing.DynamicHalfwayControllerTest;
import org.pdptw.pricing.ForwardLabelerTest;
import org.pdptw.pricing.PricingAdapterSupportTest;
import org.pdptw.pricing.ReducedCostMatricesTest;
import org.pdptw.validation.BruteForcePricingOraclePlainTest;
import org.pdptw.validation.ReducedCostAuditorPlainTest;

public final class TestRunner {
    private TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.gurobi.gurobi.GRB");
        CoreIoTest.run();
        ReducedCostAuditorPlainTest.main(new String[0]);
        BruteForcePricingOraclePlainTest.main(new String[0]);
        MasterValueObjectsTest.run();
        GurobiRmpSmokeTest.run();
        GurobiRmpCutRowsTest.run();
        ReducedCostMatricesTest.run();
        ForwardLabelerTest.run();
        BackwardLabelerTest.run();
        BidirectionalMergeTest.run();
        DynamicHalfwayControllerTest.run();
        PricingAdapterSupportTest.run();
        RootColumnGenerationTest.run();
        RobustCutsRepairTest.run();
        SubsetRowCutTest.run();
        SubsetRowPricingIntegrationTest.run();
        BranchingTest.run();
        BenchmarkRunnerTest.run();
        System.out.println("[OK] Wave 1 foundation + Wave 2 Node05-10 + Wave 3 Node11-14 tests passed.");
    }
}
