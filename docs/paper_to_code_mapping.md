# 论文算法到代码实现映射

本文档把 `Bidirectional labeling in column-generation algorithms for pickup-and-delivery problems` 中与 PDPTW branch-cut-and-price 复现相关的概念映射到当前 Java/Gurobi 代码。它不是论文逐段翻译，而是供读者审查“论文里的算法部件在仓库哪里实现、实现到了什么范围”的索引。

## 1. 总览

| 论文/算法部件 | 当前代码状态 | 关键代码 |
|---|---|---|
| PDPTW 数据模型 | 已实现 | `core/Instance.java`, `core/Request.java`, `core/Vertex.java` |
| 路线可行性检查 | 已实现 | `core/RouteChecker.java` |
| RMP / master LP | 已实现，使用 Gurobi | `master/GurobiRmp.java` |
| route column | 已实现 | `master/RouteColumn.java`, `master/ColumnPool.java` |
| artificial columns | 已实现 | `master/ArtificialColumnFactory.java` |
| dual solution | 已实现 | `master/DualSolution.java` |
| reduced-cost matrices | 已实现 | `pricing/ReducedCostMatrices.java` |
| forward labeling | 已实现 | `pricing/ForwardLabeler.java`, `pricing/ForwardLabel.java` |
| backward labeling | 已实现 | `pricing/BackwardLabeler.java`, `pricing/BackwardLabel.java` |
| label extension | 已实现 | `pricing/LabelExtender.java` |
| DTI / PTI dominance gate | 已实现 | `pricing/Dominance.java`, `cuts/DtiPtiRepair.java` |
| bidirectional static merge | 已实现 | `pricing/BidirectionalPricingSolver.java`, `pricing/BidirectionalMerger.java` |
| bidirectional dynamic half-way | 已实现 | `pricing/BidirectionalDynamicPricingSolver.java`, `pricing/DynamicHalfwayController.java` |
| subset-row cuts | 已实现受控路径 | `cuts/SubsetRowCutRow.java`, `cuts/SubsetRowCutSeparator.java`, `cuts/SRPricingAdjuster.java` |
| robust cuts | row 与 pricing repair 已实现，candidate generation tiny-only | `cuts/RobustCutRow.java`, `cuts/RobustCutCandidateGenerator.java`, `cuts/DtiPtiRepair.java` |
| branching | 已实现 vehicle-count 与 set-outflow | `branch/VehicleCountBrancher.java`, `branch/SetOutflowBrancher.java` |
| BCP 主循环 | 已实现受控 pricing-loop 路径 | `branch/BranchAndPriceSolver.java` |
| benchmark CLI | 已实现受控实验入口 | `cli/RunBcp.java`, `cli/RunBenchmarkSubinstance.java` |

状态解释：

- “已实现”表示代码中有实际可运行实现，并有测试或实验路径覆盖。
- “受控路径”表示可以用于 tiny 或 12 个受控小实验，但尚未证明能支撑论文 220 个 full-size benchmark。
- “tiny-only”表示实现依赖 `BruteForcePricingOracle` 或显式请求数上限，不适合 full-size benchmark。

## 2. 数据模型与算例读取

### 2.1 PDPTW instance

代码：

- `src/main/java/org/pdptw/core/Instance.java`
- `src/main/java/org/pdptw/core/Request.java`
- `src/main/java/org/pdptw/core/Vertex.java`

对应论文概念：

- pickup vertex 和 delivery vertex。
- start depot 和 end depot。
- request pair。
- vehicle capacity。
- travel cost / travel time。
- time windows。

实现要点：

- `Instance` 保存 `nRequests`、`vehicleCapacity`、`maxVehicles`、`startDepotId`、`endDepotId`、vertex map、request map、travel matrices。
- `Request` 保存 request id、pickup vertex id、delivery vertex id、demand。
- `Vertex` 区分 `DEPOT_START`、`DEPOT_END`、`PICKUP`、`DELIVERY`，并保存坐标、时间窗、服务时间、需求。

### 2.2 RC / LL benchmark reader

代码：

- `src/main/java/org/pdptw/io/BenchmarkInstanceReader.java`

对应论文概念：

- Ropke-Cordeau RC 格式。
- Li-Lim LL 格式。
- reverse / generated RC+ 样本作为实验输入。

实现要点：

- `BenchmarkInstanceReader.read(...)` 自动判断文件是 tiny JSON、RC 5-field header，还是 LL 3-field header。
- `readRopkeCordeau(...)` 读取 RC 文件，使用欧氏距离生成 travel matrix。
- `readLiLim(...)` 读取 LL 文件，按 pickup/delivery pair 重映射 request id，并增加 end depot。
- `paperGroup(...)` 用文件名判断 `RC`、`RC_reverse`、`LL`、`LL_reverse` 等组别。

边界：

- reader 支持把 benchmark 文件读成内部 `Instance`。
- reader 本身不保证 full-size BCP 可在合理时间内完成。

### 2.3 路线可行性检查

代码：

