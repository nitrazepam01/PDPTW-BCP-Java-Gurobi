# 双向标签实现说明

本文档解释本仓库中 bidirectional labeling 的实现逻辑、它和论文思想的对应关系，以及如何从实验输出判断“双向标签确实运行了”。

## 1. 为什么论文关心双向标签

PDPTW 的 pricing subproblem 可以看作带资源约束的 elementary shortest path problem。单向 forward labeling 从起点不断扩展到终点，容易产生大量 labels。双向标签把搜索拆成 forward 和 backward 两侧，在中间 meeting vertex 合并 partial labels，通常可以减少搜索压力。

pickup-and-delivery 结构的特殊困难在于：

- forward labeling 的强 dominance 依赖 delivery triangle inequality，简称 DTI。
- backward labeling 的强 dominance 依赖 pickup triangle inequality，简称 PTI。
- 同一个 reduced-cost matrix 通常不能同时满足 DTI 和 PTI。

论文的核心思想是：forward 和 backward 使用不同的 reduced-cost matrix，让 forward 侧满足 DTI，backward 侧满足 PTI，然后在 merge 时做 reduced-cost correction。

本仓库对应实现：

| 论文概念 | 代码 |
|---|---|
| forward reduced-cost matrix | `pricing/ReducedCostMatrices.forwardArcReducedCost(...)` |
| backward reduced-cost matrix | `pricing/ReducedCostMatrices.backwardArcReducedCost(...)` |
| DTI 检查 | `pricing/ForwardLabeler.satisfiesForwardDti(...)` |
| PTI 检查 | `pricing/BackwardLabeler.satisfiesBackwardPti(...)` |
| forward strong dominance | `pricing/Dominance.forwardStrongDominates(...)` |
| backward strong dominance | `pricing/Dominance.backwardStrongDominates(...)` |
| merge correction | `pricing/BidirectionalMerger`, `pricing/PricingContext.mergeCorrection(...)` |
| dynamic half-way | `pricing/DynamicHalfwayController`, `pricing/BidirectionalDynamicPricingSolver` |

## 2. Reduced-cost split

代码位置：

```text
src/main/java/org/pdptw/pricing/ReducedCostMatrices.java
```

核心常量：

```java
public static final double FORWARD_ALPHA = 1.0;
public static final double BACKWARD_ALPHA = 0.0;
```

含义：

- forward 方向把 request dual 分配到 pickup 侧。
- backward 方向把 request dual 分配到 delivery 侧。
- depot 使用 fleet dual。

单条 arc 的 reduced cost：

```text
c_ij
  - 0.5 * splitVertexDual(i, alpha)
  - 0.5 * splitVertexDual(j, alpha)
```

这种设计让完整 route 的 arc reduced-cost sum 和 direct route reduced cost 可以通过 merge correction 对齐。

## 3. Forward label

代码：

```text
src/main/java/org/pdptw/pricing/ForwardLabel.java
src/main/java/org/pdptw/pricing/ForwardLabeler.java
src/main/java/org/pdptw/pricing/LabelExtender.java
```

Forward label 记录：

| 字段 | 含义 |
|---|---|
| `lastVertexId` | 当前 path 末端 |
| `reducedCost` | partial path reduced cost |
| `time` | 当前服务开始时间 |
| `load` | 当前载重 |
| `completedMask` | 已 pickup 且已 delivery 的 requests |
| `openMask` | 已 pickup 但未 delivery 的 requests |
| `subsetRowRelevantVisitCounts` | SR cut pricing 状态 |
| `setOutflowStates` | set-outflow branch pricing 状态 |
| `vertexIds` | partial route |

Forward 扩展规则：

- pickup：request 不得已经 open 或 completed。
- delivery：request 必须已经 open。
- end depot：open mask 必须为空，且至少完成一个 request。
- 时间窗：到达后等待到 ready time，若超过 due time 则不可行。
- 容量：load 必须在 `[0, capacity]`。
- reduced cost：加入 forward arc reduced cost、SR adjustment、set-outflow adjustment。

Forward strong dominance 只有 DTI verified 时才允许：

```text
context.satisfiesForwardDti()
```

## 4. Backward label

代码：

