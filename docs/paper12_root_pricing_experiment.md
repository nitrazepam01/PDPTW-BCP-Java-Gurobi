# 12-Instance Root LP / Pricing-Only Controlled Experiment

日期：2026-05-13

## 1. 实验目的

本文档展示当前 Java/Gurobi 程序在 PDPTW 论文算例上的受限实验效果。实验只验证 root LP 与三请求有限候选池 pricing/column generation 的闭环，不声明复现论文完整 branch-cut-and-price 结果。

当前程序重点回答两个问题：

1. 初始受限 root LP 后，有限候选池中是否还能发现负 reduced-cost 路径。
2. 在同一有限池内重复加入负 reduced-cost 列后，是否能清空池内负列。

## 2. 论文对照口径

论文实验一共使用 220 个 PDPTW 测试实例，由 6 个组构成：

| 论文组 | 实例数 | 来源 |
|---|---:|---|
| RC | 40 | Ropke and Cordeau 原始实例 |
| RC+ | 40 | RC 上放宽时间窗右端并增加容量得到的修改组 |
| LL | 30 | Li and Lim 原始实例 |
| RC_reverse | 40 | RC 反向实例 |
| RC+_reverse | 40 | RC+ 反向实例 |
| LL_reverse | 30 | LL 反向实例 |

论文 Table 2 比较不同 labeling 策略在相同 ESPPRC pricing 问题上的时间比值，基准为 Fw-S。LP 部分中 Bi-Dy-SS 的总几何平均比值为 0.92，说明其 pricing 时间约比 Fw-S 低 8%；B&B 全树部分 Bi-Dy-SS 总比值为 0.58，约降低 42% pricing 时间。

论文 Table 3 是完整 branch-cut-and-price 独立运行结果。Bi-Dy-SS 在 220 个实例中求得 142 个最优解，平均时间 389.3s；Fw-S 为 128 个、734.2s；Bw-S 为 123 个、818.4s。

这些论文指标不能与本文的 root LP / pricing-only 受限实验逐实例直接比较，因为论文结果包含完整分支、割、启发式 pricing、精确 pricing 和整数最优性证明。本文只用论文组别作为横向参照，观察当前程序在每组代表实例上的受限列生成行为。

## 3. 本次 12 个测试点

按论文 6 个组每组选择 2 个代表实例，共 12 个测试点：

| 论文组 | 测试实例 1 | 测试实例 2 | 说明 |
|---|---|---|---|
| RC | AA30 | BB30 | 本地原始 RC 文件 |
| RC+ | AA30_rcplus | BB30_rcplus | 由 AA30、BB30 按论文规则生成 |
| LL | lc101.txt | lr101.txt | 本地 LL 文件 |
| RC_reverse | AA30_reverse | DD30_reverse | 本地反向 RC 文件 |
| RC+_reverse | AA30_rcplus_reverse | DD30_rcplus_reverse | 由反向 RC 文件按论文规则生成 |
| LL_reverse | lc101.txt_reverse | lr101.txt_reverse | 本地反向 LL 文件 |

RC+ 代表文件生成规则：对选中 RC 文件的车辆容量加 10，并将所有节点时间窗右端加 25。RC+_reverse 代表文件在本地反向 RC 文件上应用同样规则。生成文件保存于：

`G:/bid/pdptw-bcp-java-gurobi/logs/paper12_inputs`

## 4. 实验命令

每个测试点都运行两条命令：

```bat
run.bat root-lp-pricing-smoke --instance <instance> --max-route-requests 3 --max-three-request-routes 600
run.bat root-finite-cg-smoke --instance <instance> --max-route-requests 3 --max-three-request-routes 600
```

三请求有限候选池含义：

- 一请求和二请求可行路径全部进入候选池。
- 三请求路径最多额外加入 600 条。
- `root-lp-pricing-smoke` 只求一次初始 root LP 并扫描候选池。
- `root-finite-cg-smoke` 反复加入有限池内负 reduced-cost 列，直到池内没有未加入负列，或达到迭代上限。

## 5. 总体结果

| 指标 | 结果 |
|---|---:|
| 测试点数量 | 12 |
| 运行命令数量 | 24 |
| 失败命令数量 | 0 |
| 初始 root LP 发现负候选的实例 | 12 / 12 |
| 有限池 CG 清空负候选的实例 | 12 / 12 |
| 出现正人工列的实例 | 0 / 12 |

这个结果说明，当前程序在 12 个代表算例上都能完成“初始 root LP -> pricing 扫描 -> 负列加入 -> 有限池闭合”的受控流程。

## 6. 分组汇总