- `src/main/java/org/pdptw/core/Route.java`
- `src/main/java/org/pdptw/core/RouteChecker.java`

对应论文概念：

- elementarity。
- pickup-before-delivery precedence。
- capacity feasibility。
- time-window feasibility。
- depot shape。

实现要点：

- `RouteChecker.check(...)` 检查起终点 depot、未知点、重复客户、delivery-before-pickup、容量、时间窗和未闭合 request。
- `Route.cost(instance)` 计算路线 travel cost。
- `Route.servedRequests(instance)` 计算完成服务的 requests。

用途：

- pricing merge 后做 feasibility audit。
- brute-force tiny oracle 评估 route。
- route column 生成时记录 served requests 和 cost。

## 3. Master Problem / RMP

### 3.1 RMP 建模

代码：

- `src/main/java/org/pdptw/master/GurobiRmp.java`

对应论文概念：

- set-covering-style route-based master。
- 与论文常见 set-partitioning 口径相比，当前代码的 request cover rows 使用 `>= 1`，这是一个建模差异。
- 每个 request 至少被覆盖一次。
- vehicle count / fleet limit。
- LP relaxation。

实现要点：

- 构造函数 `GurobiRmp(Instance, double)` 创建 Gurobi model。
- 对每个 request 建立 `cover_i >= 1`。
- 建立 `fleet_limit <= maxVehicles`。
- 默认关闭 Gurobi 输出、单线程、关闭 presolve，便于可重复调试。
- 初始化时加入 artificial columns，保证 RMP 初始可行。

代码位置：

```text
GurobiRmp.java
  constructor GurobiRmp(Instance, double)
  solveLp()
  dualSolution()
  addColumn(...)
  addCutRow(...)
```

### 3.2 RouteColumn

代码：

- `src/main/java/org/pdptw/master/RouteColumn.java`
- `src/main/java/org/pdptw/master/ColumnPool.java`

对应论文概念：

- master 中的一列是一条可行 vehicle route。
- route column 覆盖一组 requests，目标系数是 route cost。

实现要点：

- `RouteColumn.fromRoute(...)` 将 `Route` 转换成 RMP column。
- `servedRequests()` 是 coverage rows 的系数来源。
- `fleetCoefficient()` 对真实 route 为 1，对 artificial column 为 0。
- `signature()` 用于列去重，避免重复加入相同路线。
- `ColumnPool.add(...)` 按 signature 去重。

### 3.3 Artificial columns

代码：

- `src/main/java/org/pdptw/master/ArtificialColumnFactory.java`

对应论文概念：

- 初始 RMP 可能没有足够 route columns，需要人工列保证 LP 可行。

实现要点：

- `forInstance(instance)` 给每个 request 创建一个 artificial column。
- artificial column 覆盖一个 request，成本为大罚值。
- 实验文档中 `positiveArtificial=false` 表示最终 LP 解没有依赖正人工列。

### 3.4 Dual extraction and cut duals

代码：

- `src/main/java/org/pdptw/master/DualSolution.java`
- `src/main/java/org/pdptw/master/GurobiRmp.java`

对应论文概念：

- pricing subproblem 使用 master duals 构造 reduced cost。
- cuts 和 branch rows 的 dual 也要反映进 pricing。

实现要点：

- `GurobiRmp.dualSolution()` 从 request cover rows 和 fleet row 取 Gurobi `Pi`。
- `DualSolution.directReducedCost(...)` 使用统一约定：

```text
rc(route) = route_cost - fleetDual - sum(requestDual_i for served requests i)
```

- `GurobiRmp.cutDuals()` 返回 active cut rows 的 raw Pi 和 pricing dual。
- `robustCutsFromDuals()` 和 `subsetRowCutsFromDuals()` 把 master cut rows 转成 pricing 使用的 cut objects。

## 4. Reduced-Cost Matrices

代码：

- `src/main/java/org/pdptw/pricing/ReducedCostMatrices.java`
- `src/main/java/org/pdptw/pricing/PricingContext.java`

对应论文概念：

- 为 forward 与 backward labeling 构造不同 reduced-cost matrix。
- 通过 alpha split 把 request dual 分配到 pickup/delivery 端。
- forward 和 backward 使用不同 alpha，从而支持 DTI/PTI 方向要求。

实现要点：

- `ReducedCostMatrices.FORWARD_ALPHA = 1.0`。
- `ReducedCostMatrices.BACKWARD_ALPHA = 0.0`。
- `splitVertexDual(vertexId, alpha)`：
  - depot 使用 fleet dual。
  - pickup 使用 `alpha * requestDual`。
  - delivery 使用 `(1 - alpha) * requestDual`。
- `arcReducedCost(from, to, alpha)`：

```text
travelCost(from,to)
  - 0.5 * splitVertexDual(from, alpha)
  - 0.5 * splitVertexDual(to, alpha)
```

- `forwardArcReducedCost(...)` 使用 alpha=1。
- `backwardArcReducedCost(...)` 使用 alpha=0。

`PricingContext` 在这个基础上叠加：

