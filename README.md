# PDPTW BCP Java/Gurobi Reproduction

本仓库是一个 Java + Gurobi 的 PDPTW branch-cut-and-price 复现原型，目标是复现并验证 Gschwind 等人在 PDPTW 列生成中提出的双向标签思想及其嵌入 BCP 的主要程序链路。

当前仓库适合作为论文算法复现、教学演示和后续实验开发的基础代码。它已经覆盖 tiny correctness、12 个 full-size 代表样本的 root LP / pricing-only 受控实验，从这些样本派生的 12 个 6-request BCP pricing-loop 小实验，以及 `AA30` 子实例上的 BCP 可解规模边界测试。它还不能声明复现论文完整 220 个 full-size 算例表格。

## 复现的论文

复现对象是：

> Timo Gschwind, Stefan Irnich, Ann-Kathrin Rothenbächer, Christian Tilk.  
> Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems.  
> European Journal of Operational Research 266 (2018) 521-530.

论文研究 PDPTW 的 branch-cut-and-price 算法，重点是把 forward labeling 和 backward labeling 通过不同 reduced-cost 矩阵结合起来，使双向标签可以同时使用强 dominance。论文还讨论了 robust / non-robust cuts 与动态 half-way 点的加速策略，并在 220 个 PDPTW benchmark 实例上比较多种 labeling 策略。

论文实验口径包含 6 个组：

| 论文组 | 实例数 | 说明 |
|---|---:|---|
| RC | 40 | Ropke-Cordeau 原始实例 |
| RC+ | 40 | RC 上放宽时间窗并增加容量 |
| LL | 30 | Li-Lim 原始实例 |
| RC_reverse | 40 | RC 反向实例 |
| RC+_reverse | 40 | RC+ 反向实例 |
| LL_reverse | 30 | LL 反向实例 |

论文 Table 2/3 是完整算法性能比较。本仓库目前只做受控复现实验，因此不能把本仓库结果直接对齐为论文 Table 2 或 Table 3 的逐实例复现。

## 当前复现范围

已实现或已打开的主要部件：

| 论文/算法部件 | 本仓库状态 | 主要代码 |
|---|---|---|
| PDPTW 数据模型、路线可行性检查 | 已实现 | `core/Instance.java`, `core/RouteChecker.java` |
| RC / LL benchmark 文本读取 | 已实现 | `io/BenchmarkInstanceReader.java` |
| Restricted master problem | 已实现，Gurobi LP | `master/GurobiRmp.java` |
| route column、artificial column、dual extraction | 已实现 | `master/RouteColumn.java`, `master/ArtificialColumnFactory.java`, `master/DualSolution.java` |
| reduced-cost matrix 与 alpha split | 已实现 | `pricing/ReducedCostMatrices.java` |
| forward labeling | 已实现 | `pricing/ForwardLabeler.java` |
| backward labeling | 已实现 | `pricing/BackwardLabeler.java` |
| bidirectional static labeling | 已实现 | `pricing/BidirectionalPricingSolver.java`, `pricing/BidirectionalStaticPricingSolver.java` |
| bidirectional dynamic labeling | 已实现 | `pricing/BidirectionalDynamicPricingSolver.java`, `pricing/DynamicHalfwayController.java` |
| forward/backward label merge 与 reduced-cost audit | 已实现 | `pricing/BidirectionalMerger.java` |
| DTI/PTI 检查与 robust arc 修复 | 已实现 | `cuts/DtiPtiRepair.java` |
| subset-row cut row、separation、pricing state | 已实现 | `cuts/SubsetRowCutRow.java`, `cuts/SubsetRowCutSeparator.java`, `cuts/SRPricingAdjuster.java` |
| robust cut row 与 tiny robust candidate generation | 部分实现，candidate generation 仍是 tiny-only | `cuts/RobustCutRow.java`, `cuts/RobustCutCandidateGenerator.java` |
| vehicle-count branching | 已实现 | `branch/VehicleCountBrancher.java` |
| set-outflow branching | 已实现，有 benchmark 规模保护参数 | `branch/SetOutflowBrancher.java` |
| branch-node exact labeling pricing | 已实现 | `branch/LabelingNodePricingBackend.java` |
| BCP 主循环 | 已实现受控路径 | `branch/BranchAndPriceSolver.java`, `cli/RunBcp.java` |
| 实验 CLI 与 CSV 输出 | 已实现 | `cli/Main.java`, `cli/BenchmarkCsv.java`, `cli/TraceCsv.java` |