```text
src/main/java/org/pdptw/pricing/BackwardLabel.java
src/main/java/org/pdptw/pricing/BackwardLabeler.java
src/main/java/org/pdptw/pricing/LabelExtender.java
```

Backward label 与 forward 对称，但从 end depot 向 start depot 前接节点：

| 字段 | 含义 |
|---|---|
| `firstVertexId` | 当前 suffix 的第一点 |
| `reducedCost` | suffix reduced cost |
| `time` | 该 suffix 允许前接的 latest service start |
| `load` | backward 方向的载重状态 |
| `completedMask` | backward 已配对完成的 requests |
| `openMask` | backward 已遇到 delivery 但还未遇到 pickup 的 requests |

Backward 扩展规则：

- delivery：request 不得已经 open 或 completed。
- pickup：request 必须已经 open。
- start depot：open mask 必须为空，且至少完成一个 request。
- 时间窗：计算前接点的 latest feasible service start。
- 容量和 reduced cost 同样在扩展时更新。

Backward strong dominance 只有 PTI verified 时才允许：

```text
context.satisfiesBackwardPti()
```

## 5. Static bidirectional merge

代码：

```text
src/main/java/org/pdptw/pricing/BidirectionalPricingSolver.java
src/main/java/org/pdptw/pricing/BidirectionalStaticPricingSolver.java
src/main/java/org/pdptw/pricing/BidirectionalMerger.java
```

流程：

```text
solve forward labels
solve backward labels
group backward labels by firstVertexId
for each forward label:
  find backward labels with same meeting vertex
  check compatibility
  reconstruct full route
  check full route feasibility
  compute corrected reduced cost
  audit against direct reduced cost
select best merge
```

兼容条件：

- forward 末端 vertex 等于 backward 首端 vertex。
- completed request masks 不重叠。
- forward time 不晚于 backward time。
- open requests 在 meeting vertex 处一致。

## 6. Merge correction

merge 前：

```text
rawReducedCost = forward.reducedCost + backward.reducedCost
```

但 forward/backward 使用了不同 reduced-cost split，因此 raw sum 不一定等于完整 route 的 direct reduced cost。代码用 correction 修正：

```text
mergedReducedCost = rawReducedCost + mergeCorrection
```

无 robust cuts 的基本 correction：

```text
intersection = forward.openMask & backward.openMask
symmetricDifference = (forward.openMask | backward.openMask) & ~intersection
correction =
    sum(requestDual over intersection)
  + 0.5 * sum(requestDual over symmetricDifference)
  + subsetRowMergeCorrection
```

有 robust cuts 时，correction 使用：

```text
DtiPtiRepair.robustMergeCorrection(...)
```

每次 merge 后都会做 direct reduced-cost audit。如果 merge reduced cost 和直接按完整 route 计算的 reduced cost 不一致，程序会抛出异常。这是防止双向 merge 公式写错的关键保护。

## 7. Dynamic half-way

代码：

```text
src/main/java/org/pdptw/pricing/DynamicHalfwayController.java
src/main/java/org/pdptw/pricing/BidirectionalDynamicPricingSolver.java
```

动态 half-way 的目的是不固定切分点，而是在搜索过程中根据 forward/backward 未处理 label 数动态选择扩展方向。

`DynamicHalfwayController.chooseDirection(...)`：

- 两边都无未处理 label：结束。
- forward queue 空：处理 backward。
- backward queue 空：处理 forward。
- 否则处理未处理 label 更少的一侧。

`processForward()` 和 `processBackward()` 每处理一个 label 后，会更新：

- processed forward/backward labels。
- generated forward/backward labels。
- unprocessed forward/backward labels。
- backward lower bound。
- forward upper bound。

最终 `PricingResult.Stats.ofDynamic(...)` 会把这些计数带到 BCP 结果中。

## 8. Dominance cleanup

代码：

```text
BidirectionalDynamicPricingSolver.pruneDominatedQueuedLabels()
```

逻辑：

- half-way bounds 改变时触发 dominance cleanup。
- forward cleanup 要求 forward DTI verified。
- backward cleanup 要求 backward PTI verified。
- 当前有 subset-row cuts 时强 dominance cleanup 会关闭，因为 SR state 会影响 dominance。

被清理的 labels 放入 pruned label list。最后 merge 时，代码会把 partial、complete、pruned labels 都纳入 merge 候选，保证 reduced-cost audit 不因为中途剪枝而丢失必要检查。

