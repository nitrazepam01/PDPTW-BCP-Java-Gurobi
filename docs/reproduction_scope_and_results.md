# 复现范围与实验结果

日期：2026-05-13

本文档用于公开发布时说明本仓库“复现到了什么程度”。它是 README 的结果依据，也是避免误把受控实验表述成论文全表复现的边界文件。

## 1. 一句话结论

本仓库已经较好复现了 PDPTW 双向标签列生成的核心实现，并在 12 个 full-size 代表样本上验证了 root LP / pricing-only 链路，在从这些样本派生的 12 个 6-request 子实例上验证了 BCP pricing-loop 链路。补充的 `AA30` 子实例规模边界实验显示，当前 exact bidirectional dynamic BCP 能在 8-15 request 子实例上证明最优，但 18 request 已经在本次 5 分钟级设置下超时。当前结果可以说明双向标签已经实际进入 BCP pricing loop，但不能声明已经复现论文 220 个 full-size 算例表格。

推荐公开表述：

> 在 12 个 full-size 代表样本的 root LP / pricing-only 实验、12 个派生 6-request BCP pricing-loop 小实验，以及 `AA30` 的 8-15 request BCP 可解规模边界测试上，较好地复现了论文中双向标签嵌入列生成/BCP 的核心思想和程序流程；完整 220 个 full-size 算例的论文表格级复现尚未完成。

## 2. 论文结果和本仓库结果的关系

论文 `Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems` 的实验目标是比较多种 labeling 策略在完整 branch-cut-and-price 中的表现。论文使用 220 个 PDPTW 实例：

| 论文组 | 实例数 |
|---|---:|
| RC | 40 |
| RC+ | 40 |
| LL | 30 |
| RC_reverse | 40 |
| RC+_reverse | 40 |
| LL_reverse | 30 |
| 合计 | 220 |

论文 Table 2/3 的完整口径包括 root LP、branch-and-bound 全树、cut separation、heuristic/exact pricing、branching 和整数最优性证明。

本仓库当前实验口径分为两层：

| 层级 | 实验对象 | 能支持的结论 | 不能支持的结论 |
|---|---|---|---|
| Root LP / pricing-only 受控实验 | 12 个 full-size 代表样本 | RMP-dual-pricing-列加入链路在有限候选池内闭合 | exact pricing 完整无负列证明 |
| BCP pricing-loop 小实验 | 12 个 6-request 派生子实例 | BCP 主循环 + exact bidirectional dynamic labeling pricing 可运行 | full-size 30/53 request BCP 性能或复杂分支树性能 |
| BCP 可解规模边界实验 | `AA30` 的 8/10/12/15/18 request 子实例 | 当前 exact bidirectional BCP 的可解规模和耗时增长趋势 | 其他论文组或 full-size 30 request 实例的可解性 |
| Full-size 探测 | 12 个 full-size 默认 BCP probe 和 AA30 exact-labeling probe | 当前 full-size BCP 仍不成熟 | 论文 Table 3 级复现 |

## 3. 结果层级

| Tier | 证据文件 | 当前结论 |
|---|---|---|
| Tiny correctness | `test.bat`, `logs/schema/*.csv`, tiny fixtures | core feasibility、reduced cost、pricing、cuts、branching、CLI schema 基础路径可测 |
| 12-instance root-pricing | `logs/paper12_results/paper12_root_lp.csv`, `logs/paper12_results/paper12_finite_cg.csv` | 12 个 full-size 代表样本在有限候选池内完成 root LP / finite CG 闭环 |
| 12-instance BCP pricing-loop small | `logs/bcp12_results/bcp12_sr_summary.csv` | 12 个 6-request 派生子实例用 `bidir-dynamic` 完成 BCP pricing-loop 小实验 |
| AA30 BCP size boundary | `logs/bcp_size_boundary/aa30_bcp_size_boundary.csv` | `AA30_n8` 到 `AA30_n15` 可证明最优，`AA30_n18` 在约 304 秒内未完成 |
| Full-size BCP boundary | `logs/bcp12_results/bcp12_fullsize_default_probe.csv`, `logs/bcp12_results/fullsize_exact_aa30_probe.txt` | full-size BCP 尚未达到论文表格复现能力 |