更细的论文算法到代码映射见 [docs/paper_to_code_mapping.md](docs/paper_to_code_mapping.md)。

## 复现效果

本仓库目前有三层代表性实验结果。

### 1. 12 个 full-size 代表样本的 root LP / pricing-only 受控实验

实验文件：

- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md)
- [logs/paper12_results/paper12_root_lp.csv](logs/paper12_results/paper12_root_lp.csv)
- [logs/paper12_results/paper12_finite_cg.csv](logs/paper12_results/paper12_finite_cg.csv)

实验选择 12 个代表测试点：RC、RC+、LL、RC_reverse、RC+_reverse、LL_reverse 每组 2 个。对每个测试点运行 restricted root LP 和三请求有限候选池 finite CG。

关键结果：

| 指标 | 结果 |
|---|---:|
| 测试点数量 | 12 |
| 初始 root LP 发现负 reduced-cost 候选 | 12 / 12 |
| finite-pool CG 清空池内负候选 | 12 / 12 |
| 出现正 artificial column | 0 / 12 |

该实验说明：在这 12 个代表样本上，RMP 求解、dual 提取、reduced-cost 计算、负列加入、有限候选池闭合这一条链路是稳定的。

边界也必须明确：这个实验扫描的是有限候选池，不是 full-size exact pricing，也不是完整 BCP。

### 2. 12 个 6-request 派生子实例的 BCP pricing-loop 小实验

实验文件：

- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md)
- [logs/bcp12_results/bcp12_sr_summary.csv](logs/bcp12_results/bcp12_sr_summary.csv)

实验从同一批论文组代表样本中各抽取 6 个请求，运行：

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

关键结果：

| 指标 | 结果 |
|---|---:|
| BCP pricing-loop 小实验数量 | 12 |
| 状态为 `optimal_benchmark_branch_tree` | 12 / 12 |
| 使用 `pricing=bidir-dynamic` | 12 / 12 |
| forward/backward label counters 非 `NA` | 12 / 12 |
| active SR cuts | 0 / 12 |
| 本批实际展开到子节点 | 0 / 12 |

这批结果的重点不是和论文 Table 3 比时间，而是证明当前程序已经把 bidirectional dynamic labeling 接入了 BCP pricing loop。聚合 CSV 中 `forwardLabels`、`backwardLabels` 均有非 `NA` 计数，`dominatedLabels` 也有非 `NA` 统计，部分实例可以为 0。例如 `AA30_n6` 输出 `forwardLabels=150`、`backwardLabels=162`、`dominatedLabels=53`。

另外，仓库保留了一个 3-request forced branch benchmark sanity check，验证 benchmark-labeling BCP 路径可以进入分支树并处理 vehicle-count branch rows。该 sanity check 使用 forward pricing，只用于验证分支树/branch rows 机制，不作为 bidirectional dynamic 的证据。结果见 [logs/bcp12_results/forced_branch_benchmark_n3_bcp_forward.txt](logs/bcp12_results/forced_branch_benchmark_n3_bcp_forward.txt)。

### 3. AA30 子实例 BCP 可解规模边界

实验文件：

- [logs/bcp_size_boundary/aa30_bcp_size_boundary.csv](logs/bcp_size_boundary/aa30_bcp_size_boundary.csv)

该实验从 `AA30` 抽取不同数量的 request，使用同一条 benchmark-labeling BCP 路径运行 `bidir-dynamic` pricing 和 `sr` cut setting。它的目的不是替代论文 full-size 实验，而是说明当前实现能在多大子问题上证明最优。

| 子实例 | Requests | 顶点数 | 状态 | Nodes | Total ms |
|---|---:|---:|---|---:|---:|
| `AA30_n8` | 8 | 18 | `optimal_benchmark_branch_tree` | 1 | 306 |
| `AA30_n10` | 10 | 22 | `optimal_benchmark_branch_tree` | 1 | 667 |
| `AA30_n12` | 12 | 26 | `optimal_benchmark_branch_tree` | 1 | 1201 |
| `AA30_n15` | 15 | 32 | `optimal_benchmark_branch_tree` | 1 | 143455 |
| `AA30_n18` | 18 | 38 | timeout | - | 304000+ |

