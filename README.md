# PDPTW BCP Java/Gurobi Reproduction

[English](README.md) | [中文](README.zh-CN.md)

This repository is a Java + Gurobi reproduction prototype for branch-cut-and-price algorithms for the pickup-and-delivery problem with time windows (PDPTW). Its goal is to reproduce and validate the main algorithmic pipeline behind the bidirectional labeling ideas studied by Gschwind et al.

The current codebase is useful as a research reproduction scaffold, teaching example, and basis for further experiments. It currently covers tiny correctness tests, 12 representative full-size root LP / pricing-only controlled experiments, 12 derived 6-request BCP pricing-loop experiments, and an `AA30` subinstance size-boundary test for the current BCP implementation.

It does **not** yet claim to reproduce the complete 220-instance full-size benchmark tables from the paper.

## Reference Paper

This repository targets the following paper:

> Timo Gschwind, Stefan Irnich, Ann-Kathrin Rothenbaecher, Christian Tilk.  
> Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems.  
> European Journal of Operational Research 266 (2018) 521-530.

The paper studies branch-cut-and-price algorithms for PDPTW. Its main focus is how to combine forward labeling and backward labeling with different reduced-cost matrices so that bidirectional labeling can still use strong dominance. The paper also discusses robust / non-robust cuts and dynamic half-way points, and evaluates several labeling strategies on 220 PDPTW benchmark instances.

The paper's benchmark setting contains six groups:

| Paper group | # instances | Description |
|---|---:|---|
| RC | 40 | Original Ropke-Cordeau instances |
| RC+ | 40 | RC with relaxed time windows and increased capacity |
| LL | 30 | Original Li-Lim instances |
| RC_reverse | 40 | Reversed RC instances |
| RC+_reverse | 40 | Reversed RC+ instances |
| LL_reverse | 30 | Reversed LL instances |

The paper's Table 2 and Table 3 are complete-algorithm performance comparisons. The results in this repository are controlled reproduction experiments, so they should not be interpreted as per-instance reproductions of those paper tables.

## Current Reproduction Scope

Implemented or opened components:

| Paper / algorithm component | Repository status | Main code |
|---|---|---|
| PDPTW data model and route feasibility checking | Implemented | `core/Instance.java`, `core/RouteChecker.java` |
| RC / LL benchmark text readers | Implemented | `io/BenchmarkInstanceReader.java` |
| Restricted master problem | Implemented with Gurobi LP | `master/GurobiRmp.java` |
| Route columns, artificial columns, dual extraction | Implemented | `master/RouteColumn.java`, `master/ArtificialColumnFactory.java`, `master/DualSolution.java` |
| Reduced-cost matrix and alpha split | Implemented | `pricing/ReducedCostMatrices.java` |
| Forward labeling | Implemented | `pricing/ForwardLabeler.java` |
| Backward labeling | Implemented | `pricing/BackwardLabeler.java` |
| Static bidirectional labeling | Implemented | `pricing/BidirectionalPricingSolver.java`, `pricing/BidirectionalStaticPricingSolver.java` |
| Dynamic bidirectional labeling | Implemented | `pricing/BidirectionalDynamicPricingSolver.java`, `pricing/DynamicHalfwayController.java` |
| Forward/backward merge and reduced-cost audit | Implemented | `pricing/BidirectionalMerger.java` |
| DTI/PTI checking and robust arc repair | Implemented | `cuts/DtiPtiRepair.java` |
| Subset-row cut rows, separation, pricing state | Implemented | `cuts/SubsetRowCutRow.java`, `cuts/SubsetRowCutSeparator.java`, `cuts/SRPricingAdjuster.java` |
| Robust cut rows and tiny robust candidates | Partially implemented; candidate generation is still tiny-only | `cuts/RobustCutRow.java`, `cuts/RobustCutCandidateGenerator.java` |
| Vehicle-count branching | Implemented | `branch/VehicleCountBrancher.java` |
| Set-outflow branching | Implemented with benchmark-scale guards | `branch/SetOutflowBrancher.java` |
| Branch-node exact labeling pricing | Implemented | `branch/LabelingNodePricingBackend.java` |
| BCP main loop | Implemented on controlled paths | `branch/BranchAndPriceSolver.java`, `cli/RunBcp.java` |
| Experiment CLI and CSV output | Implemented | `cli/Main.java`, `cli/BenchmarkCsv.java`, `cli/TraceCsv.java` |