- robust cut arc price。
- subset-row cut pricing state。
- set-outflow branch row pricing state。
- merge correction。

## 5. Forward Labeling

代码：

- `src/main/java/org/pdptw/pricing/ForwardLabel.java`
- `src/main/java/org/pdptw/pricing/ForwardLabeler.java`
- `src/main/java/org/pdptw/pricing/LabelExtender.java`
- `src/main/java/org/pdptw/pricing/ForwardPricingSolver.java`

对应论文概念：

- 从 start depot 正向扩展。
- label 保存当前节点、reduced cost、time、load、open/completed requests。
- 扩展 pickup / delivery / end depot。
- 使用 DTI 成立时的 strong dominance。

### 5.1 Label state

`ForwardLabel` 字段：

| 字段 | 作用 |
|---|---|
| `lastVertexId` | 当前 label 末端 vertex |
| `reducedCost` | 当前 partial path reduced cost |
| `time` | 当前服务开始时间 |
| `load` | 当前载重 |
| `completedMask` | 已完成 pickup-delivery 的 request 集合 |
| `openMask` | 已 pickup 但未 delivery 的 request 集合 |
| `subsetRowRelevantVisitCounts` | SR cut pricing resource |
| `setOutflowStates` | set-outflow branch pricing resource |
| `vertexIds` | partial route |

### 5.2 扩展逻辑

`LabelExtender.forwardExtend(context, label, nextVertexId)` 负责：

- 禁止回到 start depot。
- pickup：
  - request 不能已经 open 或 completed。
  - open mask 加入 request。
- delivery：
  - request 必须已经 open。
  - open mask 移除 request，completed mask 加入 request。
- end depot：
  - open mask 必须为空。
  - 至少服务过一个 request。
- 时间：
  - `arrival = label.time + serviceTime(current) + travelTime(current,next)`。
  - `time = max(arrival, next.readyTime)`。
  - 若超过 `next.dueTime` 则不可行。
- 容量：
  - 更新 load，必须在 `[0, vehicleCapacity]`。
- reduced cost：
  - 加 `context.forwardArcReducedCost(...)`。
  - 加 SR transition adjustment。
  - 加 set-outflow transition adjustment。

### 5.3 搜索逻辑

`ForwardLabeler.solve(PricingContext)`：

- 从 start depot 创建初始 label。
- 用 queue 广度式扩展。
- 当 label open mask 为空且 completed 非空时尝试连到 end depot。
- 对每个 request：
  - 未 open / 未 completed 则尝试 pickup。
  - 已 open 则尝试 delivery。
- 返回 best complete label。

### 5.4 Dominance

`ForwardLabeler` 可以用 strong dominance，但要求 DTI verified：

- `context.satisfiesForwardDti()`。
- `Dominance.forwardStrongDominates(...)`。

dominance 条件核心：

- 同一 `lastVertexId`。
- reduced cost 不差。
- time 不晚。
- completed mask 是子集。
- open mask 是子集。
- SR state 和 set-outflow state 一致。

## 6. Backward Labeling

代码：

- `src/main/java/org/pdptw/pricing/BackwardLabel.java`
- `src/main/java/org/pdptw/pricing/BackwardLabeler.java`
- `src/main/java/org/pdptw/pricing/LabelExtender.java`
- `src/main/java/org/pdptw/pricing/BackwardPricingSolver.java`

对应论文概念：

- 从 end depot 反向扩展。
- backward 的 open/completed 语义对称处理。
- 使用 PTI 成立时的 strong dominance。

### 6.1 Label state

`BackwardLabel` 字段与 `ForwardLabel` 对称：

| 字段 | 作用 |
|---|---|
| `firstVertexId` | 当前 backward partial path 的第一点 |
| `reducedCost` | partial reduced cost |
| `time` | 该 partial suffix 最早前接节点可用的 latest service start |
| `load` | backward 语义下的载重状态 |
| `completedMask` | backward 已配对完成的 request |
| `openMask` | backward 已遇到 delivery 但未遇到 pickup 的 request |
| `subsetRowRelevantVisitCounts` | SR pricing resource |
| `setOutflowStates` | branch pricing resource |
| `vertexIds` | partial suffix route |

### 6.2 扩展逻辑

`LabelExtender.backwardExtend(context, label, previousVertexId)` 负责：

- 禁止前接 end depot。
- delivery：
  - request 不能 already open / completed。
  - open mask 加入 request。
- pickup：
  - request 必须 open。
  - open mask 移除，completed 加入。
- start depot：
  - open mask 必须为空。
  - completed 不能为空。
- 时间：
  - 计算可前接的 `latestServiceStart`。
  - 若早于 ready time 则不可行。
- 容量：
  - backward 方向按 `label.load - previous.demand()` 更新。
- reduced cost：
  - 加 `context.backwardArcReducedCost(...)`。
  - 加 SR backward transition。
  - 加 set-outflow backward transition。

### 6.3 PTI dominance

`BackwardLabeler.satisfiesBackwardPti(...)` 检查 pickup triangle inequality。`Dominance.backwardStrongDominates(...)` 只有 PTI verified 时允许使用。

