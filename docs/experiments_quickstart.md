# 实验复现 Quick Start

本文档整理公开仓库中最常用的构建、测试和实验命令。所有命令默认在仓库根目录运行：

```bat
cd /d G:\bid\pdptw-bcp-java-gurobi
```

## 1. 环境准备

需要：

- Java JDK 17+。
- Gurobi Java API。
- `GUROBI_HOME` 指向 Gurobi `win64` 目录。

示例：

```bat
set GUROBI_HOME=D:\Application_install\gurobi\win64
```

检查 Gurobi jar：

```bat
dir "%GUROBI_HOME%\lib\gurobi.jar"
```

## 2. 编译与测试

编译主程序：

```bat
build.bat
```

运行测试：

```bat
test.bat
```

说明：

- 本项目没有 Maven/Gradle。
- `build.bat` 使用 `javac --release 17`。
- `test.bat` 编译 plain Java tests，并运行 `org.pdptw.TestRunner`。

## 3. Tiny correctness commands

### 3.1 Pricing audit

```bat
run.bat pricing-audit --instance ..\references\tiny\tiny-a-wide.json
```

用途：

- 比较当前 tiny instance 上的 pricing 行为。
- 适合快速检查 reduced-cost 和 label counters。

### 3.2 Root column generation

```bat
run.bat root-cg --instance ..\references\tiny\tiny-a-wide.json --pricing bidir-dynamic --trace
```

可替换 pricing：

```bat
--pricing forward
--pricing backward
--pricing bidir-static
--pricing bidir-dynamic
```

### 3.3 Tiny BCP

```bat
run.bat bcp --instance ..\references\tiny\tiny-a-wide.json --cuts none --branching timo --trace
```

带 SR cut 的 tiny explicit-labeling BCP：

```bat
run.bat bcp --instance ..\references\tiny\tiny-sr-cli.json --pricing bidir-dynamic --cuts sr --branching timo --trace
```

## 4. Benchmark instance parsing smoke

读取 RC/LL 文件并输出 metadata：

```bat
run.bat instance-smoke --instance ..\PDPTW_instances\RC\AA30
run.bat instance-smoke --instance ..\PDPTW_instances\LL\lc101.txt
```

用途：

- 检查 benchmark reader 是否能解析文件。
- 不求解 BCP。

## 5. 12 个 full-size 代表样本的 root LP / pricing-only 实验

完整结果见：

- `docs/paper12_root_pricing_experiment.md`
- `logs/paper12_results/paper12_root_lp.csv`
- `logs/paper12_results/paper12_finite_cg.csv`

单个样本命令：

```bat
run.bat root-lp-pricing-smoke --instance ..\PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
run.bat root-finite-cg-smoke --instance ..\PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
```

LL 样本：

```bat
run.bat root-lp-pricing-smoke --instance ..\PDPTW_instances\LL\lc101.txt --max-route-requests 3 --max-three-request-routes 600
run.bat root-finite-cg-smoke --instance ..\PDPTW_instances\LL\lc101.txt --max-route-requests 3 --max-three-request-routes 600
```

注意：

- 这两个命令使用有限候选池。
- 它们不是 exact pricing。
- 它们不是论文 Table 2/3 runner。

## 6. 生成 6-request benchmark 子实例

从 full-size benchmark 抽取前 6 个请求：

```bat
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 6 --output logs\bcp12_inputs\AA30_n6
```

其他例子：

```bat
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\BB30 --requests 6 --output logs\bcp12_inputs\BB30_n6
run.bat benchmark-subinstance --instance ..\PDPTW_instances\LL\lc101.txt --requests 6 --output logs\bcp12_inputs\lc101_n6
run.bat benchmark-subinstance --instance ..\PDPTW_instances\LL\lr101.txt --requests 6 --output logs\bcp12_inputs\lr101_n6
```

说明：

- 子实例输出为 RC-style benchmark text。
- 这是为了做受控 BCP pricing-loop 小实验，不代表原始 full-size 实例。

## 7. 运行 6-request BCP pricing-loop 小实验

单个样本：

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

参数说明：

| 参数 | 作用 |
|---|---|
| `--pricing bidir-dynamic` | 使用动态双向标签 pricing |
| `--cuts sr` | 打开 subset-row cut separation |
| `--max-nodes 20` | 最多处理 20 个 branch nodes |
| `--max-cg-iterations 100` | 每个节点最多 100 次 column generation |
| `--max-set-branch-size 3` | set-outflow branching 最多枚举 3 请求集合 |
| `--max-route-requests 3` | seed route pool 最多用 3-request routes |
| `--max-three-request-routes 600` | seed pool 中 3-request routes 最多 600 条 |
| `--trace` | 输出 node trace CSV |

重要边界：

- seed route pool 只用于 RMP 初始列和 branch feasibility。
- exact pricing 由 `bidir-dynamic` labeling solver 完成，不受 `--max-route-requests` 限制。

## 8. AA30 子实例 BCP 可解规模边界

这组命令用于复现当前 BCP 的规模边界，而不是论文 full-size 实验。建议先跑 8、10、12 requests，再谨慎尝试 15 requests；15 requests 本轮耗时约 143 秒。

生成子实例：

```bat
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 8 --output logs\bcp_size_boundary\AA30_n8
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 10 --output logs\bcp_size_boundary\AA30_n10
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 12 --output logs\bcp_size_boundary\AA30_n12
run.bat benchmark-subinstance --instance ..\PDPTW_instances\RC\AA30 --requests 15 --output logs\bcp_size_boundary\AA30_n15
```