For a more detailed paper-to-code mapping, see [docs/paper_to_code_mapping.md](docs/paper_to_code_mapping.md).

## Reproduction Results

This repository currently contains three layers of representative experiments.

### 1. Root LP / pricing-only experiment on 12 full-size representative samples

Files:

- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md)
- [logs/paper12_results/paper12_root_lp.csv](logs/paper12_results/paper12_root_lp.csv)
- [logs/paper12_results/paper12_finite_cg.csv](logs/paper12_results/paper12_finite_cg.csv)

The experiment selects 12 representative test points: two from each of RC, RC+, LL, RC_reverse, RC+_reverse, and LL_reverse. Each test point is run with a restricted root LP and a three-request finite candidate-pool CG experiment.

Key results:

| Metric | Result |
|---|---:|
| Test points | 12 |
| Initial root LP runs with negative reduced-cost candidates | 12 / 12 |
| Finite-pool CG runs with no remaining negative candidates | 12 / 12 |
| Runs with positive artificial columns | 0 / 12 |

This experiment shows that the following pipeline is stable on the 12 representative samples:

```text
RMP solve -> dual extraction -> reduced-cost scan -> negative-column insertion -> finite candidate-pool closure
```

Important boundary: this experiment scans a finite candidate pool. It is not full-size exact pricing and not complete BCP.

### 2. BCP pricing-loop experiment on 12 derived 6-request subinstances

Files:

- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md)
- [logs/bcp12_results/bcp12_sr_summary.csv](logs/bcp12_results/bcp12_sr_summary.csv)

Each test instance is derived from the same paper-group representatives by extracting six requests. The experiment runs:

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

Key results:

| Metric | Result |
|---|---:|
| BCP pricing-loop experiments | 12 |
| Status `optimal_benchmark_branch_tree` | 12 / 12 |
| Runs using `pricing=bidir-dynamic` | 12 / 12 |
| Forward/backward label counters not `NA` | 12 / 12 |
| Active SR cuts | 0 / 12 |
| Runs that expanded child nodes in this batch | 0 / 12 |

The point of this batch is not to compare runtime with Table 3. It verifies that bidirectional dynamic labeling is actually connected to the BCP pricing loop. The aggregate CSV contains non-`NA` `forwardLabels`, `backwardLabels`, and `dominatedLabels` counters. For example, `AA30_n6` reports `forwardLabels=150`, `backwardLabels=162`, and `dominatedLabels=53`.

The repository also keeps a 3-request forced-branch benchmark sanity check. It verifies that the benchmark-labeling BCP path can enter the branch tree and process vehicle-count branch rows. This sanity check uses forward pricing and is only evidence for the branching / branch-row mechanism, not evidence for bidirectional dynamic pricing. See [logs/bcp12_results/forced_branch_benchmark_n3_bcp_forward.txt](logs/bcp12_results/forced_branch_benchmark_n3_bcp_forward.txt).

### 3. BCP size-boundary probe on AA30 subinstances

File:

- [logs/bcp_size_boundary/aa30_bcp_size_boundary.csv](logs/bcp_size_boundary/aa30_bcp_size_boundary.csv)

This experiment extracts different numbers of requests from `AA30` and runs the same benchmark-labeling BCP path with `bidir-dynamic` pricing and `sr` cut settings. Its purpose is to show the current implementation's scale boundary, not to replace the paper's full-size experiment.