结果说明：当前程序确实可以用 exact bidirectional dynamic labeling BCP 在受控 benchmark 子实例上证明最优，但规模增长后耗时上升很快。`AA30_n15` 已经需要约 143 秒，`AA30_n18` 在约 304 秒内没有完成。因此当前 BCP 的稳定可解规模更接近 12-15 requests，而不是论文 full-size RC 的 30 requests。

这组结果比单独的 6-request 小实验更能说明当前程序的能力边界：双向标签 BCP 不是只能跑 tiny toy case，但也还没有达到论文 Table 3 的工程性能。

### 4. full-size BCP 边界

当前不能声明 full-size 30/53 request BCP 已复现。

已有边界证据：

- 12 个 full-size 默认 `bcp` 探测全部被拒绝为 route-universe tiny-only 路径，见 [logs/bcp12_results/bcp12_fullsize_default_probe.csv](logs/bcp12_results/bcp12_fullsize_default_probe.csv)。
- full-size `AA30` exact-labeling BCP probe 在 600 秒内未完成，见 [logs/bcp12_results/fullsize_exact_aa30_probe.txt](logs/bcp12_results/fullsize_exact_aa30_probe.txt)。

因此，本仓库当前最准确的表述是：

> 在 12 个 full-size 代表样本的 root LP / pricing-only 实验、12 个派生 6-request BCP pricing-loop 小实验，以及 `AA30` 的 8-15 request BCP 可解规模边界测试上，较好地复现了论文中双向标签嵌入列生成/BCP 的核心思想和程序流程；完整 220 个 full-size 算例的论文表格级复现尚未完成。

## Quick Start

### 1. 环境要求

- Windows 环境下的批处理脚本。
- Java JDK 17 或更新版本。
- Gurobi，并安装 Java API。
- 设置 `GUROBI_HOME` 到 Gurobi 的 `win64` 目录，例如：

```bat
set GUROBI_HOME=D:\Application_install\gurobi\win64
```

脚本会使用：

```text
%GUROBI_HOME%\lib\gurobi.jar
%GUROBI_HOME%\bin
```

运行时会加入：

```text
-Djava.library.path=%GUROBI_HOME%\bin
```

### 2. 编译

```bat
build.bat
```

### 3. 运行测试

```bat
test.bat
```

测试会编译主程序和 plain Java 测试，并运行 tiny correctness、pricing、cuts、branching、CLI schema 等检查。

### 4. 运行一个 tiny pricing audit

```bat
run.bat pricing-audit --instance ..\references\tiny\tiny-a-wide.json
```

### 5. 运行 root column generation

```bat
run.bat root-cg --instance ..\references\tiny\tiny-a-wide.json --pricing bidir-dynamic --trace
```

### 6. 运行 tiny BCP

```bat
run.bat bcp --instance ..\references\tiny\tiny-a-wide.json --cuts none --branching timo --trace
```

### 7. 运行一个 benchmark 子实例 BCP

先从 full-size benchmark 生成 6-request 子实例：

```bat
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 6 --output logs\bcp12_inputs\AA30_n6
```

然后运行 bidirectional dynamic BCP：

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

## 常用 CLI

| 命令 | 作用 | 当前定位 |
|---|---|---|
| `pricing-audit` | 对 tiny 实例运行 pricing audit | tiny correctness |
| `root-cg` | root column generation | tiny / controlled root CG |
| `bcp` | branch-and-price / benchmark labeling BCP | tiny 和受控 benchmark 子实例 |
| `compare-pricing` | 比较 forward/backward/bidir-static/bidir-dynamic | tiny pricing comparison |
| `benchmark` | 输出 tiny smoke/report CSV | 不是论文 benchmark runner |
| `instance-smoke` | 读取 RC/LL benchmark 并输出 metadata | 解析验证 |
| `root-lp-pricing-smoke` | restricted root LP + finite candidate scan | 受控实验 |
| `root-finite-cg-smoke` | finite candidate pool column generation | 受控实验 |
| `benchmark-subinstance` | 从 RC/LL benchmark 抽取 n-request 子实例 | 受控实验 helper |

## 实验复现入口

