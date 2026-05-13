# PDPTW BCP Java/Gurobi

[English](README.md) | [中文](README.zh-CN.md)

这是一个 Java + Gurobi 的 PDPTW branch-cut-and-price 复现实验仓库，目标是复现并验证 Gschwind 等人在 PDPTW 列生成中提出的双向标签思想，以及它嵌入 BCP 的主要程序链路。

当前仓库适合用于论文算法复现、教学演示和后续实验开发。它已经覆盖 tiny correctness、12 个 full-size 代表样本的 root LP / pricing-only 受控实验、12 个派生 6-request BCP pricing-loop 小实验，以及 `AA30` 子实例上的 BCP 可解规模边界测试。

注意：本仓库目前还不能声明完整复现论文的 220 个 full-size benchmark 表格。

## 复现对象

> Timo Gschwind, Stefan Irnich, Ann-Kathrin Rothenbaecher, Christian Tilk.  
> Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems.  
> European Journal of Operational Research 266 (2018) 521-530.

论文研究 PDPTW 的 branch-cut-and-price 算法，重点是 forward labeling、backward labeling、bidirectional labeling、dynamic half-way point，以及这些策略在 220 个 PDPTW benchmark 实例上的表现。

## 当前范围

已经实现或部分实现：

- PDPTW 数据模型和路线可行性检查。
- RC / LL benchmark 文本读取。
- Gurobi restricted master problem。
- route column、artificial column、LP dual extraction。
- forward / backward / bidirectional static / bidirectional dynamic labeling。
- dynamic half-way 控制。
- subset-row cut 的数据结构和受控集成路径。
- robust cut 相关脚手架和 DTI/PTI repair。
- vehicle-count branching 和 set-outflow branching。
- tiny 和 benchmark 子实例上的受控 branch-and-price loop。
- root LP、pricing probe、finite-pool CG、小规模 BCP 实验 CLI。

还不能声明：

- 已经复现论文 Table 2 或 Table 3。
- 已经完成 220 个 full-size 算例的完整 BCP 实验。
- 已达到论文 C++/CPLEX 实现的 full-size 性能。

## 已包含算例

仓库包含 `PDPTW_instances/` 目录，方便直接复现实验：

- `PDPTW_instances/RC`
- `PDPTW_instances/LL`

这些算例用于当前的代表性 root LP / pricing-only 实验，以及派生的 BCP 小规模子实例实验。

## 主要结果

### 12 个 full-size 代表样本 root LP / pricing-only 实验

相关文件：

- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md)
- [logs/paper12_results/paper12_root_lp.csv](logs/paper12_results/paper12_root_lp.csv)
- [logs/paper12_results/paper12_finite_cg.csv](logs/paper12_results/paper12_finite_cg.csv)

结果摘要：

| 指标 | 结果 |
|---|---:|
| 代表测试点 | 12 |
| 初始 root LP 发现负 reduced-cost 候选 | 12 / 12 |
| finite-pool CG 后没有剩余负候选 | 12 / 12 |
| 出现正 artificial column | 0 / 12 |

这说明当前程序在代表样本上已经打通：

```text
benchmark instance -> restricted root LP -> dual extraction -> reduced-cost scan -> finite-pool column generation
```

### 12 个 6-request 派生子实例 BCP pricing-loop 实验

相关文件：

- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md)
- [logs/bcp12_results/bcp12_sr_summary.csv](logs/bcp12_results/bcp12_sr_summary.csv)

这批实验说明 `bidir-dynamic` pricing 已经接入受控 BCP pricing loop。

### AA30 BCP 可解规模边界

相关文件：

- [logs/bcp_size_boundary/aa30_bcp_size_boundary.csv](logs/bcp_size_boundary/aa30_bcp_size_boundary.csv)

当前实现可以在 `AA30` 的 8-15 request 子实例上证明最优，但还没有达到 full-size 30 request 的论文级性能。

## 环境要求

- Windows 命令行环境。
- Java JDK 17 或更新版本。
- Gurobi，并安装 Java API。
- 设置 `GUROBI_HOME` 到 Gurobi 的 `win64` 目录。

示例：

```bat
set GUROBI_HOME=D:\Application_install\gurobi\win64
```

## 快速开始

编译：

```bat
build.bat
```

运行测试：

```bat
test.bat
```

读取一个 benchmark 算例：

```bat
run.bat instance-smoke --instance PDPTW_instances\RC\AA30
```

运行 root LP / pricing probe：

```bat
run.bat root-lp-pricing-smoke --instance PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
```

运行 finite-pool CG：

```bat
run.bat root-finite-cg-smoke --instance PDPTW_instances\RC\AA30 --max-route-requests 3 --max-three-request-routes 600
```

生成 6-request 子实例：

```bat
run.bat benchmark-subinstance --instance PDPTW_instances\RC\AA30 --requests 6 --output logs\bcp12_inputs\AA30_n6
```

运行受控 BCP pricing-loop：

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --max-nodes 20 --max-cg-iterations 100 --max-set-branch-size 3 --max-route-requests 3 --max-three-request-routes 600 --trace
```

## 文档入口

- [docs/reproduction_scope_and_results.md](docs/reproduction_scope_and_results.md)
- [docs/paper12_root_pricing_experiment.md](docs/paper12_root_pricing_experiment.md)
- [docs/bcp12_small_experiment.md](docs/bcp12_small_experiment.md)
- [docs/paper_to_code_mapping.md](docs/paper_to_code_mapping.md)
- [docs/bidirectional_labeling_notes.md](docs/bidirectional_labeling_notes.md)
- [docs/experiments_quickstart.md](docs/experiments_quickstart.md)

## 公开表述建议

建议说：

- 本仓库复现了 PDPTW 双向标签列生成的核心实现，并在受控样本上验证了 root LP / pricing-only 和 BCP pricing-loop 链路。
- 当前结果是受控复现，不是论文完整 220 个 full-size benchmark 表格复现。

不建议说：

- 已经完整复现论文全部实验。
- 已经复现 Table 2 / Table 3。
- 当前 Java 程序已经能稳定求解全部 full-size RC/LL BCP。

## License

本仓库使用 MIT License，见 [LICENSE](LICENSE)。