## 4. Root LP / pricing-only 受控实验

详细文档见 [paper12_root_pricing_experiment.md](paper12_root_pricing_experiment.md)。

### 4.1 实验对象

按论文 6 个组每组 2 个测试点，共 12 个：

| 论文组 | 测试点 |
|---|---|
| RC | `AA30`, `BB30` |
| RC+ | `AA30_rcplus`, `BB30_rcplus` |
| LL | `lc101.txt`, `lr101.txt` |
| RC_reverse | `AA30_reverse`, `DD30_reverse` |
| RC+_reverse | `AA30_rcplus_reverse`, `DD30_rcplus_reverse` |
| LL_reverse | `lc101.txt_reverse`, `lr101.txt_reverse` |

RC+ 和 RC+_reverse 的本地生成规则：

- 车辆容量加 10。
- 所有节点时间窗右端加 25。
- 生成文件保存于 `logs/paper12_inputs`。

注意：`RC+` / `RC+_reverse` 是本轮实验 manifest 和聚合结果中的论文组口径。单独调用 `BenchmarkInstanceReader.paperGroup(...)` 时，reader 主要按文件名前缀识别 `AA/BB/CC/DD` 为 `RC` 或 `RC_reverse`，不会天然把 `_rcplus` 文件名归为 `RC+`。

### 4.2 命令口径

每个测试点运行：

```bat
run.bat root-lp-pricing-smoke --instance <instance> --max-route-requests 3 --max-three-request-routes 600
run.bat root-finite-cg-smoke --instance <instance> --max-route-requests 3 --max-three-request-routes 600
```

候选池含义：

- 一请求路线全部进入候选池。
- 二请求路线全部进入候选池。
- 三请求路线最多额外加入 600 条。
- `root-lp-pricing-smoke` 只求一次初始 restricted root LP，然后扫描候选池。
- `root-finite-cg-smoke` 重复求 LP、扫描候选池、加入负 reduced-cost 列，直到有限池内没有未加入负列。

### 4.3 汇总指标

| 指标 | 结果 |
|---|---:|
| 测试点数量 | 12 |
| 运行命令数量 | 24 |
| 失败命令数量 | 0 |
| 初始 root LP 发现负候选 | 12 / 12 |
| finite-pool CG 清空负候选 | 12 / 12 |
| 出现正 artificial column | 0 / 12 |

### 4.4 分组汇总

| 组别 | 样本数 | 平均候选路径数 | 初始负候选均值 | 初始最小 reduced cost 均值 | 有限池清空数 | 平均迭代数 | 平均加入列数 |
|---|---:|---:|---:|---:|---:|---:|---:|
| RC | 2 | 1113.0 | 516.0 | -64.70 | 2 / 2 | 5.0 | 255.0 |
| RC+ | 2 | 1379.5 | 613.5 | -77.75 | 2 / 2 | 5.0 | 292.0 |
| LL | 2 | 1010.5 | 107.0 | -81.55 | 2 / 2 | 4.0 | 116.5 |
| RC_reverse | 2 | 1212.5 | 532.5 | -84.18 | 2 / 2 | 6.0 | 298.0 |
| RC+_reverse | 2 | 1564.0 | 578.0 | -98.79 | 2 / 2 | 5.5 | 315.0 |
| LL_reverse | 2 | 1010.5 | 74.0 | -58.60 | 2 / 2 | 4.0 | 108.5 |

### 4.5 结果解释

这个实验验证的是基础但关键的一步：初始 RMP 并没有提前覆盖有限候选池内所有改进列，而 pricing 扫描可以发现这些负 reduced-cost 候选；随后 finite CG 能把这些候选逐步加入 RMP，使有限池内不再剩余负列。

这说明以下程序链路在 12 个代表样本上闭合：

```text
benchmark parser
  -> seed columns
  -> Gurobi RMP
  -> LP solve
  -> dual extraction
  -> reduced-cost scan
  -> negative column insertion
  -> repeated finite CG
```