核心条件：

- 同一 `firstVertexId`。
- reduced cost 不差。
- backward time 不小于被支配 label 的 time。
- completed/open mask 子集关系。
- SR state 和 set-outflow state 一致。

## 7. Bidirectional Labeling and Merge

### 7.1 Static bidirectional pricing

代码：

- `src/main/java/org/pdptw/pricing/BidirectionalPricingSolver.java`
- `src/main/java/org/pdptw/pricing/BidirectionalStaticPricingSolver.java`

对应论文概念：

- 分别生成 forward labels 和 backward labels。
- 对 meeting vertex 相同且 compatible 的 label pair 做 merge。
- 从 merge route 中选 best reduced-cost route。

实现流程：

```text
BidirectionalPricingSolver.solve(context)
  -> new ForwardLabeler().solve(context)
  -> new BackwardLabeler().solve(context)
  -> mergeAll(context, forward, backward)
  -> bestMerge(context, merges)
```

`mergeAll(...)` 先按 `BackwardLabel.firstVertexId` 建索引，然后遍历所有 forward labels，找相同 meeting vertex 的 backward candidates。

### 7.2 Merge compatibility

代码：

- `src/main/java/org/pdptw/pricing/BidirectionalMerger.java`

`BidirectionalMerger.compatible(instance, forward, backward)` 检查：

- `forward.lastVertexId == backward.firstVertexId`。
- completed masks 不相交。
- `forward.time <= backward.time`。
- open requests 在 merge vertex 处兼容。

open requests 兼容规则：

- merge vertex 是 pickup：backward open mask 应等于 forward open mask 移除该 request。
- merge vertex 是 delivery：forward open mask 应等于 backward open mask 移除该 request。
- 其他点：forward open mask 与 backward open mask 相同。

### 7.3 Route reconstruction

`BidirectionalMerger.reconstructRoute(forward, backward)`：

```text
route = forward.vertexIds + backward.vertexIds[1..]
```

然后 `RouteChecker` 再做完整 feasibility audit。

### 7.4 Merge reduced-cost correction

论文中双向 reduced-cost merge 需要修正 open requests 的 dual 计数。代码中：

- 无 robust cuts 时使用 `BidirectionalMerger.defaultMergeCorrection(...)` 或 `PricingContext.mergeCorrection(...)`。
- 有 robust cuts 时使用 `DtiPtiRepair.robustMergeCorrection(...)`。
- 有 subset-row cuts 时叠加 `PricingContext.subsetRowMergeCorrection(...)`。

无 robust 的基本修正：

```text
intersection = forward.openMask & backward.openMask
symmetricDifference = (forward.openMask | backward.openMask) & ~intersection
correction = sum(requestDual over intersection)
           + 0.5 * sum(requestDual over symmetricDifference)
           + subsetRowMergeCorrection
```

merge 后会用 direct reduced cost audit 校验：

```text
mergedReducedCost == context.directReducedCost(reconstructedRoute)
```

如果超过容差，抛出异常。

## 8. Dynamic Bidirectional Labeling

代码：

- `src/main/java/org/pdptw/pricing/BidirectionalDynamicPricingSolver.java`
- `src/main/java/org/pdptw/pricing/DynamicHalfwayController.java`

对应论文概念：

- 动态调整 bidirectional half-way point。
- 根据 forward/backward 未处理 label 数决定下一步扩展方向。
- 用 half-way bounds 限制扩展。

### 8.1 入口

`BidirectionalDynamicPricingSolver.price(context)`：

```text
Result result = solve(context)
convert result.merges() to RouteColumn
return PricingResult.exact(..., Stats.ofDynamic(...))
```

输出统计包含：

- generated forward labels。
- generated backward labels。
- complete labels。
- merges。
- dominated labels。
- final dynamic half-way snapshot。
- dominance cleanup counters。

这也是 BCP pricing-loop 小实验 CSV 中 `forwardLabels` / `backwardLabels` / `dominatedLabels` 的来源。

### 8.2 DynamicSearch 初始化

`BidirectionalDynamicPricingSolver.DynamicSearch`：

- 建 forward priority queue，按 time 升序。
- 建 backward priority queue，按 time 降序。
- 初始化 start forward label 和 sink backward label。
- `DynamicHalfwayController` 初始 lower/upper bounds 分别来自 start depot ready time 与 end depot due time。

### 8.3 方向选择

`DynamicHalfwayController.chooseDirection(...)`：

- 如果两个方向都没有未处理 label，结束。
- 如果某方向为空，处理另一方向。
- 否则选择未处理 label 数较少的一侧。

这是一种动态 half-way 控制策略，用于避免静态切分点导致一侧 label 爆炸。

### 8.4 Forward / backward processing

`processForward()`：

- 从 forward queue 取 time 最小 label。
- 若 label time 不超过 current forward upper bound，则扩展。
- 可扩展到 end depot、未 pickup request 的 pickup、open request 的 delivery。
- 调用 controller 更新 processed/generated/unprocessed 计数和 half-way bound。