| Subinstance | Requests | Vertices | Status | Nodes | Total ms |
|---|---:|---:|---|---:|---:|
| `AA30_n8` | 8 | 18 | `optimal_benchmark_branch_tree` | 1 | 306 |
| `AA30_n10` | 10 | 22 | `optimal_benchmark_branch_tree` | 1 | 667 |
| `AA30_n12` | 12 | 26 | `optimal_benchmark_branch_tree` | 1 | 1201 |
| `AA30_n15` | 15 | 32 | `optimal_benchmark_branch_tree` | 1 | 143455 |
| `AA30_n18` | 18 | 38 | timeout | - | 304000+ |

The result shows that the current exact bidirectional dynamic labeling BCP path can prove optimality on controlled benchmark subinstances, but runtime grows quickly. `AA30_n15` already takes about 143 seconds, and `AA30_n18` does not finish in about 304 seconds. The current stable BCP scale is therefore closer to 12-15 requests than to the 30-request full-size RC instances in the paper.

This is stronger evidence than a tiny toy example, but it is still not paper-level full-size BCP performance.

### 4. Full-size BCP boundary

The repository currently does not claim full-size 30/53-request BCP reproduction.

Existing boundary evidence:

- 12 full-size default `bcp` probes are rejected by the route-universe tiny-only guard. See [logs/bcp12_results/bcp12_fullsize_default_probe.csv](logs/bcp12_results/bcp12_fullsize_default_probe.csv).
- A full-size `AA30` exact-labeling BCP probe does not finish within 600 seconds. See [logs/bcp12_results/fullsize_exact_aa30_probe.txt](logs/bcp12_results/fullsize_exact_aa30_probe.txt).

The most accurate current statement is:

> This repository reproduces the core bidirectional-labeling column-generation workflow on controlled experiments: 12 full-size representative root LP / pricing-only samples, 12 derived 6-request BCP pricing-loop samples, and AA30 subinstance BCP size-boundary tests up to roughly 12-15 requests. Full 220-instance paper-table reproduction is not yet complete.

## Quick Start

### 1. Requirements

- Windows environment for the provided batch scripts.
- Java JDK 17 or newer.
- Gurobi with the Java API installed.
- `GUROBI_HOME` set to the Gurobi `win64` directory, for example:

```bat
set GUROBI_HOME=D:\Application_install\gurobi\win64
```

The scripts use:

```text
%GUROBI_HOME%\lib\gurobi.jar
%GUROBI_HOME%\bin
```

At runtime, the scripts pass:

```text
-Djava.library.path=%GUROBI_HOME%\bin
```

### 2. Build

```bat
build.bat
```

### 3. Run tests

```bat
test.bat
```

The test suite compiles the main program and plain Java tests, then runs tiny correctness checks, pricing checks, cut checks, branching checks, and CLI schema checks.

Note: the historical tiny tests use the original workspace path `..\references\tiny`. If you clone only this repository and do not have that folder, benchmark smoke commands still run, but the full local `test.bat` workflow expects those tiny fixtures.

### 4. Parse a benchmark instance

```bat
run.bat instance-smoke --instance PDPTW_instances\RC\AA30
```

### 5. Run a controlled root LP / pricing probe

```bat
run.bat root-lp-pricing-smoke --instance PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
```

### 6. Run finite-pool column generation

```bat
run.bat root-finite-cg-smoke --instance PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
```

### 7. Run a benchmark subinstance BCP experiment

First extract a 6-request subinstance:

```bat
run.bat benchmark-subinstance --instance PDPTW_instances\RC\AA30 --requests 6 --output logs\bcp12_inputs\AA30_n6
```

Then run bidirectional dynamic BCP:

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

## CLI Overview

| Command | Purpose | Current role |
|---|---|---|
| `pricing-audit` | Run a pricing audit on tiny instances | Tiny correctness |
| `root-cg` | Root column generation | Tiny / controlled root CG |
| `bcp` | Branch-and-price / benchmark labeling BCP | Tiny and controlled benchmark subinstances |
| `compare-pricing` | Compare forward/backward/static/dynamic pricing | Tiny pricing comparison |
| `benchmark` | Emit tiny smoke/report CSV | Not a paper benchmark runner |
| `instance-smoke` | Read RC/LL benchmark files and print metadata | Parser validation |
| `root-lp-pricing-smoke` | Restricted root LP + finite candidate scan | Controlled experiment |
| `root-finite-cg-smoke` | Finite candidate-pool column generation | Controlled experiment |
| `benchmark-subinstance` | Extract an n-request subinstance from RC/LL benchmark data | Controlled experiment helper |