但它不说明 exact pricing 已经对 full-size 实例完成了“无负列”证明，因为候选池是人为限制的。

## 5. 12 个 6-request 派生子实例 BCP pricing-loop 小实验

详细文档见 [bcp12_small_experiment.md](bcp12_small_experiment.md)。

### 5.1 实验对象

从同一批 12 个代表样本中各抽取前 6 个请求，输出到：

```text
logs/bcp12_inputs/
```

抽取命令示例：

```bat
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 6 --output logs\bcp12_inputs\AA30_n6
```

### 5.2 BCP 命令口径

每个子实例运行：

```bat
run.bat bcp --instance logs\bcp12_inputs\<name>_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

关键参数解释：

| 参数 | 含义 |
|---|---|
| `--pricing bidir-dynamic` | 使用 bidirectional dynamic labeling pricing |
| `--cuts sr` | 打开 subset-row cut separation |
| `--max-nodes 20` | 限制 BCP 节点数 |
| `--max-cg-iterations 100` | 限制每个节点的列生成迭代数 |
| `--max-set-branch-size 3` | set-outflow branching 只枚举最多 3 请求集合 |
| `--max-route-requests 3 --max-three-request-routes 600` | 构造 seed route pool，不限制 exact labeling pricing |

### 5.3 明细结果

结果源文件：[logs/bcp12_results/bcp12_sr_summary.csv](../logs/bcp12_results/bcp12_sr_summary.csv)

| 组别 | 子实例 | 状态 | Root LB | Integer UB | Nodes | Columns | Pricing Calls | Fwd Labels | Bwd Labels | Dominated |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| RC | `AA30_n6` | `optimal_benchmark_branch_tree` | 216.5823 | 216.5823 | 1 | 67 | 2 | 150 | 162 | 53 |
| RC | `BB30_n6` | `optimal_benchmark_branch_tree` | 366.6450 | 366.6450 | 1 | 41 | 2 | 95 | 90 | 15 |
| RC+ | `AA30_rcplus_n6` | `optimal_benchmark_branch_tree` | 208.0708 | 208.0708 | 1 | 134 | 2 | 362 | 325 | 255 |
| RC+ | `BB30_rcplus_n6` | `optimal_benchmark_branch_tree` | 366.6450 | 366.6450 | 1 | 42 | 2 | 143 | 129 | 19 |
| LL | `lc101_n6` | `optimal_benchmark_branch_tree` | 59.6181 | 59.6181 | 1 | 42 | 2 | 265 | 244 | 111 |
| LL | `lr101_n6` | `optimal_benchmark_branch_tree` | 409.5828 | 409.5828 | 1 | 6 | 2 | 56 | 32 | 0 |
| RC_reverse | `AA30_reverse_n6` | `optimal_benchmark_branch_tree` | 216.5823 | 216.5823 | 1 | 67 | 2 | 162 | 141 | 53 |
| RC_reverse | `DD30_reverse_n6` | `optimal_benchmark_branch_tree` | 415.1672 | 415.1672 | 1 | 52 | 2 | 126 | 102 | 22 |
| RC+_reverse | `AA30_rcplus_reverse_n6` | `optimal_benchmark_branch_tree` | 208.0708 | 208.0708 | 1 | 134 | 2 | 333 | 353 | 255 |
| RC+_reverse | `DD30_rcplus_reverse_n6` | `optimal_benchmark_branch_tree` | 346.0029 | 346.0029 | 1 | 111 | 2 | 312 | 262 | 173 |
| LL_reverse | `lc101_reverse_n6` | `optimal_benchmark_branch_tree` | 146.3006 | 146.3006 | 1 | 33 | 2 | 226 | 211 | 48 |
| LL_reverse | `lr101_reverse_n6` | `optimal_benchmark_branch_tree` | 292.8522 | 292.8522 | 1 | 8 | 2 | 95 | 53 | 4 |

### 5.4 结果解释

这 12 行里最重要的证据是：

- `pricing=bidir-dynamic`。
- `status=optimal_benchmark_branch_tree`。
- `forwardLabels` 和 `backwardLabels` 都有非 `NA`、非零计数。
- `dominatedLabels` 有非 `NA` 统计，部分实例可为 0。

这证明 BCP 节点 pricing 调用的不是 tiny route-universe backend，而是 exact labeling backend，并且 bidirectional dynamic labeling 在 BCP pricing loop 中真实运行。

本批 12 个子实例全部在 root node 得到整数解，因此没有实际展开分支子节点。这不削弱“pricing loop 已接入”的结论，但它意味着这批实验本身不能证明复杂分支树上的性能。因此本文把它称为 BCP pricing-loop 小实验，而不是论文级完整 BCP 性能复现。

本批运行启用了 `--cuts sr`，但 `activeCuts=0`。因此可以说 SR separation path 已打开并执行检查，但这批数据没有分离出违反的 SR cut。

## 6. Branching sanity check

为避免 12 个 n=6 子实例都在 root node 求解而无法看到分支行为，另有一个 3-request benchmark 文本实例：

```text
logs/bcp12_inputs/forced_branch_benchmark_n3
```

运行结果：

```text
logs/bcp12_results/forced_branch_benchmark_n3_bcp_forward.txt
```

关键现象：

- 处理 3 个节点。
- root node 的 `pruneReason=branched_vehicle_count`。
- 两个子节点分别带有 `sum_lambda >= 2` 和 `sum_lambda <= 1`。

这个 sanity check 使用 forward pricing，只说明 branch-tree mechanics、vehicle-count branching 和 inherited branch rows 能在 benchmark-labeling BCP 路径中工作；它不作为 bidirectional dynamic labeling 的证据。

## 7. AA30 子实例 BCP 可解规模边界

### 7.1 实验目的

前面的 12 个 6-request 小实验能证明 bidirectional dynamic labeling 已经进入 BCP pricing loop，但规模偏小。因此补充一组 `AA30` 单源子实例边界测试：从同一个 full-size RC 算例 `AA30` 抽取 8、10、12、15、18 个 request，使用同一条 exact bidirectional dynamic benchmark BCP 路径求解。

这组实验回答的问题是：

- 当前程序是否只能跑 6-request 小实例。
- 当前 exact bidirectional dynamic BCP 大致能证明到什么规模。
- 从可解子实例走向 full-size `AA30` 时，性能差距在哪里开始显现。

### 7.2 命令口径

子实例生成示例：

```bat
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 15 --output logs\bcp_size_boundary\AA30_n15
```

BCP 运行示例：

```bat
run.bat bcp --instance logs\bcp_size_boundary\AA30_n15 --pricing bidir-dynamic --cuts sr --max-nodes 80 --max-cg-iterations 300 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 3000 --trace
```

完整结果归档：

```text
logs/bcp_size_boundary/aa30_bcp_size_boundary.csv
```

### 7.3 结果

| 子实例 | Requests | 顶点数 | 状态 | Root LB | Integer UB | Nodes | Columns | Fwd Labels | Bwd Labels | Dominated | Pricing ms | Total ms |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `AA30_n8` | 8 | 18 | `optimal_benchmark_branch_tree` | 235.6542 | 235.6542 | 1 | 189 | 415 | 442 | 267 | 153 | 306 |
| `AA30_n10` | 10 | 22 | `optimal_benchmark_branch_tree` | 357.1842 | 357.1842 | 1 | 300 | 634 | 990 | 323 | 386 | 667 |
| `AA30_n12` | 12 | 26 | `optimal_benchmark_branch_tree` | 421.6994 | 421.6994 | 1 | 473 | 1105 | 1244 | 794 | 619 | 1201 |
| `AA30_n15` | 15 | 32 | `optimal_benchmark_branch_tree` | 523.2750 | 523.2750 | 1 | 805 | 4127 | 3883 | 1371 | 112946 | 143455 |
| `AA30_n18` | 18 | 38 | timeout | - | - | - | - | - | - | - | - | 304000+ |

### 7.4 解释

这组结果表明，当前 BCP 不只是在 6-request 小规模实例上可运行。`AA30_n8`、`AA30_n10`、`AA30_n12` 和 `AA30_n15` 都能通过 exact bidirectional dynamic labeling BCP 证明最优，且 forward/backward label counters 均有实际计数。

但规模增长后的耗时跳变很明显：`AA30_n12` 约 1.2 秒，`AA30_n15` 约 143 秒，`AA30_n18` 在约 304 秒内未完成。这个结果说明当前实现的稳定可解规模更接近 12-15 requests，而不是论文 full-size RC 的 30 requests。因此它增强了“程序链路真实可运行”的证据，同时也清楚揭示了与论文 Table 3 工程性能之间的差距。

需要注意：这是一组 `AA30` 单源子实例边界测试，不代表所有 RC/LL 组。它适合用作当前程序能力边界说明，不适合替代论文 220 个 full-size benchmark 实验。

## 8. Full-size BCP 边界

### 8.1 默认 route-universe BCP 被拒绝

文件：

```text
logs/bcp12_results/bcp12_fullsize_default_probe.csv
```

12 个 full-size 样本的默认 `bcp` 命令均得到：

```text
Benchmark RC/LL BCP requires explicit labeling pricing, for example --pricing bidir-dynamic; route-universe exact pricing is tiny-only
```

这不是 bug，而是有意保护：route-universe exact enumeration 只适合 tiny 实例，不适合 30/53 request benchmark。

### 8.2 AA30 full-size exact-labeling probe 超时

文件：

```text
logs/bcp12_results/fullsize_exact_aa30_probe.txt
```

命令：

```bat
run.bat bcp --instance G:/bid/PDPTW_instances/RC/AA30 --pricing bidir-dynamic --cuts none --max-nodes 1 --max-cg-iterations 20 --max-set-branch-size 1 --max-route-requests 3 --max-three-request-routes 600
```

结果：

```text
Timed out after 600 seconds during full-size exact-labeling BCP probe.
```

解释：

benchmark text BCP 入口和 exact labeling path 已打开，但当前 Java 实现还不能把 full-size `AA30` 作为常规完整 BCP 实验快速跑完。

## 9. 可以声明什么

可以声明：

- 已实现 PDPTW RMP、dual extraction、reduced-cost matrices、forward/backward labeling、bidirectional static/dynamic labeling、label merge、DTI/PTI check、subset-row pricing state、branch-node exact labeling pricing、vehicle-count/set-outflow branching 和受控 BCP 主循环。
- 在 12 个代表性 full-size 样本上，root LP / finite-pool pricing-only 实验稳定完成。
- 在 12 个 6-request 派生子实例上，`bidir-dynamic` 成功嵌入 BCP pricing loop，且 label counters 提供了双向标签实际运行证据。
- 在 `AA30` 子实例规模边界测试中，当前 exact bidirectional dynamic BCP 能证明 8、10、12、15 request 子实例最优，但 18 request 在约 304 秒内未完成。
- 当前程序已经是一个有验证结果的论文核心算法复现原型。

## 10. 不能声明什么

不能声明：

- 已复现论文完整 220 个算例。
- 已复现论文 Table 2 或 Table 3。
- 已达到论文 full-size runtime 或 solved-count。
- robust cut separation 已具备 benchmark-scale 能力。
- full-size exact BCP 已经可作为批量实验稳定运行。
- `AA30` 子实例边界测试可以代表全部论文组或 full-size benchmark。

## 11. 后续工作建议

如果目标是从“不错的核心程序复现”推进到“论文全表级复现”，建议按以下顺序：

1. 优化 full-size exact labeling pricing。
2. 在 full-size root LP 上对 forward/backward/bidir-static/bidir-dynamic 做同口径定量对照。
3. 设计 benchmark-scale SR candidate selection，避免全三元组无控制枚举。
4. 重新实现或扩展 robust cut candidate generation，使其脱离 tiny-only brute-force oracle。
5. 增强 branching 策略、node selection 和 incumbent 维护。
6. 建立 220-instance batch runner 和结果归档格式。
7. 再与论文 Table 2/3 做分组统计对照。
