# 12-Instance Controlled BCP Small Experiment

日期：2026-05-13

## 1. 目标和边界

本轮目标是把当前 `bcp` 能力推进到可运行的完整 branch-and-price 小实验：

- 先确认 full-size RC/LL 直接跑当前 `bcp` 的能力边界。
- 打开 benchmark 文本实例的 exact labeling pricing 路径，避免 tiny-only route-universe 穷举。
- 对 12 个论文组代表样本各抽取 6 个请求，运行完整 BCP 主循环。

注意：这仍不是论文 Table 3 的 220 个 full-size 实例复现。它是从论文样本派生的受控小实验，用于验证 BCP 主循环、exact pricing、branching、SR cut separation 的程序链路。

## 2. 当前 BCP 能力检查

原始 `bcp` 入口此前有两个限制：

- 默认 `route-universe` pricing 会调用 `BruteForcePricingOracle` 枚举完整路线全集，只适合 tiny 实例。
- `RunBcp` 对 RC/LL 文本文件没有走 `BenchmarkInstanceReader`，因此 full-size RC/LL 不能直接进入完整 BCP。

本轮修改后：

- benchmark 文本实例必须显式指定 labeling pricing，例如 `--pricing bidir-dynamic`。
- benchmark BCP 走 `LabelingNodePricingBackend`，不走 brute-force route-universe。
- benchmark BCP 支持 `--cuts none` 和 `--cuts sr`。
- `--cuts robust` / `--cuts robust,sr` 对 benchmark 文本实例仍被拒绝，因为当前 robust candidate generator 是 tiny-only。

12 个 full-size 代表样本的默认 `bcp` 探测均被明确拒绝为 route-universe tiny-only 路径。探测结果保存于
`logs/bcp12_results/bcp12_fullsize_default_probe.csv`。

对 full-size `AA30` 做了 10 分钟尝试：

```bat
run.bat bcp --instance G:/bid/PDPTW_instances/RC/AA30 --pricing bidir-dynamic --cuts none --max-nodes 1 --max-cg-iterations 20 --max-set-branch-size 1 --max-route-requests 3 --max-three-request-routes 600
```

结果：600 秒超时未完成，记录见 `logs/bcp12_results/fullsize_exact_aa30_probe.txt`。结论是入口和 exact pricing 路径已经打开，但当前实现尚不能把 30 请求 full-size 实例作为常规完整 BCP 实验直接跑完。

补充规模边界测试见第 5 节：在 `AA30` 子实例上，8、10、12、15 request 均可由 `bidir-dynamic` BCP 证明最优，18 request 在约 304 秒内未完成。这个结果说明当前程序不止能跑 6-request 小实例，但距离 full-size 30-request `AA30` 仍有明显性能差距。

## 3. 小实验设计

从前一轮 12 个论文组代表样本中，各抽取前 6 个请求，统一输出为 RC-style benchmark 文本：

```bat
run.bat benchmark-subinstance --instance <source> --requests 6 --output logs/bcp12_inputs/<name>_n6
```

然后对每个子实例运行：