## Experiment Entry Points

More complete experiment commands are collected in [docs/experiments_quickstart.md](docs/experiments_quickstart.md).

Important result documents:

- [docs/reproduction_scope_and_results.md](docs/reproduction_scope_and_results.md): recommended starting point for reproduction scope and result interpretation.
- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md): root LP / pricing-only experiment on 12 full-size representatives.
- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md): BCP pricing-loop experiment on 12 derived 6-request subinstances.
- [logs/bcp_size_boundary/aa30_bcp_size_boundary.csv](logs/bcp_size_boundary/aa30_bcp_size_boundary.csv): AA30 BCP size-boundary results.
- [docs/paper_to_code_mapping.md](docs/paper_to_code_mapping.md): detailed mapping from paper concepts to code locations.
- [docs/bidirectional_labeling_notes.md](docs/bidirectional_labeling_notes.md): implementation notes on bidirectional labeling, DTI/PTI, merge, and dynamic half-way control.

## Repository Layout

```text
pdptw-bcp-java-gurobi/
  README.md
  README.zh-CN.md
  LICENSE
  build.bat
  test.bat
  run.bat
  PDPTW_instances/
    RC/
    LL/
  src/main/java/org/pdptw/
    core/        PDPTW instance, request, vertex, route, route checker
    io/          benchmark readers
    master/      Gurobi RMP, duals, route columns, final integer master
    pricing/     forward/backward/bidirectional labeling and pricing context
    cuts/        subset-row cuts, robust cuts, DTI/PTI repair
    branch/      branch-and-price tree, branch constraints, node pricing backend
    cli/         public commands and CSV output
    validation/  brute-force tiny oracle and reduced-cost auditors
  src/test/java/org/pdptw/
  docs/
  logs/
    schema/
    paper12_results/
    bcp12_results/
    bcp_size_boundary/
```

## Recommended Public Claims

Recommended:

- "This repository reproduces the core PDPTW bidirectional-labeling column-generation implementation and validates the root LP / pricing-only pipeline on 12 representative full-size samples."
- "It validates the BCP pricing-loop path on 12 derived 6-request benchmark subinstances."
- "On AA30 subinstances, the current BCP implementation can prove optimality around 8-15 requests; `AA30_n15` takes about 143 seconds and `AA30_n18` does not finish within about 304 seconds."
- "The current results are controlled reproduction experiments, not complete 220-instance paper-table reproduction."

Not recommended:

- "This repository fully reproduces all paper experiments."
- "This repository reproduces Table 2 / Table 3."
- "The current Java implementation can reliably solve all full-size RC/LL BCP instances."

## Roadmap

To move toward paper-table-level reproduction, the most important next steps are:

1. Improve full-size exact labeling pricing performance for 30-request instances such as `AA30`.
2. Complete benchmark-scale robust cut candidate separation.
3. Improve subset-row cut candidate selection so full-size runs do not enumerate uncontrolled triples.
4. Strengthen branching and node-selection strategies, with richer node-level traces.
5. Design long-running batch scripts and result tables for the paper's 220 instances.
6. Compare forward, backward, bidirectional static, and bidirectional dynamic pricing under a common experimental protocol.

## License

This repository is released under the MIT License. See [LICENSE](LICENSE).

## Citation

If you use this repository, please cite the original paper:

```bibtex
@article{gschwind2018bidirectional,
  title = {Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems},
  author = {Gschwind, Timo and Irnich, Stefan and Rothenbacher, Ann-Kathrin and Tilk, Christian},
  journal = {European Journal of Operational Research},
  volume = {266},
  number = {2},
  pages = {521--530},
  year = {2018},
  publisher = {Elsevier}
}
```