`processBackward()`：

- 从 backward queue 取 time 最大 label。
- 若 label time 大于 current backward lower bound，则扩展。
- 可扩展到 start depot、未 open request 的 delivery、open request 的 pickup。
- 调用 controller 更新状态。

### 8.5 Dynamic dominance cleanup

当 half-way bounds 更新时：

```text
triggerDominanceCleanupIfBoundsChanged(...)
  -> pruneDominatedQueuedLabels()
```

dominance cleanup：

- 只在对应方向 DTI/PTI verified 时启用。
- 当前有 subset-row cuts 时关闭强 dominance cleanup，因为 SR 状态会改变 dominance 条件。
- 被剪枝 label 会保留在 pruned label list，以便最后 merge 仍可审计。

### 8.6 最终 merge

动态搜索结束后，代码把 partial、complete、pruned labels 合并起来做 meeting-vertex merge：

```text
allForwardLabels(partial, complete, pruned)
allBackwardLabels(partial, complete, pruned)
merger.merge(context, forward, backward)
bestMerge(context, merges)
```

这保证动态过程中被 bounds/dominance 从 queue 移出的 labels 仍可参与最终可行 merge 审计。

## 9. DTI / PTI and Robust Cut Repair

代码：

- `src/main/java/org/pdptw/cuts/DtiPtiRepair.java`
- `src/main/java/org/pdptw/pricing/PricingContext.java`

对应论文概念：

- forward strong dominance 要求 DTI。
- backward strong dominance 要求 PTI。
- DTI 与 PTI 不能直接由同一 reduced-cost matrix 同时保证。
- 论文使用不同方向 cost matrix 支持两侧 strong dominance。
- robust cuts 会改变 arc reduced cost，需要 repair。

实现要点：

- `ForwardLabeler.satisfiesForwardDti(...)` 检查 DTI。
- `BackwardLabeler.satisfiesBackwardPti(...)` 检查 PTI。
- `DtiPtiRepair.forwardTheta(...)` 计算 forward DTI violation 修正量。
- `DtiPtiRepair.backwardTheta(...)` 计算 backward PTI violation 修正量。
- `repairForwardDti(...)` 对 pickup/delivery incident arcs 施加 shift。
- `repairBackwardPti(...)` 做对称修正。
- `PricingContext.forwardArcReducedCost(...)` / `backwardArcReducedCost(...)` 在 robust cuts 存在时使用 repaired matrix。
- `PricingContext.mergeCorrection(...)` 在 robust cuts 存在时使用 `DtiPtiRepair.robustMergeCorrection(...)`。

边界：

- DTI/PTI repair 与 pricing context 已实现。
- benchmark-scale robust candidate separation 尚未完成，因此 CLI 对 benchmark RC/LL 拒绝 `--cuts robust`。

## 10. Subset-Row Cuts

代码：

- `src/main/java/org/pdptw/cuts/SubsetRowCut.java`
- `src/main/java/org/pdptw/cuts/SubsetRowCutRow.java`
- `src/main/java/org/pdptw/cuts/SubsetRowCutSeparator.java`
- `src/main/java/org/pdptw/cuts/SRPricingAdjuster.java`
- `src/main/java/org/pdptw/cuts/SubsetRowResourceState.java`
- `src/main/java/org/pdptw/pricing/PricingContext.java`

对应论文概念：

- subset-row inequalities。
- master row 中 route coefficient 取决于 route 服务的 request set。
- pricing 中要用 resource state 记录访问相关 request 的次数，并在 coefficient 变化时调整 reduced cost。

### 10.1 Master row

`SubsetRowCutRow`：

- `sense() = LESS_EQUAL`。
- `rhs()` 来自 `SubsetRowCut.rhs()`。
- `coefficient(instance, column)` 调用 `SubsetRowCut.coefficientForRoute(...)`。
- `toPricingCut(rawPi)` 把 Gurobi dual 转成 pricing cut。

### 10.2 Separation

`SubsetRowCutSeparator.violatedL2Triples(...)`：

- 枚举所有 request 三元组。
- 构造 `l=2` subset-row cut。
- 计算当前 LP solution activity。
- 如果 activity > rhs + tolerance，则返回 violated row。

边界：

- 逻辑已实现。
- full-size 下枚举所有三元组可能昂贵，当前受控 BCP pricing-loop 小实验 n=6 可用。

### 10.3 Pricing adjustment

`SRPricingAdjuster`：

- `pricingAdjustmentForCoefficient(cut, coefficient) = -sigma * coefficient`。
- forward 只在 pickup relevant request 时更新 SR state。
- backward 只在 delivery relevant request 时更新 SR state。
- merge 时用 `mergeCorrection(...)` 修正 forward/backward partial coefficient 与 direct route coefficient 的差异。

`PricingContext` 把 SR adjustment 接入：

- `forwardSubsetRowTransition(...)`
- `backwardSubsetRowTransition(...)`
- `subsetRowMergeCorrection(...)`
- `directReducedCost(...)`