```bat
run.bat bcp --instance logs/bcp12_inputs/<name>_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

参数含义：

- `--pricing bidir-dynamic`：使用 exact bidirectional dynamic labeling pricing。
- `--cuts sr`：启用 subset-row separation。
- `--max-nodes 20`：限制分支树节点数，避免小实验失控。
- `--max-cg-iterations 100`：限制每个节点的列生成迭代数。
- `--max-set-branch-size 3`：限制 set-outflow branching 候选请求集合大小。
- `--max-route-requests 3 --max-three-request-routes 600`：只用于构造初始 seed route pool 和分支可行性列，不限制 exact labeling pricing。

## 4. 12 个实验结果

| 组别 | 源实例 | 子实例 | 状态 | Root LB | Integer UB | Gap | Nodes | Columns | Cuts | Pricing Calls | Fwd Labels | Bwd Labels | Dominated | Pricing ms | Total ms |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| RC | AA30 | AA30_n6 | optimal_benchmark_branch_tree | 216.5823 | 216.5823 | 0.0000 | 1 | 67 | 0 | 2 | 150 | 162 | 53 | 54 | 130 |
| RC | BB30 | BB30_n6 | optimal_benchmark_branch_tree | 366.6450 | 366.6450 | 0.0000 | 1 | 41 | 0 | 2 | 95 | 90 | 15 | 41 | 136 |
| RC+ | AA30_rcplus | AA30_rcplus_n6 | optimal_benchmark_branch_tree | 208.0708 | 208.0708 | 0.0000 | 1 | 134 | 0 | 2 | 362 | 325 | 255 | 111 | 186 |
| RC+ | BB30_rcplus | BB30_rcplus_n6 | optimal_benchmark_branch_tree | 366.6450 | 366.6450 | 0.0000 | 1 | 42 | 0 | 2 | 143 | 129 | 19 | 52 | 136 |
| LL | lc101.txt | lc101_n6 | optimal_benchmark_branch_tree | 59.6181 | 59.6181 | 0.0000 | 1 | 42 | 0 | 2 | 265 | 244 | 111 | 62 | 145 |
| LL | lr101.txt | lr101_n6 | optimal_benchmark_branch_tree | 409.5828 | 409.5828 | 0.0000 | 1 | 6 | 0 | 2 | 56 | 32 | 0 | 22 | 88 |
| RC_reverse | AA30_reverse | AA30_reverse_n6 | optimal_benchmark_branch_tree | 216.5823 | 216.5823 | 0.0000 | 1 | 67 | 0 | 2 | 162 | 141 | 53 | 53 | 118 |
| RC_reverse | DD30_reverse | DD30_reverse_n6 | optimal_benchmark_branch_tree | 415.1672 | 415.1672 | 0.0000 | 1 | 52 | 0 | 2 | 126 | 102 | 22 | 50 | 132 |
| RC+_reverse | AA30_rcplus_reverse | AA30_rcplus_reverse_n6 | optimal_benchmark_branch_tree | 208.0708 | 208.0708 | 0.0000 | 1 | 134 | 0 | 2 | 333 | 353 | 255 | 111 | 187 |
| RC+_reverse | DD30_rcplus_reverse | DD30_rcplus_reverse_n6 | optimal_benchmark_branch_tree | 346.0029 | 346.0029 | 0.0000 | 1 | 111 | 0 | 2 | 312 | 262 | 173 | 91 | 166 |
| LL_reverse | lc101.txt_reverse | lc101_reverse_n6 | optimal_benchmark_branch_tree | 146.3006 | 146.3006 | 0.0000 | 1 | 33 | 0 | 2 | 226 | 211 | 48 | 48 | 113 |
| LL_reverse | lr101.txt_reverse | lr101_reverse_n6 | optimal_benchmark_branch_tree | 292.8522 | 292.8522 | 0.0000 | 1 | 8 | 0 | 2 | 95 | 53 | 4 | 34 | 114 |

汇总：

- 12 / 12 个小实验成功完成。
- 12 / 12 个状态为 `optimal_benchmark_branch_tree`。
- 所有实验均输出非 `NA` 的 forward/backward/dominated label counters，说明使用的是 exact labeling pricing，而不是 route-universe backend。
- 本批样本都在根节点得到整数解，因此没有实际展开子节点；branching 代码路径已打开并受 `--max-nodes` / `--max-set-branch-size` 控制，但这批数据没有触发分支。
- 本批运行启用了 `--cuts sr`，但没有发现违反的 subset-row cut，因此 active cut 数为 0。

补充分支验证：另外构造了一个 3-request benchmark 文本实例
`logs/bcp12_inputs/forced_branch_benchmark_n3`，使用 `--pricing forward --cuts none` 运行同一 benchmark-labeling
BCP 路径。结果处理 3 个节点，根节点 `pruneReason=branched_vehicle_count`，两个子节点分别为
`sum_lambda >= 2` 和 `sum_lambda <= 1`。原始输出保存在
`logs/bcp12_results/forced_branch_benchmark_n3_bcp_forward.txt`。

## 5. AA30 子实例 BCP 可解规模边界

为了避免只用 6-request 小实验低估或高估当前 BCP 能力，补充运行了 `AA30` 的 8、10、12、15、18 request 子实例。所有子实例由同一个 full-size `AA30` 抽取，使用 benchmark-labeling BCP 路径、`--pricing bidir-dynamic` 和 `--cuts sr`。

结果源文件：

```text
logs/bcp_size_boundary/aa30_bcp_size_boundary.csv
```

| 子实例 | Requests | 顶点数 | 状态 | Nodes | Columns | Fwd Labels | Bwd Labels | Dominated | Total ms |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|
| `AA30_n8` | 8 | 18 | `optimal_benchmark_branch_tree` | 1 | 189 | 415 | 442 | 267 | 306 |
| `AA30_n10` | 10 | 22 | `optimal_benchmark_branch_tree` | 1 | 300 | 634 | 990 | 323 | 667 |
| `AA30_n12` | 12 | 26 | `optimal_benchmark_branch_tree` | 1 | 473 | 1105 | 1244 | 794 | 1201 |
| `AA30_n15` | 15 | 32 | `optimal_benchmark_branch_tree` | 1 | 805 | 4127 | 3883 | 1371 | 143455 |
| `AA30_n18` | 18 | 38 | timeout | - | - | - | - | - | 304000+ |

解释：

- 当前 BCP 可以在 `AA30` 的 8-15 request 子实例上证明最优。
- 所有已完成子实例都在 root node 得到整数解，因此这仍不是复杂 branch tree 性能证明。
- `AA30_n15` 的耗时已经上升到约 143 秒，`AA30_n18` 在约 304 秒内未完成，说明 exact labeling pricing 的规模压力已经非常明显。
- 因此当前程序的稳定可解规模可以保守表述为 12-15 requests；论文 full-size `AA30` 是 30 requests，仍不具备论文 Table 3 级别性能。

## 6. 程序改动摘要

本轮补齐或打开的能力：

- `RunBcp`：benchmark 文本实例走 `BenchmarkInstanceReader`，要求显式 labeling pricing，拒绝 benchmark route-universe 和 robust cuts。
- `BranchAndPriceSolver`：新增 benchmark-labeling 构造路径，使用 exact labeling backend，支持节点列生成迭代上限和 benchmark 状态名。
- `SetOutflowBrancher`：新增最大请求集合大小限制，避免 30/53 请求时全子集枚举失控。
- `RunBenchmarkSubinstance`：新增从 benchmark 实例抽取 6-request 子实例的 CLI。
- 测试：新增 benchmark BCP CLI 正向和负向测试；完整 `test.bat` 已通过。

## 7. 输出文件

- 汇总 CSV：`G:/bid/pdptw-bcp-java-gurobi/logs/bcp12_results/bcp12_sr_summary.csv`
- AA30 子实例规模边界：`G:/bid/pdptw-bcp-java-gurobi/logs/bcp_size_boundary/aa30_bcp_size_boundary.csv`
- full-size 默认能力探测：`G:/bid/pdptw-bcp-java-gurobi/logs/bcp12_results/bcp12_fullsize_default_probe.csv`
- full-size exact 长时探测：`G:/bid/pdptw-bcp-java-gurobi/logs/bcp12_results/fullsize_exact_aa30_probe.txt`
- 12 个子实例：`G:/bid/pdptw-bcp-java-gurobi/logs/bcp12_inputs`
- 12 个原始运行输出：`G:/bid/pdptw-bcp-java-gurobi/logs/bcp12_results/*_bcp_sr.txt`
- benchmark 文本分支验证：`G:/bid/pdptw-bcp-java-gurobi/logs/bcp12_results/forced_branch_benchmark_n3_bcp_forward.txt`