更完整的实验命令见 [docs/experiments_quickstart.md](docs/experiments_quickstart.md)。

当前最重要的结果文档：

- [docs/reproduction_scope_and_results.md](docs/reproduction_scope_and_results.md)：公开发布时建议优先阅读的复现范围与结果说明。
- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md)：12 个 full-size 代表样本的 root LP / pricing-only 受控实验。
- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md)：12 个 6-request 派生子实例的 BCP pricing-loop 小实验。
- [logs/bcp_size_boundary/aa30_bcp_size_boundary.csv](logs/bcp_size_boundary/aa30_bcp_size_boundary.csv)：`AA30` 子实例 BCP 可解规模边界结果。
- [docs/paper_to_code_mapping.md](docs/paper_to_code_mapping.md)：论文算法与代码位置的详细映射。
- [docs/bidirectional_labeling_notes.md](docs/bidirectional_labeling_notes.md)：双向标签、DTI/PTI、merge 和动态 half-way 的实现说明。

## 仓库结构

```text
pdptw-bcp-java-gurobi/
  README.md
  build.bat
  test.bat
  run.bat
  src/main/java/org/pdptw/
    core/        PDPTW instance, request, vertex, route, route checker
    io/          tiny JSON and RC/LL benchmark readers
    master/      Gurobi RMP, duals, route columns, final integer master
    pricing/     forward/backward/bidirectional labeling and pricing context
    cuts/        subset-row cuts, robust cuts, DTI/PTI repair
    branch/      branch-and-price tree, branch constraints, node pricing backend
    cli/         public commands and CSV output
    validation/  brute-force tiny oracle and reduced-cost auditors
  src/test/java/org/pdptw/
    ...          plain Java tests
  docs/
    ...          reproduction reports and implementation notes
  logs/
    schema/      stable CSV schema examples
    paper12_results/
    bcp12_results/
    bcp_size_boundary/
```

## 公开使用时的结论口径

建议使用：

- “本仓库复现了 PDPTW 双向标签列生成的核心实现，并在 12 个 full-size 代表样本上验证了 root LP / pricing-only 链路，在 12 个派生 6-request 子实例上验证了 BCP pricing-loop 链路，并在 `AA30` 子实例上观察到当前 BCP 稳定可解规模约为 12-15 requests。”
- “在 12 个 6-request BCP pricing-loop 小实验中，`bidir-dynamic` 确实进入了 BCP pricing loop，forward/backward label counters 可作为证据。”
- “`AA30_n15` 可以证明最优但耗时约 143 秒，`AA30_n18` 在约 304 秒内未完成，说明当前实现和论文 full-size BCP 性能仍有明显差距。”
- “当前结果是受控复现，不是论文完整 220 个 full-size benchmark 表格复现。”

不建议使用：

- “已经完整复现论文全部实验。”
- “已经复现 Table 2 / Table 3。”
- “当前 Java 程序已经能稳定求解全部 full-size RC/LL BCP。”

## Roadmap

后续如果要向论文表格级复现推进，优先事项是：

1. 优化 full-size exact labeling pricing 的性能，降低 `AA30` 这类 30-request 实例的运行时间。
2. 补齐 benchmark-scale robust cut candidate separation。
3. 扩展 SR separation 的候选选择策略，避免 full-size 下无控制地枚举所有三元组。
4. 增强 branching 策略与节点选择策略，记录更完整的 node-level trace。
5. 按论文 220 个实例重新设计长期批实验脚本和结果表。
6. 对 forward、backward、bidirectional static、bidirectional dynamic 进行统一口径的 pricing 策略对比。

## License

当前仓库尚未包含 `LICENSE` 文件。公开到 GitHub 前应先选择许可证，例如 MIT、Apache-2.0 或 GPL 系列。没有许可证时，默认不建议其他人复用代码。

## Citation

如果使用本仓库，请同时引用原论文：

```bibtex
@article{gschwind2018bidirectional,
  title = {Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems},
  author = {Gschwind, Timo and Irnich, Stefan and Rothenb{\"a}cher, Ann-Kathrin and Tilk, Christian},
  journal = {European Journal of Operational Research},
  volume = {266},
  number = {2},
  pages = {521--530},
  year = {2018},
  publisher = {Elsevier}
}
```