## 11. Robust Cuts

代码：

- `src/main/java/org/pdptw/cuts/RobustCut.java`
- `src/main/java/org/pdptw/cuts/RobustCutRow.java`
- `src/main/java/org/pdptw/cuts/RobustCutSeparator.java`
- `src/main/java/org/pdptw/cuts/RobustCutCandidateGenerator.java`
- `src/main/java/org/pdptw/cuts/RoundedCapacityCut.java`
- `src/main/java/org/pdptw/cuts/TwoPathCut.java`
- `src/main/java/org/pdptw/cuts/DtiPtiRepair.java`

对应论文概念：

- robust cuts 会给 arcs 增加 pricing dual effect。
- bidirectional labeling 下 robust cuts 需要维护 DTI/PTI compatible reduced-cost matrix。

### 11.1 Robust master row

`RobustCutRow`：

- 保存 row sense、rhs、arc coefficients。
- `coefficient(instance, column)` 对 route 中每条 arc 累加 coefficient。
- `toPricingCut(lpDualValue)` 生成 pricing 用的 `RobustCut`。

### 11.2 Robust separation

`RobustCutSeparator.violatedRows(...)`：

- 接受候选 robust cut rows。
- 计算当前 LP solution activity。
- 对违反 row 去重后返回。

### 11.3 Tiny-only candidate generation

`RobustCutCandidateGenerator`：

- `MAX_EXACT_REQUESTS = 6`。
- `violatedTwoPathRequestSetRows(...)` 依赖 `BruteForcePricingOracle().enumerate(instance)`。
- `violatedRoundedCapacityRequestSetRows(...)` 枚举 request subsets。

边界：

- robust cut row、pricing repair、separator 已实现。
- candidate generator 明确 tiny-only。
- `RunBcp` 对 benchmark RC/LL 文本实例拒绝 `--cuts robust` / `--cuts robust,sr`。

## 12. Branching

代码：

- `src/main/java/org/pdptw/branch/BranchAndPriceSolver.java`
- `src/main/java/org/pdptw/branch/VehicleCountBrancher.java`
- `src/main/java/org/pdptw/branch/VehicleCountConstraint.java`
- `src/main/java/org/pdptw/branch/SetOutflowBrancher.java`
- `src/main/java/org/pdptw/branch/SetOutflowConstraint.java`
- `src/main/java/org/pdptw/branch/BranchMasterRow.java`
- `src/main/java/org/pdptw/pricing/SetOutflowPricingRule.java`
- `src/main/java/org/pdptw/branch/LabelingNodePricingBackend.java`

对应论文概念：

- branch-cut-and-price 中不能直接对 arc/route 变量做破坏 pricing 结构的分支。
- 本实现提供 vehicle-count branching 和 set-outflow branching。

### 12.1 Branching order

`BranchAndPriceSolver.chooseBranch(...)`：

1. `VehicleCountBrancher.branch(...)`
2. `SetOutflowBrancher.branch(...)`

### 12.2 Vehicle-count branching

`VehicleCountBrancher`：

- 计算 `sum(lambda_r * fleetCoefficient_r)`。
- 若非整数，则产生：
  - `sum_lambda <= floor(value)`
  - `sum_lambda >= ceil(value)`

`VehicleCountConstraint.expression()` 输出形如：

```text
sum_lambda <= k
sum_lambda >= k
```

pricing 接入：

- `BranchMasterRow.coefficient(...)` 对真实 route 返回 fleet coefficient。
- `LabelingNodePricingBackend` 把 vehicle branch row dual 合并进 fleet dual。

### 12.3 Set-outflow branching

`SetOutflowBrancher`：

- 从当前 LP solution 的 served requests 构造候选 request sets。
- 对每个 candidate set 计算 `x(delta+(U))`。
- 若 fractional，则产生：
  - `x(delta+(U)) <= floor(value)`
  - `x(delta+(U)) >= ceil(value)`

benchmark safeguard：

- `SetOutflowBrancher(double tolerance, int maxRequestSetSize)`。
- CLI 参数 `--max-set-branch-size` 控制最大集合大小。
- 12 个 BCP pricing-loop 小实验使用 `--max-set-branch-size 3`。

pricing 接入：

- `BranchMasterRow` 把 branch constraint 变成 RMP row。
- `LabelingNodePricingBackend.pricingContextWithSupportedBranchRows(...)` 将 set-outflow branch row dual 转成 `SetOutflowPricingRule`。
- `SetOutflowPricingRule` 在 forward/backward extension 中维护 inside/outside 状态，并在跨出集合时给 reduced cost 加 branch dual adjustment。

## 13. Branch-Cut-and-Price Main Loop

代码：

- `src/main/java/org/pdptw/branch/BranchAndPriceSolver.java`
- `src/main/java/org/pdptw/branch/NodeQueue.java`
- `src/main/java/org/pdptw/branch/BranchNode.java`
- `src/main/java/org/pdptw/branch/LabelingNodePricingBackend.java`
- `src/main/java/org/pdptw/branch/RouteUniverseNodePricingBackend.java`
- `src/main/java/org/pdptw/branch/HybridNodePricingBackend.java`