运行 BCP：

```bat
run.bat bcp --instance logs\bcp_size_boundary\AA30_n8 --pricing bidir-dynamic --cuts sr --max-nodes 50 --max-cg-iterations 200 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 1000 --trace
run.bat bcp --instance logs\bcp_size_boundary\AA30_n10 --pricing bidir-dynamic --cuts sr --max-nodes 50 --max-cg-iterations 200 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 1500 --trace
run.bat bcp --instance logs\bcp_size_boundary\AA30_n12 --pricing bidir-dynamic --cuts sr --max-nodes 50 --max-cg-iterations 200 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 2000 --trace
run.bat bcp --instance logs\bcp_size_boundary\AA30_n15 --pricing bidir-dynamic --cuts sr --max-nodes 80 --max-cg-iterations 300 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 3000 --trace
```

本轮记录结果：

```text
logs/bcp_size_boundary/aa30_bcp_size_boundary.csv
```

解释：

- `AA30_n8`、`AA30_n10`、`AA30_n12`、`AA30_n15` 均证明最优。
- `AA30_n15` 用时约 143 秒。
- `AA30_n18` 在约 304 秒内未完成，因此不建议把 18 request 作为常规 quickstart 命令。

## 9. Full-size BCP probe

默认 full-size `bcp` 会被拒绝：

```bat
run.bat bcp --instance ..\PDPTW_instances\RC\AA30
```

原因：

```text
route-universe exact pricing is tiny-only
```

显式 exact-labeling full-size probe：

```bat
run.bat bcp --instance ..\PDPTW_instances\RC\AA30 --pricing bidir-dynamic --cuts none --max-nodes 1 --max-cg-iterations 20 --max-set-branch-size 1 --max-route-requests 3 --max-three-request-routes 600
```

当前已记录结果：

```text
logs/bcp12_results/fullsize_exact_aa30_probe.txt
```

解释：

- 入口和 exact-labeling path 已打开。
- 当前实现 600 秒内没有完成 full-size AA30。
- 不应把该路径写成已完成 full-size BCP 复现。

## 10. 如何读取输出

`BenchmarkCsv` 输出字段：

```text
instance,mode,status,lowerBound,upperBound,gap,nodes,columns,activeCuts,pricingCalls,forwardLabels,backwardLabels,dominatedLabels,pricingTimeMs,totalTimeMs
```

关键字段解释：

| 字段 | 含义 |
|---|---|
| `status` | 求解状态，例如 `optimal_benchmark_branch_tree` |
| `lowerBound` | root 或当前 BCP lower bound |
| `upperBound` | incumbent objective，若没有 incumbent 则为 `NaN` |
| `nodes` | processed BCP nodes |
| `columns` | route universe / seed columns 规模，视命令而定 |
| `activeCuts` | active cut row 数 |
| `pricingCalls` | pricing backend 调用次数 |
| `forwardLabels` | forward labels 计数 |
| `backwardLabels` | backward labels 计数 |
| `dominatedLabels` | dominance 剪枝计数 |

判断 bidirectional dynamic labeling 是否实际运行：

```text
pricing=bidir-dynamic
forwardLabels > 0
backwardLabels > 0
```

注意：

- `pricing` 列存在于 `logs/bcp12_results/bcp12_sr_summary.csv` 这类聚合结果文件。
- 单次 `run.bat bcp` 的原始 CSV 输出没有 `pricing` 列，pricing mode 由命令参数 `--pricing bidir-dynamic` 确认。

## 11. 输出文件索引

| 文件 | 内容 |
|---|---|
| `logs/schema/benchmark-results.csv` | benchmark summary CSV schema |
| `logs/schema/root-cg-trace.csv` | root CG trace schema |
| `logs/schema/bcp-node-trace.csv` | BCP node trace schema |
| `logs/paper12_results/paper12_root_lp.csv` | 12 个 full-size 代表样本 root LP scan |
| `logs/paper12_results/paper12_finite_cg.csv` | 12 个 full-size 代表样本 finite CG |
| `logs/paper12_results/paper12_raw_results.json` | 12 个 root/pricing 实验原始结果 |
| `logs/bcp12_inputs/` | 12 个 6-request BCP 子实例 |
| `logs/bcp12_results/bcp12_sr_summary.csv` | 12 个 BCP pricing-loop 小实验汇总 |
| `logs/bcp12_results/*_bcp_sr.txt` | 每个 BCP pricing-loop 小实验原始输出 |
| `logs/bcp_size_boundary/aa30_bcp_size_boundary.csv` | AA30 子实例 BCP 可解规模边界 |
| `logs/bcp12_results/bcp12_fullsize_default_probe.csv` | full-size 默认 BCP 边界探测 |
| `logs/bcp12_results/fullsize_exact_aa30_probe.txt` | AA30 full-size exact-labeling probe |

## 12. 推荐公开复现实验顺序

如果读者第一次运行，建议按以下顺序：

1. `build.bat`
2. `test.bat`
3. `run.bat pricing-audit --instance ..\references\tiny\tiny-a-wide.json`
4. `run.bat root-cg --instance ..\references\tiny\tiny-a-wide.json --pricing bidir-dynamic --trace`
5. `run.bat benchmark-subinstance ... --requests 6 ...`
6. `run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr ...`
7. `run.bat benchmark-subinstance ... --requests 12 ...` 后运行 `logs\bcp_size_boundary\AA30_n12` 的 BCP 边界测试

这样可以先验证 tiny correctness，再验证 benchmark 子实例 BCP，而不是一开始就跑 full-size AA30。
