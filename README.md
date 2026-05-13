# PDPTW BCP Java/Gurobi

[English](README.md) | [中文](README.zh-CN.md)

A Java/Gurobi reproduction harness for branch-cut-and-price experiments on the pickup-and-delivery problem with time windows (PDPTW).

This repository focuses on reproducing and validating the main implementation path behind bidirectional labeling for PDPTW column generation. It includes benchmark readers, restricted master problem code, forward/backward/bidirectional labeling, controlled pricing experiments, and a small-scale BCP workflow.

It is an experimental research codebase. It does not yet claim a full reproduction of the paper's complete 220-instance benchmark tables.

## Reference Paper

This project is based on:

> Timo Gschwind, Stefan Irnich, Ann-Kathrin Rothenbaecher, Christian Tilk.  
> Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems.  
> European Journal of Operational Research 266 (2018) 521-530.

The paper studies branch-cut-and-price algorithms for PDPTW and compares several labeling strategies, including forward labeling, backward labeling, static bidirectional labeling, and dynamic bidirectional labeling.

## Current Scope

Implemented or partially implemented components:

- PDPTW data model, requests, vertices, routes, and route feasibility checking.
- RC / LL benchmark text readers.
- Gurobi-based restricted master problem.
- Route columns, artificial columns, LP dual extraction, and reduced-cost evaluation.
- Forward labeling, backward labeling, bidirectional static pricing, and bidirectional dynamic pricing.
- Dynamic half-way control for bidirectional labeling.
- Subset-row cut data structures and controlled integration paths.
- Robust cut scaffolding and DTI/PTI repair utilities.
- Vehicle-count branching and set-outflow branching.
- A controlled branch-and-price loop for tiny and benchmark subinstances.
- CLI commands for smoke tests, root LP pricing probes, finite-pool column generation, and small BCP experiments.

Not yet claimed:

- Full reproduction of Table 2 or Table 3 from the paper.
- Full 220-instance benchmark-scale branch-cut-and-price performance.
- Production-grade PDPTW solver performance.

## Included Benchmark Data

The repository includes the `PDPTW_instances/` directory for reproducible local experiments:

- `PDPTW_instances/RC`
- `PDPTW_instances/LL`

The current reports use these files for representative root LP / pricing-only experiments and derived small BCP subinstances.

## Key Results in This Repository

### 12 representative full-size root LP / pricing-only runs

Report:

- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md)
- [logs/paper12_results/paper12_root_lp.csv](logs/paper12_results/paper12_root_lp.csv)
- [logs/paper12_results/paper12_finite_cg.csv](logs/paper12_results/paper12_finite_cg.csv)

Summary:

| Metric | Result |
|---|---:|
| Representative test points | 12 |
| Initial root LP runs with negative reduced-cost candidates | 12 / 12 |
| Finite-pool CG runs with no remaining negative candidates | 12 / 12 |
| Runs with positive artificial columns | 0 / 12 |

This verifies the controlled pipeline:

```text
benchmark instance -> restricted root LP -> dual extraction -> reduced-cost scan -> finite-pool column generation
```

### 12 derived 6-request BCP pricing-loop runs

Report:

- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md)
- [logs/bcp12_results/bcp12_sr_summary.csv](logs/bcp12_results/bcp12_sr_summary.csv)

These runs demonstrate that bidirectional dynamic labeling is connected to the BCP pricing loop on controlled benchmark subinstances.

### AA30 BCP size-boundary probe

Report:

- [logs/bcp_size_boundary/aa30_bcp_size_boundary.csv](logs/bcp_size_boundary/aa30_bcp_size_boundary.csv)

The current implementation can prove optimality on controlled AA30 subinstances around 8-15 requests, but it does not yet reach paper-level full-size performance.

## Requirements

- Windows command shell for the provided `.bat` scripts.
- Java JDK 17 or newer.
- Gurobi with the Java API installed.
- `GUROBI_HOME` set to the Gurobi `win64` directory.

Example:

```bat
set GUROBI_HOME=D:\Application_install\gurobi\win64
```

The scripts use:

```text
%GUROBI_HOME%\lib\gurobi.jar
%GUROBI_HOME%\bin
```

## Quick Start

Build:

```bat
build.bat
```

Run tests:

```bat
test.bat
```

Parse a benchmark instance:

```bat
run.bat instance-smoke --instance PDPTW_instances\RC\AA30
```

Run a controlled root LP / pricing probe:

```bat
run.bat root-lp-pricing-smoke --instance PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
```

Run finite-pool column generation:

```bat
run.bat root-finite-cg-smoke --instance PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
```

Create a 6-request benchmark subinstance:

```bat
run.bat benchmark-subinstance --instance PDPTW_instances\RC\AA30 --requests 6 --output logs\bcp12_inputs\AA30_n6
```

Run a controlled BCP pricing-loop experiment:

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

## CLI Overview

| Command | Purpose | Current role |
|---|---|---|
| `pricing-audit` | Reduced-cost audit on tiny instances | Correctness check |
| `root-cg` | Root column generation | Tiny / controlled root CG |
| `bcp` | Branch-and-price workflow | Tiny and controlled benchmark subinstances |
| `compare-pricing` | Compare pricing strategies | Tiny pricing comparison |
| `benchmark` | Emit tiny report CSV | Not a paper benchmark runner |
| `instance-smoke` | Parse RC/LL benchmark files | Benchmark reader validation |
| `root-lp-pricing-smoke` | Restricted root LP + finite candidate scan | Controlled experiment |
| `root-finite-cg-smoke` | Finite-pool column generation | Controlled experiment |
| `benchmark-subinstance` | Extract an n-request benchmark subinstance | Experiment helper |

## Documentation

- [docs/reproduction_scope_and_results.md](docs/reproduction_scope_and_results.md)
- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md)
- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md)
- [docs/paper_to_code_mapping.md](docs/paper_to_code_mapping.md)
- [docs/bidirectional_labeling_notes.md](docs/bidirectional_labeling_notes.md)
- [docs/experiments_quickstart.md](docs/experiments_quickstart.md)

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
  src/main/java/org/pdptw/
    core/        instance, request, vertex, route, feasibility checks
    io/          benchmark readers
    master/      Gurobi RMP, duals, route columns
    pricing/     forward/backward/bidirectional labeling
    cuts/        subset-row cuts, robust cuts, DTI/PTI repair
    branch/      branch-and-price tree and branching constraints
    cli/         public commands and CSV output
    validation/  brute-force tiny oracle and reduced-cost checks
  src/test/java/org/pdptw/
  docs/
  logs/
```

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

## License

This repository is released under the MIT License. See [LICENSE](LICENSE).