### 13.1 Solver configuration

`BranchAndPriceSolver` 关键参数：

| 参数 | 默认/用途 |
|---|---|
| `DEFAULT_MAX_NODES = 100` | 最大处理节点数 |
| `DEFAULT_MAX_COLUMN_GENERATION_ITERATIONS = 100` | 每个节点最大 CG 迭代 |
| `DEFAULT_MAX_SET_OUTFLOW_BRANCH_SET_SIZE = 3` | benchmark set-outflow 候选集合上限 |
| `DEFAULT_TOLERANCE = 1e-7` | 数值容差 |

工厂方法：

- `withRootLabeling(...)`：tiny/root labeling path，可混合 route-universe fallback。
- `withBenchmarkLabeling(...)`：benchmark text BCP exact labeling path。
- `withBenchmarkLabelingAndSubsetRowSeparation(...)`：benchmark text BCP + SR separation。

### 13.2 Node loop

`BranchAndPriceSolver.solve(instance, routeUniverse, activeCutRows)`：

```text
queue.add(root)
while queue not empty and processedNodes < maxNodes:
  node = queue.poll()
  relaxation = solveNodeRelaxation(instance, node, universe, cutRows)
  if positive artificial:
    prune
  else if lower bound >= incumbent:
    prune by bound
  else if integral:
    update incumbent
  else:
    chooseBranch(...)
    add children
```

输出状态：

- `optimal_tiny_branch_tree`
- `optimal_benchmark_branch_tree`
- `node_limit`
- `no_incumbent`

### 13.3 Node relaxation and column generation

`solveNodeRelaxation(...)`：

1. 创建 `GurobiRmp`。
2. 加 active cut rows。
3. 加 inherited branch rows。
4. 用 seed route universe 补充满足 branch feasibility 的初始列。
5. 重复：
   - solve node LP。
   - separate cuts。
   - extract duals。
   - build `ReducedCostMatrices`。
   - build `PricingContext` with active robust/SR cuts。
   - call `pricingBackend.price(...)`。
   - 若 best reduced cost 非负，返回 node optimal relaxation。
   - 否则加入 negative columns。
6. 超过 `maxColumnGenerationIterations` 则报错。

### 13.4 Pricing backend

`LabelingNodePricingBackend`：

- 使用 `PricingSolver`，即 forward/backward/bidir-static/bidir-dynamic。
- 从 inherited branch rows 构造 pricing adjustment。
- 要求 `PricingResult.exact() == true`。
- 检查返回 column 的 direct reduced cost 与 best reduced cost 一致。
- 过滤当前 RMP 已有列，只返回 missing negative columns。

`RouteUniverseNodePricingBackend`：

- 在 tiny route universe 中扫描 reduced cost。
- 不产生 forward/backward label counters。
- full-size benchmark 默认拒绝该路径。

`HybridNodePricingBackend`：

- 用于 tiny/root 路径在 labeling backend 和 route-universe backend 之间切换。

### 13.5 Cut separation inside BCP

`solveNodeLpWithSeparatedRows(...)`：

- 对 active robust candidate rows 调用 `RobustCutSeparator`。
- 若打开 generated robust tiny path，则调用 `RobustCutCandidateGenerator`。
- 若打开 SR separation，则调用 `SubsetRowCutSeparator.violatedL2Triples(...)`。
- 加 cut 后重新 solve LP，直到没有新 violated rows。

边界：

- benchmark BCP 支持 `--cuts none` 和 `--cuts sr`。
- benchmark BCP 不支持 robust cuts，因为 robust candidate generator tiny-only。

## 14. CLI and Experiment Commands

代码：

- `src/main/java/org/pdptw/cli/Main.java`
- `src/main/java/org/pdptw/cli/RunBcp.java`
- `src/main/java/org/pdptw/cli/RunRootCg.java`
- `src/main/java/org/pdptw/cli/RootColumnGenerationRunner.java`
- `src/main/java/org/pdptw/cli/RunRootLpPricingSmoke.java`
- `src/main/java/org/pdptw/cli/RunRootFiniteCgSmoke.java`
- `src/main/java/org/pdptw/cli/RunBenchmarkSubinstance.java`
- `src/main/java/org/pdptw/cli/BenchmarkCsv.java`
- `src/main/java/org/pdptw/cli/TraceCsv.java`

### 14.1 Command dispatcher

`Main.run(...)` 支持：

```text
pricing-audit
root-cg
bcp
compare-pricing
benchmark
instance-smoke
root-lp-pricing-smoke
root-finite-cg-smoke
benchmark-subinstance
```

### 14.2 BCP CLI

`RunBcp.main(...)` 关键逻辑：