| 组别 | 样本数 | 平均候选路径数 | 初始负候选均值 | 初始最小 reduced cost 均值 | 有限池清空数 | 平均迭代数 | 平均加入列数 | 平均 finite CG 时间(ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| RC | 2 | 1113.0 | 516.0 | -64.70 | 2 / 2 | 5.0 | 255.0 | 174.0 |
| RC+ | 2 | 1379.5 | 613.5 | -77.75 | 2 / 2 | 5.0 | 292.0 | 172.0 |
| LL | 2 | 1010.5 | 107.0 | -81.55 | 2 / 2 | 4.0 | 116.5 | 728.5 |
| RC_reverse | 2 | 1212.5 | 532.5 | -84.18 | 2 / 2 | 6.0 | 298.0 | 169.5 |
| RC+_reverse | 2 | 1564.0 | 578.0 | -98.79 | 2 / 2 | 5.5 | 315.0 | 150.5 |
| LL_reverse | 2 | 1010.5 | 74.0 | -58.60 | 2 / 2 | 4.0 | 108.5 | 760.0 |

## 7. 明细结果

| 组别 | 实例 | 初始候选数 | 初始负候选数 | 初始最小 RC | finite CG 迭代 | 加入列数 | 剩余负候选 | final LP obj | 时间(ms) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| RC | AA30 | 1121 | 541 | -71.26 | 5 | 266 | 0 | 1250.9947 | 152 |
| RC | BB30 | 1105 | 491 | -58.14 | 5 | 244 | 0 | 1266.8773 | 196 |
| RC+ | AA30_rcplus | 1433 | 600 | -98.28 | 5 | 313 | 0 | 1134.0320 | 169 |
| RC+ | BB30_rcplus | 1326 | 627 | -57.21 | 5 | 271 | 0 | 1211.0087 | 175 |
| LL | lc101.txt | 1482 | 61 | -51.59 | 4 | 87 | 0 | 828.9369 | 299 |
| LL | lr101.txt | 539 | 153 | -111.50 | 4 | 146 | 0 | 1661.2523 | 1158 |
| RC_reverse | AA30_reverse | 1121 | 541 | -71.26 | 5 | 266 | 0 | 1250.9947 | 184 |
| RC_reverse | DD30_reverse | 1304 | 524 | -97.11 | 7 | 330 | 0 | 1333.1198 | 155 |
| RC+_reverse | AA30_rcplus_reverse | 1433 | 601 | -98.28 | 5 | 313 | 0 | 1134.0320 | 153 |
| RC+_reverse | DD30_rcplus_reverse | 1695 | 555 | -99.29 | 6 | 317 | 0 | 1185.3717 | 148 |
| LL_reverse | lc101.txt_reverse | 1482 | 52 | -46.18 | 5 | 61 | 0 | 828.9369 | 322 |
| LL_reverse | lr101.txt_reverse | 539 | 96 | -71.02 | 3 | 156 | 0 | 1661.2523 | 1198 |

## 8. 程序效果解读

初始 root LP 后，12 个代表实例全部存在负 reduced-cost 候选路径，说明当前 conservative seed columns 只是启动 RMP，并没有提前覆盖有限候选池内的改进列。pricing 扫描能够有效发现这些改进列。

有限池 CG 后，12 个实例全部达到 `restricted_finite_cg_pool_no_negative`，且 `remainingNegativeCandidates=0`。这说明在当前受控候选池中，RMP 求解、duals 提取、reduced-cost 扫描、负列加入与重复迭代这一整条链路是闭合的。

所有实例 `positiveArtificial=false`，说明初始种子列与后续列生成在这些测试点上都没有依赖正人工列来维持 LP 可行性。

从分组看，RC+ 和 RC+_reverse 的候选池规模与初始负候选数更高，符合时间窗放宽、容量增加后可行路径变多的预期。LL 与 LL_reverse 的运行时间明显高于 30 请求 RC 组，主要因为 LL 代表实例有 53 个请求、108 个节点。

## 9. 与论文结果的关系

论文 Table 2 和 Table 3 说明 Bi-Dy-SS 在完整算法和 pricing 策略层面优于 Fw-S/Bw-S。本文结果不能证明同样的时间加速，因为当前程序没有在这些 12 个样本上并列运行六种 labeling 策略，也没有完整 branch-cut-and-price。

本文能证明的是更基础的一步：程序已经能在论文六组口径的代表样本上稳定执行 root LP / pricing-only 有限池列生成，并且在三请求受控候选池中消除所有负 reduced-cost 候选。这为后续扩展到更完整的 pricing 策略对照、精确 pricing 和完整 BCP 复现实验提供了可验证的起点。

## 10. 输出文件

本次实验输出：

- `G:/bid/pdptw-bcp-java-gurobi/logs/paper12_results/paper12_root_lp.csv`
- `G:/bid/pdptw-bcp-java-gurobi/logs/paper12_results/paper12_finite_cg.csv`
- `G:/bid/pdptw-bcp-java-gurobi/logs/paper12_results/paper12_raw_results.json`