## 9. 在 BCP 中如何接入双向标签

代码：

```text
src/main/java/org/pdptw/cli/RunBcp.java
src/main/java/org/pdptw/cli/BcpRunner.java
src/main/java/org/pdptw/branch/BranchAndPriceSolver.java
src/main/java/org/pdptw/branch/LabelingNodePricingBackend.java
```

命令：

```bat
run.bat bcp --instance logs\bcp12_inputs\AA30_n6 --pricing bidir-dynamic --cuts sr --trace
```

调用链：

```text
Main.run(...)
  -> RunBcp.main(...)
  -> RunBcp.runBenchmarkBcp(...)
  -> BcpRunner.withBenchmarkLabelingAndSubsetRowSeparation(...)
  -> BranchAndPriceSolver.withBenchmarkLabelingAndSubsetRowSeparation(...)
  -> BranchAndPriceSolver.solve(...)
  -> solveNodeRelaxation(...)
  -> LabelingNodePricingBackend.price(...)
  -> BidirectionalDynamicPricingSolver.price(...)
```

关键点：

- benchmark text instance 必须显式指定 `--pricing bidir-dynamic`。
- route-universe pricing 对 full-size benchmark 被拒绝。
- `LabelingNodePricingBackend` 要求 `PricingResult.exact() == true`。
- branch rows 的 dual 会被转入 `PricingContext`：
  - vehicle-count branch dual 调整 fleet dual。
  - set-outflow branch dual 转成 `SetOutflowPricingRule`。

## 10. 如何证明双向标签实际运行

在 BCP 实验结果中看这些字段。注意：聚合文件 `logs/bcp12_results/bcp12_sr_summary.csv` 有 `pricing` 列；原始 `run.bat bcp` 输出遵循 `BenchmarkCsv` schema，没有单独的 `pricing` 列，pricing mode 需要由运行命令确认。

```text
pricing
forwardLabels
backwardLabels
dominatedLabels
pricingCalls
```

例如 `logs/bcp12_results/bcp12_sr_summary.csv` 中：

```text
AA30_n6,bidir-dynamic,...,pricingCalls=2,forwardLabels=150,backwardLabels=162,dominatedLabels=53
```

判定逻辑：

- `pricing=bidir-dynamic` 表示选择了动态双向标签 pricing mode。
- `forwardLabels > 0` 表示 forward side 扩展过 labels。
- `backwardLabels > 0` 表示 backward side 扩展过 labels。
- `dominatedLabels >= 0` 表示 dominance / cleanup 统计由 labeling backend 返回。
- 如果走 route-universe backend，这些 label counters 会是 `NA` 或不可用。

因此，12 个 BCP pricing-loop 小实验中所有行都有 forward/backward label counters，是“双向标签确实进入 BCP pricing loop”的直接程序证据。

## 11. 当前双向标签复现强度

可以认为已经复现得比较好的部分：

- forward/backward 两套 reduced-cost matrix。
- DTI/PTI gate。
- forward/backward label extension。
- bidirectional merge compatibility。
- merge reduced-cost correction and audit。
- dynamic half-way controller。
- dynamic bidirectional pricing 统计。
- BCP node pricing backend 对 `bidir-dynamic` 的接入。

需要继续加强的部分：

- full-size benchmark 的 exact pricing 性能。
- full-size BCP 长时间运行稳定性。
- robust cut candidate generation 的 benchmark-scale 实现。
- 更多 labeling 策略对照实验，例如 forward、backward、bidir-static、bidir-dynamic 同口径比较。

## 12. 给论文复现报告的建议表述

建议写：

> 本程序实现了 forward、backward、bidirectional static 和 bidirectional dynamic 四类 exact labeling pricing，并在 12 个派生 6-request BCP pricing-loop 小实验中确认 `bidir-dynamic` 实际参与 BCP pricing。输出的 forward/backward label counters 说明双向标签不是停留在独立 pricing demo，而是已经嵌入 branch-and-price 节点列生成流程。

不要写：

> 本程序已在论文 full-size 算例上完全复现双向标签 BCP 性能。

因为当前 full-size `AA30` exact-labeling BCP probe 600 秒内没有完成，完整 220 个实例仍是后续工作。