- 若输入是 benchmark text instance：
  - 必须显式指定 labeling pricing，例如 `--pricing bidir-dynamic`。
  - 拒绝 route-universe pricing。
  - 支持 `--cuts none` 或 `--cuts sr`。
  - 拒绝 `--cuts robust`。
  - 解析 `--max-nodes`、`--max-cg-iterations`、`--max-set-branch-size`。
  - 用 restricted seed columns 初始化 route universe。
  - 调用 `BcpRunner.withBenchmarkLabeling(...)` 或 `withBenchmarkLabelingAndSubsetRowSeparation(...)`。

注意：

- seed route universe 只用于初始列和 branch feasibility。
- benchmark text BCP 的 pricing 由 explicit labeling solver 执行，不是 finite candidate pricing。
- 12 个 `*_n6` 受控实验全部在 root node 得到整数解，因此当前实验验证的是 BCP pricing-loop 链路，不是复杂分支树性能。

### 14.3 Root LP / finite CG smoke

`RunRootLpPricingSmoke`：

- 读取 benchmark instance。
- 构造 conservative seed columns。
- 求 restricted root LP。
- 在 finite candidate pool 中扫描 negative reduced-cost candidates。

`RunRootFiniteCgSmoke`：

- 在同一个 finite candidate pool 内重复加入 negative columns。
- 输出 `remainingNegativeCandidates`。

边界：

- 这是受控 finite-pool 实验，不是 exact pricing。

### 14.4 CSV 输出

`BenchmarkCsv.Row` 字段包括：

```text
instance, mode, status, lowerBound, upperBound, gap,
nodes, columns, activeCuts, pricingCalls,
forwardLabels, backwardLabels, dominatedLabels,
pricingTimeMs, totalTimeMs
```

`TraceCsv.bcpNodes(...)` 输出 node-level trace。

## 15. Tests

重要测试文件：

| 测试文件 | 覆盖内容 |
|---|---|
| `src/test/java/org/pdptw/core/CoreIoTest.java` | core data model、reader、route checker |
| `src/test/java/org/pdptw/master/GurobiRmpSmokeTest.java` | Gurobi RMP 基础 |
| `src/test/java/org/pdptw/pricing/ForwardLabelerTest.java` | forward labeling |
| `src/test/java/org/pdptw/pricing/BackwardLabelerTest.java` | backward labeling |
| `src/test/java/org/pdptw/pricing/BidirectionalMergeTest.java` | bidirectional merge |
| `src/test/java/org/pdptw/pricing/DynamicHalfwayControllerTest.java` | dynamic half-way controller |
| `src/test/java/org/pdptw/cuts/SubsetRowCutTest.java` | subset-row cut |
| `src/test/java/org/pdptw/cuts/RobustCutsRepairTest.java` | robust DTI/PTI repair |
| `src/test/java/org/pdptw/branch/BranchingTest.java` | branching |
| `src/test/java/org/pdptw/cli/BenchmarkRunnerTest.java` | CLI benchmark paths |

运行：

```bat
test.bat
```

## 16. 当前实现边界

### 16.1 可以认为已经实现的核心部件

- RMP LP and dual extraction。
- Forward / backward / bidirectional static / bidirectional dynamic pricing。
- Label merge and reduced-cost audit。
- DTI/PTI checks and robust matrix repair。
- SR cut row、SR separation、SR pricing state。
- Vehicle-count and set-outflow branching。
- Branch-node exact labeling pricing backend。
- Controlled benchmark text BCP path。

### 16.2 仍是受控或 tiny-only 的部件

- `BruteForcePricingOracle` 和 route-universe exact pricing 仅适合 tiny。
- `RobustCutCandidateGenerator` 明确限制 `nRequests <= 6`。
- Full-size benchmark robust cut separation 尚未完成。
- Full-size benchmark BCP exact labeling path 可以进入，但性能尚不能支撑论文表格级批实验。
- Root LP / finite CG benchmark smoke 使用有限候选池，不是 exact pricing proof。

### 16.3 公开时不应夸大的点

不能写：

```text
This repository reproduces the full 220-instance paper results.
```

应写：

```text
This repository reproduces the core bidirectional labeling and controlled BCP mechanics, with 12 representative controlled experiments. Full 220-instance paper-table reproduction is still future work.
```

## 17. 文件索引

| 主题 | 文件 |
|---|---|
| 总体 README | `README.md` |
| 复现范围与结果 | `docs/reproduction_scope_and_results.md` |
| root LP / pricing-only 12 算例 | `docs/paper12_root_pricing_experiment.md` |
| BCP 12 小实验 | `docs/bcp12_small_experiment.md` |
| 双向标签说明 | `docs/bidirectional_labeling_notes.md` |
| 实验命令 | `docs/experiments_quickstart.md` |
| root LP 结果 | `logs/paper12_results/paper12_root_lp.csv` |
| finite CG 结果 | `logs/paper12_results/paper12_finite_cg.csv` |
| BCP pricing-loop 小实验结果 | `logs/bcp12_results/bcp12_sr_summary.csv` |
| full-size default probe | `logs/bcp12_results/bcp12_fullsize_default_probe.csv` |
| full-size exact AA30 probe | `logs/bcp12_results/fullsize_exact_aa30_probe.txt` |
