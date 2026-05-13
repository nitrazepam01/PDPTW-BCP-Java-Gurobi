# PDPTW BCP Reproduction Status

Status date: 2026-05-12

This project is a Java/Gurobi reproduction harness for the PDPTW branch-cut-and-price mechanics. It is a Tiny smoke/report only public path and is not a paper-table reproduction.

## Implemented

- Tiny route feasibility, reduced-cost auditing, exact pricing, root column generation, and branch-and-price smoke paths.
- Public command routing and stable CSV contracts for tiny diagnostics.
- Programmatic tiny robust and subset-row cut integration paths.
- Public programmatic active-cut rows reject inherited branch rows, keeping branch constraints separate from cut rows.
- Public programmatic root-CG has generated two-path robust + SR and generated rounded-capacity robust + SR tiny paths.
- Explicit `RunBcp --pricing route-universe-exact|route_universe_exact --trace` keeps route-universe `NA` label counters.
- Public `RunBcp --cuts sr|robust|robust,sr --pricing bidir-dynamic --trace` tiny paths are open only for explicit-labeling non-route-universe pricing; single-cut paths preserve `activeCutCount=1`, the mixed path preserves `activeCutCount=2`, and all expose non-`NA` label counters.

## Not Implemented

- Paper-table benchmark reproduction.
- Benchmark-scale branch-cut-and-price performance reporting.

## Public Commands

- `pricing-audit`: runs exact pricing diagnostics for supported tiny pricing instances.
- `root-cg`: runs no-cut root column generation with `--pricing forward|backward|bidir-static|bidir-dynamic` and optional `--trace`.
- `bcp`: runs the tiny branch-and-price public path. The default backend is route-universe exact pricing; explicit `--pricing` modes use labeling where supported.
- `compare-pricing`: compares all pricing modes on a tiny pricing instance.
- `benchmark`: emits the same Tiny smoke/report CSV contract. It is not a benchmark-table runner.

## Boundary

- CLI --cuts sr|robust|robust,sr open only on tiny explicit-labeling non-route-universe pricing.
- Route-universe cut modes still reject because they cannot carry SR/robust pricing state.
- Programmatic mixed-cut paths remain tiny correctness hooks, not benchmark-scale cut modes.
- Active-cut row APIs reject branch rows explicitly on direct and route-universe overloads; branch rows enter only through branch-tree constraints.
- LL/RC benchmark files are reference/provenance material only.
- Do not report objective, runtime, gap, solved-count, route-count, or paper-table comparisons from this project.

## Stable CSV Contracts

- `logs/schema/benchmark-results.csv`: summary rows from `pricing-audit`, `root-cg`, `bcp`, `compare-pricing`, and `benchmark`.
- `logs/schema/root-cg-trace.csv`: root column generation trace rows.
- `logs/schema/bcp-node-trace.csv`: branch-and-price node trace rows.

The test suite requires these schema/report files to exist so the public reproduction boundary is checked with the code.
