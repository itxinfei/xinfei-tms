# 78-零担 TMS 智能求解器（OR-Tools / OptaPlanner / Timefold）接入需求文档

> 项目：心飞物流 TMS  
> 日期：2026-10-24  
> 文档类型：技术选型 + 功能需求 + 合规接入任务卡  
> 上游文档：《77-零担TMS智能调度配载装车闭环修复任务卡_20261024.md》  
> 适用阶段：P1 智能求解器试点；P0 规则引擎与扫码封签仍按《77》优先落地  
> 执行对象：Claude Code / 后端 / 运维 / 测试 / 管理端前端

---

## 0. 结论与执行边界

### 0.1 结论

OR-Tools、OptaPlanner、Timefold 都可以用于心飞物流零担 TMS 的智能调度，但职责不同：

1. **OR-Tools 优先用于 P1 的容量配载、多车拆分、车辆路径和时间窗问题。**
2. **新 Java 项目不再优先引入 OptaPlanner，OptaPlanner 官方已提示其延续为 Timefold Solver；若需要纯 Java 排班、司机/车次/月台约束评分，优先评估 Timefold Community。**
3. **OR-Tools / Timefold 只返回候选方案，不直接写业务库。**最终容量复核、配载方案落库、扫码封签、发运门禁仍由 `xfwl-work` / `xfwl-dispatch` 负责。
4. **P0 不上求解器。**先落地《77》的确定性规则引擎和真实扫码闭环，保证数据、状态、约束和审计可用。
5. **P1 以独立 `xfwl-optimizer` 服务试点 OR-Tools。**P2 再评估 Timefold 用于司机排班、月台波次、多点装卸和持续重规划。

### 0.2 一句话选型

| 问题 | 首选 | 原因 |
|---|---|---|
| 一批订单按重量/体积分到哪些车 | OR-Tools | bin packing、MIP/CP-SAT 是官方明确场景，适合硬约束和多车拆分 |
| 多车辆路径、取送货、时间窗、容量 CVRP | OR-Tools | Routing 模型成熟，官方示例完整 |
| 司机班次、车辆占用、车次资源冲突 | Timefold Community | 纯 Java，硬约束/软约束表达直观，适合排班和持续重规划 |
| 月台、装车口、波次、司机技能/资质 | Timefold Community | 调度排班、任务分配、资源可用性建模更自然 |
| 真正 3D 装箱、摆放朝向、堆叠碰撞 | 均非开箱即用 | 必须有包裹级尺寸，并用自研几何模型或专项算法 |
| 基础可解释、可兜底的 P0 配载 | 自研规则引擎 | 透明、稳定、可测，不依赖原生库和复杂求解器 |

### 0.3 当前不做

- 不把求解器直接放进 `xfwl-work` 事务链路；
- 不让求解器直接写订单、运单、运输任务、配载方案；
- 不因为“AI 推荐”绕过容量、扫码、封签、证照、GPS 在线等硬门禁；
- 不使用商业 SaaS 求解服务，不上传客户、订单、司机、轨迹数据到外部平台；
- 不引入 GPL/AGPL/SSPL 等与内网商业部署冲突的依赖；
- 不在缺少包裹长宽高、货量、车辆容量数据时宣称 3D 智能装箱；
- 不把求解器的“无可行解”直接展示给调度员，必须转换成业务原因。

---

## 1. 背景与问题定义

### 1.1 已完成的 P0 目标

《77》已经定义并要求先完成以下闭环：

1. 订单按当前机构、线路段、目的方向聚合；
2. 汇总件数、重量、体积；
3. 枚举车次、车辆、司机；
4. 后端做载重、体积、GPS、司机、占用硬校验；
5. 一车装不下时按确定性规则拆多车；
6. 生成配载方案、装车清单和扫码事件；
7. PDA 扫码装车、封签后才允许发运；
8. 到车扫码卸车并继续中转/终到链路。

P0 规则引擎解决“业务正确性”和“不漏不超”，但存在优化空间：

- 多车、多线路、多车次组合时，规则排序不一定得到最低成本或最高装载率；
- 当车辆资源紧张、时效件穿插、多点装卸时，人工规则难覆盖全部组合；
- 后续司机排班、月台波次、回程车、顺路车、时间窗会快速变成组合优化问题；
- 调度员需要知道为什么无可行解、哪个约束最紧、放宽哪个条件后能发车。

### 1.2 P1 要解决的问题

P1 求解器不是替代 P0，而是在 P0 数据和门禁之上回答：

1. 一批确定线路下的订单，应该用哪几台车、哪些车次、哪个司机？
2. 如何在不超载、不超容、不冲突、不晚点的前提下，减少车辆数和成本？
3. 当资源不足时，哪些订单必须留下，原因是超重、超容、司机不足、车辆离线还是发车时间不足？
4. 多点装卸、时间窗、回程车场景下，车辆应该按什么顺序走？
5. P2 如何把司机排班、月台波次、车辆检修、资质有效期纳入统一优化？

### 1.3 可量化业务目标

P1 试点只使用项目测试数据和真实试运行数据对比，不承诺竞品宣传数字。建议验收目标：

| 指标 | P1 试点目标 | 口径 |
|---|---:|---|
| 容量硬约束违规 | 0 | 任何自动/人工确认方案不得超重、超容 |
| 规则引擎可用性 | 99.9% | 求解器故障时仍可生成 P0 规则方案或转人工 |
| 100 票 / 20 车预览 P95 | ≤ 8 秒 | 管理端预览，含数据组装和求解 |
| 500 票 / 50 车异步任务 P95 | ≤ 30 秒 | 后台求解任务，不阻塞调度员 |
| 求解超时降级 | 100% 可回退 | 超时后返回规则方案或待人工，不返回错误状态 |
| 推荐方案可解释覆盖率 | 100% | 每个入选/未入选资源都有原因或评分项 |
| 无可行解原因覆盖率 | ≥ 95% | 未分配订单必须能定位到主要约束 |
| 推荐被采纳率 | 试运行统计，不设硬 KPI | 用于判断模型是否贴合现场 |
| 平均使用车辆数 | 不劣于规则引擎 | 同输入下对比规则方案 |
| 平均装载率 | 不劣于规则引擎 | 重量/体积利用率双口径同时观察 |

---

## 2. 官方资料与事实边界

### 2.1 OR-Tools 官方能力

OR-Tools 官方 Java 文档说明：

- OR-Tools 可在 Java 中处理线性优化、混合整数规划、约束规划；
- 官方列举的问题类型包括 assignment、bin packing、scheduling、routing、network flows；
- Routing 问题用于为车队在网络中寻找最优路径；
- Bin Packing 用于把不同尺寸对象放入不同容量容器；
- Java 安装通过 Maven 依赖 `com.google.ortools:ortools-java`，它是 C++ 原生库的 Java 封装；
- 官方示例中 bin packing 使用 MIP，变量表示“物品放入哪个 bin”，约束保证每件物品恰好放入一个 bin，且每个 bin 不超过容量，目标是最小化使用 bin 数。

### 2.2 OptaPlanner / Timefold 官方状态

OptaPlanner 官网当前明确提示：

- OptaPlanner 已 EOL，继续为 Timefold Solver；
- OptaPlanner 可优化带硬约束和软约束的计划与调度，覆盖 VRP、员工排班、任务分配、车间调度、云资源 bin packing 等；
- 它是轻量级、可嵌入 Java/Kotlin/Scala 的规划引擎，支持 Spring Boot、Quarkus、Kubernetes；
- 旧官网展示版本要求 JDK 11 或更高；
- Timefold 官网称其社区版为 Apache License 2.0、发布在 Maven Central，是 OptaPlanner 的延续。

Timefold 官方文档显示 Spring Boot starter 面向 Spring Boot 3.5；心飞当前根 `pom.xml` 实测为 **JDK 25 + Spring Boot 4.1.1**，因此不能直接把 Timefold starter 装进主工程，必须先做独立服务兼容验证。

### 2.3 不能写成事实的内容

以下内容没有在官方资料中得到直接证明，不得在项目文档、README 或验收中宣称：

- “OR-Tools 开箱即用支持零担 3D 装车”；
- “Timefold 已官方支持 Spring Boot 4.1”；
- “求解器一定比规则引擎快/省车/提升多少百分比”；
- “接入后无需人工调度”；
- “联云或某竞品一定使用 OR-Tools / OptaPlanner / Timefold”；
- “不需要 DWS 和包裹尺寸也能做精确空间摆放”。

---

## 3. 技术选型结论

### 3.1 选型矩阵

| 维度 | 自研规则引擎 | OR-Tools | OptaPlanner | Timefold Community |
|---|---|---|---|---|
| 许可证 | 自有代码 | Apache-2.0 | Apache-2.0，旧项目可用 | Apache-2.0，新项目优先 |
| 语言/运行 | Java | Java 封装 + C++ 原生库 | 纯 Java | 纯 Java |
| 上手成本 | 低 | 中 | 中 | 中 |
| 容量配载 | 适合确定性规则 | 强 | 可做 | 可做 |
| 多车拆分 | 可做，复杂组合一般 | 强 | 可做 | 可做 |
| VRP 路径 | 不适合自研复杂算法 | 强 | 可做 | 可做 |
| 时间窗 | 可做简单规则 | 强 | 可做 | 可做 |
| 司机/月台排班 | 不适合 | 可建模但不直观 | 强 | 强 |
| 软硬约束解释 | 最直观 | 需自行建模 | 强 | 强 |
| 持续重规划 | 一般 | 可重复求解 | 强 | 强 |
| Docker 原生依赖 | 无 | 有原生库，需验证 | 无 | 无 |
| 与 Boot4/JDK25 | 完全自主 | 需验证原生库与 JDK | 不建议新项目 | starter 需验证 |
| 心飞阶段 | P0 必须 | P1 试点 | 仅参考，不新引 | P2 评估 |

### 3.2 推荐架构

```text
管理端/自动调度
      │
      ▼
xfwl-dispatch
  ├─ 数据组装：订单、货物、线路、车次、车辆、司机、占用、证照
  ├─ P0 规则引擎：永远先跑，作为兜底与对照
  └─ OptimizerFeign（内网 REST）
            │
            ▼
      xfwl-optimizer
      ├─ 输入校验与租户隔离
      ├─ Rule Adapter（复制同版规则，用于对照）
      ├─ OR-Tools Adapter（P1：容量配载 / CVRP）
      ├─ Timefold Adapter（P2：排班 / 波次 / 多点装卸）
      └─ 结果标准化与不可行原因解释
            │
            ▼
xfwl-dispatch 保存求解任务与候选快照
            │
      人工确认 / 自动策略
            │
            ▼
xfwl-work 二次硬校验 → 配载方案落库 → 装车扫码 → 封签 → 发运
```

### 3.3 为什么独立服务

1. OR-Tools 带 C++ 原生库，独立服务可隔离镜像依赖和崩溃风险；
2. Timefold 当前 Spring Boot 支持口径与主工程 Boot4/JDK25 不完全一致，独立服务便于使用兼容运行时；
3. 求解 CPU 密集，避免抢占 `xfwl-dispatch` 的定时任务和接口线程；
4. 可设置超时、线程池、内存限制、灰度开关和版本回滚；
5. 未来替换 OR-Tools、Timefold 或商业求解器时，不影响核心业务事务；
6. 求解器只做无状态计算，不持有业务写权限，合规和审计边界清晰。

### 3.4 通信方式

P1 使用内网 REST/JSON，不先引入 gRPC：

- 优点：调试方便、网关/Feign 经验可复用、不需要额外代码生成；
- 性能：100～500 票级 JSON 序列化不是瓶颈；
- 超时：Feign 连接超时 ≤ 3 秒，读超时按场景配置，预览 ≤ 8 秒，异步任务不阻塞；
- P2 若出现高频批量求解且 JSON 成为瓶颈，再评估 gRPC。

---

## 4. 角色与权限

| 操作 | 调度员 | 网点操作员 | 调度主管 | 系统管理员 | 司机 |
|---|---|---|---|---|---|
| 发起规则配载预览 | 本网点/线路 | 本网点 | 授权范围 | 全量 | 否 |
| 发起求解器预览 | 本网点/线路 | 否 | 授权范围 | 全量 | 否 |
| 查看候选方案评分 | 是 | 否 | 是 | 是 | 否 |
| 人工调整候选方案 | 是 | 否 | 是 | 否 | 否 |
| 确认方案 | 是 | 否 | 是 | 否 | 否 |
| 自动应用求解方案 | 否 | 否 | 可配置 | 配置开关 | 否 |
| 查看求解输入/输出快照 | 本范围 | 否 | 授权范围 | 全量 | 否 |
| 配置权重/超时/安全系数 | 否 | 否 | 是 | 技术配置 | 否 |
| 强制放宽硬约束 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| 扫码装车/卸车/封签 | 可代操作 P1 | 可代操作 P1 | 可查看 | 否 | 本人任务 |

硬约束不允许任何角色通过“强制确认”绕过。现场异常只能通过改变输入解决：换车、拆车、补录货量、换司机、调整车次、走异常审批。

---

## 5. 功能需求

### FR-OPT-001 场景管理

系统必须支持以下求解场景：

| 场景编码 | 名称 | 阶段 | 引擎 |
|---|---|---|---|
| `LOAD_CAPACITY` | 同线路段容量配载/多车拆分 | P1 | OR-Tools |
| `LINE_VEHICLE_ROUTING` | 线路内车辆路径/时间窗 | P1 | OR-Tools |
| `RESOURCE_SCHEDULING` | 司机/车辆/车次资源排班 | P2 | Timefold |
| `DOCK_WAVE_SCHEDULING` | 月台/波次/装车口排程 | P2 | Timefold |
| `MULTI_STOP_LOADING` | 多点装卸与区间容量 | P2 | OR-Tools + Timefold |
| `PACKAGE_3D_LOADING` | 包裹级 3D 装箱 | P2+ | 自研专项，暂不承诺 |

### FR-OPT-002 数据质量闸门

求解前必须校验：

- 订单 ID、线路段、当前机构、目的机构存在；
- 货物件数、总重量、总体积非空且为正数；
- 车辆准载重量、准载体积非空且为正数；
- 车辆车型尺寸字段在 P1 可缺失，但缺失时不能进入 3D 装箱；
- 车次绑定车辆和司机；
- 车辆状态、司机状态、GPS 在线状态可获取；
- 车辆未完成任务和同批次占用可查询；
- 所有时间字段含时区，统一使用服务器本地业务时间并在 DTO 中保留 ISO-8601；
- 数据不完整时，对应订单进入 `unassigned`，不得静默忽略。

### FR-OPT-003 规则引擎对照

每次 OR-Tools 求解必须同时保留规则引擎结果：

- `RULE_FEASIBLE`：规则方案可行；
- `RULE_INFEASIBLE`：规则方案不可行；
- `ORTOOLS_FEASIBLE`：求解器方案可行；
- `ORTOOLS_BETTER`：求解器评分优于规则；
- `ORTOOLS_WORSE`：求解器不优于规则；
- `ORTOOLS_TIMEOUT`：求解器超时。

P1 灰度期默认：

- 自动任务仍采用规则结果；
- 求解器结果只展示“推荐对比”；
- 调度主管可手工采用求解器方案；
- 经过固定测试集和试运行后，才允许配置自动采用。

### FR-OPT-004 硬约束

P1 容量配载必须至少建模以下硬约束：

1. 每个订单要么被分配一次，要么进入未分配列表；
2. 每台车总重量不得超过 `allowableLoad × weightSafetyFactor`；
3. 每台车总体积不得超过 `allowableVolume × volumeFillFactor`；
4. P1 只允许同当前机构、同线路段、同方向货物拼车；
5. 车辆必须启用，且未被未完成任务占用；
6. 车辆 GPS 必须在线，离线车辆不可用；
7. 司机必须可用，不能有任务冲突；
8. 车次必须绑定车辆和司机；
9. 确认时间距计划发车时间必须满足最小装车准备时间；
10. 车辆营运证、司机驾驶证/从业资格证等 P1 已接入的资质必须有效；
11. 时效件必须满足最晚发车/到达约束；
12. 单票重量或体积超过所有候选车时，不允许拆票，必须进入单票超限异常；
13. 不同租户数据不得出现在同一求解输入中。

### FR-OPT-005 软约束与评分

软约束不能违反硬约束，只能在可行方案中排序：

| 评分项 | 方向 | 建议默认权重 | 说明 |
|---|---|---:|---|
| 使用车辆数 | 越小越好 | 1000 | 多用车强惩罚 |
| 超重/超容风险 | 越小越好 | 硬约束 | 不进入软约束，直接不可行 |
| 总行驶距离 | 越小越好 | 100 | 线路/路径场景使用 |
| 预估运输成本 | 越小越好 | 100 | 车型成本、里程、车次成本 |
| 时效晚点分钟 | 越小越好 | 200 | 晚发/晚到惩罚高 |
| 装载率偏离目标 | 越小越好 | 80 | 避免大马拉小车，也避免过度拥挤 |
| 空驶/回程浪费 | 越小越好 | 150 | P2 回程车使用 |
| 司机超时/连续作业 | 越小越好 | 300 | P2 排班 |
| 临时换车/换司机 | 越少越好 | 50 | 保持计划稳定 |
| 异形/易碎混装风险 | 越小越好 | 120 | P1 无细粒度属性时仅预留 |

权重必须配置化，按租户、机构、场景可覆盖；不得写死在代码中。

### FR-OPT-006 多车拆分

- 同一货量组可输出多个方案 bin；
- 不允许为了凑车把一票运单拆到多台车，除非未来业务显式支持“拆票运输”；
- 大件、重货、时效件优先排序；
- 同输入、同配置、同版本必须可复现；
- OR-Tools 求解必须设置固定随机种子或确定性模式；
- 每台车输出总重量、总体积、件数、利用率和订单列表。

### FR-OPT-007 无可行解诊断

当无法全部装下时，系统必须返回可操作原因，而不是只返回失败：

| 原因码 | 诊断动作 | 建议处理 |
|---|---|---|
| `MISSING_CARGO_MEASURE` | 定位缺重量/体积/件数订单 | 补录货量 |
| `OVERLOAD_WEIGHT` | 放宽车数后仍超重 | 换大车/整车 |
| `OVERLOAD_VOLUME` | 放宽车数后仍超容 | 换大车/整车 |
| `OVERSIZE_SINGLE_ORDER` | 单票超过所有候选车 | 异形车/人工整车 |
| `TRUCK_DISABLED` | 车辆停用 | 更换车辆 |
| `OFFLINE_TRUCK` | GPS 离线 | 修复设备或换车 |
| `TRUCK_OCCUPIED` | 车辆已有未完成任务 | 换车或调整时间 |
| `DRIVER_UNAVAILABLE` | 无可用司机 | 调整司机排班 |
| `LICENSE_EXPIRED` | 车证/人证过期 | 更换资源 |
| `DEPARTURE_TOO_SOON` | 装车准备时间不足 | 换车次/加急审批 |
| `LATE_DELIVERY_RISK` | 无法满足时效 | 调整优先级/资源 |
| `NO_FEASIBLE_VEHICLE` | 其他无可行资源 | 待人工 |

实现要求：

- 为每类硬约束增加松弛变量或二次诊断；
- 输出每个未分配订单的主原因、次原因、约束缺口值；
- 例如：`缺容 12.4m³，增加 1 台 4.2m 箱车可满足`；
- 原因展示必须能让调度员直接采取动作。

### FR-OPT-008 候选方案预览

管理端预览必须展示：

- 规则方案与 OR-Tools 方案对比；
- 每个 bin 的车牌、车型、司机、车次、计划发车时间；
- 订单数、件数、重量、体积；
- 载重利用率、容积利用率；
- 预估距离、成本、晚点分钟；
- 硬约束是否全部满足；
- 软约束总分和主要扣分；
- 未分配订单及原因；
- 求解引擎、模型版本、约束版本、求解耗时。

### FR-OPT-009 人工可调整

在确认前，调度员可以：

- 从候选车中移除订单；
- 把订单从一台车调整到另一台车；
- 更换车辆、司机或车次；
- 锁定某台车或某个订单不允许求解器移动；
- 重新求解；
- 放弃求解器结果，采用规则方案。

每次人工调整后必须重新执行后端硬约束。调整后若违反硬约束，不允许确认。

### FR-OPT-010 自动应用策略

P1 灰度期自动应用必须同时满足：

- 求解状态为 `FEASIBLE`；
- 硬约束罚分为 0；
- 求解器评分不劣于规则方案；
- 不存在未分配订单；
- 所有资源在线、资质有效、占用检查通过；
- 场景开关 `dispatch.optimizer.auto-apply=true`；
- 方案仍需通过 `xfwl-work` 二次硬校验。

不满足任一条件时，生成预览或待人工方案。

### FR-OPT-011 超时与故障降级

| 故障 | 处理 |
|---|---|
| optimizer 服务不可用 | dispatch 返回规则方案；规则不可行则待人工 |
| OR-Tools 原生库加载失败 | 服务标记不健康，dispatch 自动切规则 |
| 求解超时 | 返回当前最优可行解；若无可行解则切规则 |
| 输入数据不合法 | 不调用求解，返回数据质量问题 |
| Feign 返回空或异常 | 不允许当作“无货/无车”，必须失败关闭 |
| 求解器返回超载 | 结果判定为非法，不允许展示为推荐 |
| 结果反序列化失败 | 记录错误，切规则，不自动应用 |

### FR-OPT-012 可复现与回放

每次求解必须记录：

- 请求 ID、租户、机构、场景、触发来源；
- 输入快照；
- 规则引擎版本；
- OR-Tools / Timefold 版本；
- 约束模型版本；
- 权重配置版本；
- 求解时间、随机种子、超时配置；
- 输出结果和未分配原因；
- 最终是否采用、采用人/自动策略。

系统必须支持按快照重放：同一输入、同一引擎版本和配置应得到相同结果。引擎升级后可回放历史 case 做回归。

---

## 6. 输入输出契约

### 6.1 内部接口

`xfwl-optimizer` 不直接通过网关暴露，仅注册 Nacos 供内网服务调用。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/actuator/health` | 健康检查，需包含原生库加载状态 |
| POST | `/api/v1/optimizer/load-plans/solve` | 容量配载/多车拆分 |
| POST | `/api/v1/optimizer/routes/solve` | 车辆路径/时间窗 |
| POST | `/api/v1/optimizer/schedules/solve` | P2：资源排班 |
| GET | `/api/v1/optimizer/engines` | 返回引擎、版本、能力开关 |
| POST | `/api/v1/optimizer/replay` | 按历史快照回放 |

### 6.2 管理端接口

经 `xfwl-web-manager` 暴露：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/manager/dispatch/optimization/preview` | 同步预览 |
| POST | `/api/manager/dispatch/optimization/jobs` | 创建异步求解任务 |
| POST | `/api/manager/dispatch/optimization/jobs/page` | 分页查询任务 |
| GET | `/api/manager/dispatch/optimization/jobs/{id}` | 查看任务和候选结果 |
| POST | `/api/manager/dispatch/optimization/jobs/{id}/apply` | 采用某个候选方案 |
| POST | `/api/manager/dispatch/optimization/jobs/{id}/discard` | 放弃结果 |
| POST | `/api/manager/dispatch/optimization/jobs/{id}/replay` | 回放求解 |

### 6.3 请求示例

```json
{
  "requestId": "REQ-20261024-000001",
  "tenantId": "1",
  "scene": "LOAD_CAPACITY",
  "engine": "OR_TOOLS",
  "solveMode": "PREVIEW",
  "timeLimitMs": 5000,
  "randomSeed": 20261024,
  "currentOrgId": "100",
  "transportLineId": "200",
  "orders": [
    {
      "orderId": "10001",
      "transportOrderId": "20001",
      "startOrgId": "100",
      "endOrgId": "300",
      "quantity": 3,
      "weightKg": 120.5,
      "volumeM3": 0.8,
      "priority": 10,
      "latestDepartureTime": "2026-10-24T18:00:00+08:00",
      "goodsFlags": []
    }
  ],
  "vehicles": [
    {
      "tripsId": "30001",
      "truckId": "40001",
      "truckTypeId": "50001",
      "driverId": "60001",
      "departureTime": "2026-10-24T19:00:00+08:00",
      "allowableLoadKg": 5000,
      "allowableVolumeM3": 28,
      "truckStatus": "ENABLED",
      "onlineStatus": "ONLINE",
      "driverAvailable": true,
      "occupied": false
    }
  ],
  "constraints": {
    "weightSafetyFactor": 0.98,
    "volumeFillFactor": 0.85,
    "fullThresholdPercent": 90,
    "minDepartureLeadMinutes": 30
  },
  "objectiveWeights": {
    "vehicleCount": 1000,
    "lateness": 200,
    "distance": 100,
    "cost": 100,
    "utilizationGap": 80
  }
}
```

### 6.4 响应示例

```json
{
  "requestId": "REQ-20261024-000001",
  "status": "FEASIBLE",
  "engine": "OR_TOOLS",
  "engineVersion": "9.12.4544",
  "modelVersion": "load-capacity-v1",
  "constraintVersion": "constraints-v1",
  "solveDurationMs": 820,
  "hardScore": 0,
  "softScore": -1280,
  "bins": [
    {
      "binNo": "BIN-1",
      "tripsId": "30001",
      "truckId": "40001",
      "driverId": "60001",
      "orderIds": ["10001"],
      "plannedQuantity": 3,
      "plannedWeightKg": 120.5,
      "plannedVolumeM3": 0.8,
      "weightUtilizationPercent": 2.41,
      "volumeUtilizationPercent": 2.86,
      "loadingStatus": "HALF",
      "estimatedCost": 320.00,
      "estimatedDistanceKm": 86.0
    }
  ],
  "unassigned": [],
  "violations": [],
  "metrics": {
    "vehicleCount": 1,
    "assignedOrderCount": 1,
    "unassignedOrderCount": 0,
    "latenessMinutes": 0,
    "totalWeightKg": 120.5,
    "totalVolumeM3": 0.8
  },
  "comparedWithRule": {
    "ruleVehicleCount": 1,
    "better": false,
    "differences": []
  }
}
```

### 6.5 状态枚举

求解任务状态：

| 状态 | 含义 |
|---|---|
| `PENDING` | 已创建，等待执行 |
| `RUNNING` | 求解中 |
| `FEASIBLE` | 找到可行方案 |
| `INFEASIBLE` | 无可行方案 |
| `TIMEOUT` | 超时 |
| `FAILED` | 服务或数据异常 |
| `APPLIED` | 候选方案已被采用并进入 WORK 落库 |
| `DISCARDED` | 已放弃 |

求解结果状态：

| 状态 | 含义 |
|---|---|
| `OPTIMAL` | 求解器证明达到模型最优 |
| `FEASIBLE` | 找到可行解但未必最优 |
| `INFEASIBLE` | 硬约束无可行解 |
| `UNKNOWN` | 超时或求解器未能证明 |
| `INVALID` | 输出未通过业务校验 |

---

## 7. OR-Tools 建模要求

### 7.1 容量配载 MIP 模型

集合：

- `I`：订单集合；
- `V`：候选车辆/车次集合；
- `x[i,v]`：0/1 变量，订单 `i` 是否分配给车辆 `v`；
- `y[v]`：0/1 变量，车辆 `v` 是否被使用。

硬约束：

```text
# 每个订单恰好分配一台车，或在 P1 中进入未分配集合
Σ_v x[i,v] = 1，对所有 i ∈ I

# 重量约束
Σ_i weight[i] × x[i,v] ≤ allowableLoad[v] × weightSafetyFactor[v] × y[v]

# 体积约束
Σ_i volume[i] × x[i,v] ≤ allowableVolume[v] × volumeFillFactor[v] × y[v]

# 线路、方向、时间窗、车辆状态、司机可用性均为硬约束
```

目标函数：

```text
最小化：
  1000 × 使用车辆数
+ 200 × 时效晚点分钟
+ 100 × 预估运输成本
+ 100 × 行驶距离
+ 80  × 装载率偏离目标
+ 50  × 临时换车次数
```

### 7.2 未分配变量

为了让无可行解能输出原因，P1 应增加松弛变量：

- `z[i]`：订单 `i` 是否未分配；
- 约束变为 `Σ_v x[i,v] + z[i] = 1`；
- 目标函数对 `z[i]` 施加极大惩罚；
- 若求解后 `z[i]=1`，再运行诊断器定位原因。

### 7.3 Routing 模型边界

P1 的线路 VRP 只处理：

- 已存在线路下的途经网点顺序；
- 单仓/多网点取送货；
- 车辆容量；
- 时间窗；
- 多点装卸顺序。

P1 不替换现有 `TaskRoutePlanningServiceImpl` 的 DFS 线路规划。DFS 先解决“线路网络和中转层级”，OR-Tools 再解决“具体车辆和顺序优化”。

### 7.4 3D 装箱边界

OR-Tools 可以对长宽高、坐标、朝向、不可重叠进行数学建模，但这会显著增加变量数量和求解时间。P1 不做的原因：

1. 当前只有订单聚合重量/体积，没有包裹级尺寸；
2. 缺少可堆压、易碎、朝向、异形、重心约束；
3. P0/P1 的首要业务目标是容量不超载、现场不漏装；
4. 没有 DWS 实测数据时，3D 图可信度不足。

P2 前置条件：

- 包裹级条码和长宽高；
- 车型内部尺寸；
- 货物堆码/易碎/朝向属性；
- 多点装卸顺序；
- 现场可执行的装载位置编码。

---

## 8. Timefold 建模要求（P2）

### 8.1 引入条件

只有出现以下任一情况，才启动 Timefold POC：

- 司机排班、车辆检修、车次时间窗成为主要瓶颈；
- 月台、装车口、波次需要统一排程；
- 一辆车一天内多个任务，需要连续规划；
- 需要在订单临时取消、车辆晚点、GPS 离线时毫秒级重规划；
- 规则和 OR-Tools 难以表达大量软约束。

### 8.2 规划实体建议

| 实体 | 规划变量 | 说明 |
|---|---|---|
| `LoadOrderAssignment` | `selectedVehicleTrip` | 订单分配到车次/车辆 |
| `DriverTripAssignment` | `driver` | 车次分配司机 |
| `VehicleTaskBlock` | `timeSlot` | 车辆任务时间块 |
| `DockLoadingSlot` | `waveNo`、`dockCode`、`startTime` | 月台/波次排程 |
| `MultiStopVisit` | `previousVisit`、`nextVisit` | 多点装卸顺序 |

### 8.3 硬约束示例

- 司机同一时间只能执行一台车；
- 车辆同一时间只能执行一个任务；
- 证照过期司机/车辆不能分配；
- 车辆容量和配送时间窗必须满足；
- 月台同一时间只能服务一台车；
- 波次截单后不能加入新订单；
- 冷链、危货、易碎等特殊货物必须匹配对应资源。

### 8.4 软约束示例

- 减少司机等待；
- 减少车辆空驶；
- 优先熟线司机；
- 保证司机休息与连续作业限制；
- 降低月台拥堵；
- 尽量不改变已发布计划；
- 优先使用回程车。

### 8.5 兼容策略

Timefold 官方 Spring Boot starter 对 Spring Boot 3.5 支持明确；心飞主工程当前为 Boot4.1.1。P2 必须三选一：

1. 等待官方明确支持 Boot4/JDK25 后再接入；
2. 将 `xfwl-optimizer` 固定为独立 Java 21 + Spring Boot 3.5 服务；
3. 只使用 `timefold-solver-core`，自行装配，不使用 Spring Boot starter。

未完成最小 Demo 前，不允许在主工程 POM 中直接加 Timefold 依赖。

---

## 9. 数据落地

### 9.1 与《77》表的关系

配载方案、装车清单、扫码事件仍以《77》为准：

- `work_load_plan`：配载方案；
- `work_load_plan_order`：装车清单；
- `work_load_scan_event`：扫码/封签事件。

本文只新增调度求解任务与候选快照表，不重复存储最终配载方案。

### 9.2 新增表：求解任务

建议迁移脚本：`sql/dispatch/V20261024_1001__optimization_job.sql`

```sql
CREATE TABLE IF NOT EXISTS `disp_optimization_job` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'ID',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
  `job_no` VARCHAR(40) NOT NULL COMMENT '求解任务号',
  `scene` VARCHAR(40) NOT NULL COMMENT '场景 LOAD_CAPACITY/LINE_VEHICLE_ROUTING/RESOURCE_SCHEDULING',
  `engine` VARCHAR(30) NOT NULL COMMENT '引擎 RULE/OR_TOOLS/TIMEFOLD',
  `status` VARCHAR(30) NOT NULL COMMENT '状态 PENDING/RUNNING/FEASIBLE/INFEASIBLE/TIMEOUT/FAILED/APPLIED/DISCARDED',
  `trigger_source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '触发来源 MANUAL/AUTO_JOB/REPLAY',
  `current_org_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '当前机构ID',
  `transport_line_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '线路ID',
  `biz_group_key` VARCHAR(200) DEFAULT NULL COMMENT '业务分组键',
  `request_hash` VARCHAR(64) NOT NULL COMMENT '输入归一化哈希，用于幂等',
  `input_snapshot` JSON NOT NULL COMMENT '求解输入快照',
  `result_snapshot` JSON DEFAULT NULL COMMENT '求解结果快照',
  `selected_solution_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '选中的候选方案ID',
  `hard_score` BIGINT NOT NULL DEFAULT 0 COMMENT '硬约束得分，0为无硬违约',
  `soft_score` BIGINT NOT NULL DEFAULT 0 COMMENT '软约束得分',
  `solver_status` VARCHAR(30) DEFAULT NULL COMMENT 'OPTIMAL/FEASIBLE/INFEASIBLE/UNKNOWN/INVALID',
  `time_limit_ms` INT NOT NULL DEFAULT 5000 COMMENT '求解超时毫秒',
  `solve_duration_ms` INT NOT NULL DEFAULT 0 COMMENT '实际求解耗时毫秒',
  `engine_version` VARCHAR(60) DEFAULT NULL COMMENT '引擎版本',
  `model_version` VARCHAR(60) NOT NULL COMMENT '模型版本',
  `constraint_version` VARCHAR(60) NOT NULL COMMENT '约束版本',
  `weight_version` VARCHAR(60) NOT NULL COMMENT '权重配置版本',
  `random_seed` BIGINT DEFAULT NULL COMMENT '随机种子',
  `fallback_engine` VARCHAR(30) DEFAULT NULL COMMENT '实际降级引擎',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  `requested_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '发起人',
  `started_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `finished_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `applied_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '采用人',
  `applied_time` DATETIME DEFAULT NULL COMMENT '采用时间',
  `create_by` BIGINT UNSIGNED DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_by` BIGINT UNSIGNED DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_job_no` (`tenant_id`,`job_no`),
  KEY `idx_tenant_scene_status` (`tenant_id`,`scene`,`status`),
  KEY `idx_tenant_org_line` (`tenant_id`,`current_org_id`,`transport_line_id`),
  KEY `idx_request_hash` (`tenant_id`,`request_hash`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调度优化求解任务';
```

### 9.3 新增表：候选方案

```sql
CREATE TABLE IF NOT EXISTS `disp_optimization_solution` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'ID',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
  `job_id` BIGINT UNSIGNED NOT NULL COMMENT '求解任务ID',
  `solution_no` VARCHAR(40) NOT NULL COMMENT '候选方案号',
  `engine` VARCHAR(30) NOT NULL COMMENT '生成引擎',
  `rank_no` INT NOT NULL DEFAULT 1 COMMENT '候选排名',
  `is_selected` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否选中 0否 1是',
  `is_applied` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否已应用 0否 1是',
  `bin_count` INT NOT NULL DEFAULT 0 COMMENT '使用车辆数',
  `assigned_order_count` INT NOT NULL DEFAULT 0 COMMENT '已分配订单数',
  `unassigned_order_count` INT NOT NULL DEFAULT 0 COMMENT '未分配订单数',
  `total_quantity` INT NOT NULL DEFAULT 0 COMMENT '总件数',
  `total_weight` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '总重量kg',
  `total_volume` DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '总体积m³',
  `estimated_distance_km` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '预估距离km',
  `estimated_cost` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '预估成本',
  `lateness_minutes` INT NOT NULL DEFAULT 0 COMMENT '晚点分钟',
  `hard_score` BIGINT NOT NULL DEFAULT 0 COMMENT '硬约束得分',
  `soft_score` BIGINT NOT NULL DEFAULT 0 COMMENT '软约束得分',
  `total_score` BIGINT NOT NULL DEFAULT 0 COMMENT '总分',
  `solution_json` JSON NOT NULL COMMENT '标准候选方案JSON',
  `explain_json` JSON DEFAULT NULL COMMENT '评分解释与未分配原因',
  `create_by` BIGINT UNSIGNED DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_solution_no` (`tenant_id`,`solution_no`),
  KEY `idx_tenant_job_rank` (`tenant_id`,`job_id`,`rank_no`),
  KEY `idx_tenant_applied` (`tenant_id`,`is_applied`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调度优化候选方案';
```

### 9.4 留存要求

- 求解任务、输入快照、候选方案、采用记录随运单链路留存，至少 **3 年**；
- 管理操作日志按现有合规要求至少留存 **6 个月**；
- 若未来涉税或事故复盘使用调度结果，相关快照按更长业务留存策略执行；
- 快照属于经营数据，不允许普通调度员导出；
- 重放、测试数据必须脱敏，不能导出真实客户手机号和详细地址。

---

## 10. 配置项

```yaml
dispatch:
  optimizer:
    enabled: false
    base-url: http://xfwl-optimizer
    engine: RULE
    auto-apply: false
    preview-time-limit-ms: 5000
    async-time-limit-ms: 30000
    max-order-count: 500
    max-vehicle-count: 80
    compare-rule: true
    save-snapshot: true
    weight-safety-factor: 0.98
    volume-fill-factor: 0.85
    full-threshold-percent: 90
    min-departure-lead-minutes: 30
    weights:
      vehicle-count: 1000
      lateness: 200
      distance: 100
      cost: 100
      utilization-gap: 80
      resource-change: 50
```

要求：

- 默认关闭，灰度按机构/线路/场景开启；
- 配置进入 Nacos，不写死在代码；
- 权重修改必须记录版本；
- 安全系数不允许配置为大于 1；
- 体积填充系数不允许配置为大于 1；
- 求解器开关不影响 P0 规则引擎。

---

## 11. 工程落地要求

### 11.1 新增模块

新增 Maven 模块：`xfwl-optimizer`

建议结构：

```text
xfwl-optimizer
├── src/main/java/com/xinfei/optimizer
│   ├── XfwlOptimizerApplication.java
│   ├── controller
│   ├── application
│   ├── domain
│   │   ├── model
│   │   ├── constraint
│   │   └── score
│   ├── engine
│   │   ├── rule
│   │   ├── ortools
│   │   └── timefold
│   ├── adapter
│   └── config
└── src/main/resources
    ├── application.yml
    └── bootstrap.yml
```

### 11.2 Maven 依赖

OR-Tools 官方 Maven 坐标示例：

```xml
<dependency>
  <groupId>com.google.ortools</groupId>
  <artifactId>ortools-java</artifactId>
  <version>${ortools.version}</version>
</dependency>
```

Timefold 社区版核心依赖示例：

```xml
<dependency>
  <groupId>ai.timefold.solver</groupId>
  <artifactId>timefold-solver-core</artifactId>
  <version>${timefold.version}</version>
</dependency>
```

要求：

- 版本统一写入根 POM `dependencyManagement`；
- 不得在子模块散写版本；
- 只允许使用社区版坐标 `ai.timefold.solver:*`，不得引入 `ai.timefold.solver.enterprise:*`；
- 引入前执行 `mvn dependency:tree`、OWASP dependency-check、CycloneDX SBOM；
- OR-Tools 必须在 Linux x86_64 Docker 镜像中完成原生库加载测试；
- Windows 本机运行如缺 Visual C++ Redistributable，只能作为开发环境说明，不能要求服务器安装非必要图形组件。

### 11.3 Docker 部署

- 服务容器名：`xfwl-optimizer`；
- 纳入 docker compose 和 Gitea Actions；
- 注册 Nacos，namespace/group 与现有项目一致；
- 健康检查必须包含：应用存活、原生库加载、求解 smoke test；
- CPU 限制建议初始 2 核，内存 1～2Gi，按压测调整；
- 不挂载外部 SaaS 凭据；
- 不开放公网端口；
- 不保存客户个人信息；
- 日志只输出请求 ID、数量、耗时、状态，不输出客户姓名、手机号、详细地址。

### 11.4 线程与超时

- 单实例默认并发求解数 = CPU 核数 / 2，至少 1；
- 超出队列走异步任务；
- 每个求解任务必须可中断；
- 超时后释放线程和原生资源；
- 不允许在 Tomcat 请求线程中执行超过 10 秒的求解；
- 500 票以上必须走异步 job。

### 11.5 幂等与一致性

- 相同 `requestHash` 的重复预览在短时间内可返回同一快照；
- 应用候选方案时必须携带 `jobId`、`solutionId`、`solutionHash`；
- `xfwl-work` 二次校验失败时，任务不能标记为 APPLIED；
- 候选方案确认后，若订单、车辆、司机状态变化，应失效或重新校验；
- 采用求解方案后，最终数据以 `work_load_plan` 为准，`disp_optimization_solution` 仅为决策快照。

---

## 12. 合规与许可证要求

### 12.1 开源许可证

| 组件 | 官方许可证 | 商用可行性 | 要求 |
|---|---|---|---|
| OR-Tools | Apache-2.0 | 可商用、可本地部署、可修改 | 保留版权、许可证、NOTICE，声明修改 |
| OptaPlanner | Apache-2.0 | 可商用 | 新项目不优先，旧项目参考 |
| Timefold Community | Apache-2.0 | 可商用 | 只使用社区版，不使用企业版功能 |
| SCIP | 8.0.3 起 Apache-2.0 | 需审计实际打包版本 | 以最终依赖树和发行包许可证为准 |

合规动作：

1. 新增依赖前生成 CycloneDX SBOM；
2. OWASP dependency-check 不得存在未处理高危 CVE；
3. 人工确认传递依赖中无 GPL、AGPL、SSPL、商业授权限制；
4. 在发行物中保留 `LICENSES/` 与 `NOTICE`；
5. 修改开源源码时，在 `NOTICE` 中声明修改内容；
6. 不从非 Maven Central / 官方 GitHub Release 以外来源下载 jar/native lib；
7. 锁定依赖版本和 checksum；
8. 不使用 Timefold Enterprise 或其他需要商业授权的求解器功能。

### 12.2 数据最小化

传给 optimizer 的字段必须最小化：

- 允许：订单 ID、运单 ID、机构 ID、线路 ID、件数、重量、体积、车型、时间窗、资源状态、资质有效期；
- 不需要：客户姓名、手机号、详细门牌地址、证件号、司机手机号、登录凭据；
- 路径优化如必须使用坐标，只传机构/网点坐标，不传客户详细地址；
- 输入快照在日志中默认脱敏；
- 错误日志不得打印完整 JSON。

### 12.3 本地化与内网部署

- 所有求解在本地 Docker 或客户内网完成；
- 不请求外部 SaaS 求解接口；
- 不向 Google、Timefold 或其他外部服务发送业务数据；
- Maven 依赖只在构建期下载，运行期不得联网拉取模型；
- 离线部署包必须包含全部原生库和依赖；
- 服务不可访问公网时，核心功能必须可运行。

### 12.4 决策可解释与人工干预

- 自动推荐必须保留人工拒绝、调整、采用规则方案的入口；
- 不允许出现“系统强制派车，调度员无法修改”的黑盒流程；
- 每个推荐方案必须能解释硬约束和软约束；
- 自动求解失败不得影响正常人工调度；
- 所有采用、放弃、人工调整、权重修改必须留痕。

### 12.5 审计与业务真实性

- 求解快照、候选结果、采用记录必须可追溯；
- 运单、轨迹、结算相关数据留存按合规文档不少于 3 年；
- 操作日志不少于 6 个月；
- 车辆离线、证照过期、司机不可用等硬条件必须来自真实业务数据；
- 不允许为了让求解器得到方案而伪造车辆容量、GPS 在线状态或司机可用性；
- 最终发运仍必须遵守《77》的扫码、封签和状态门禁。

---

## 13. 页面需求

### 13.1 调度看板增加“智能推荐”区域

字段：

- 规则方案车辆数 / 求解器方案车辆数；
- 总重量、总体积、预估成本、预估晚点；
- 装载率对比；
- 未分配订单数量；
- 求解耗时；
- 引擎与模型版本；
- “采用规则方案 / 采用推荐方案 / 人工调整”按钮。

### 13.2 候选方案详情

- 按车辆分栏展示订单；
- 每台车展示容量条：重量、体积双维度；
- 不满足硬约束的车辆红色置顶；
- 鼠标悬停显示评分扣分项；
- 锁定订单/车辆后可重新求解；
- 调整后实时重算容量。

### 13.3 未分配订单处理

- 未分配订单独立列表；
- 展示主原因、次原因、缺口值；
- 提供快捷动作：补录货量、更换车次、查看车辆、查看司机证照；
- 未处理完不能整体采用方案。

### 13.4 求解任务记录

- 可按机构、线路、场景、状态、引擎、时间查询；
- 可查看输入/输出快照；
- 可一键回放；
- 可对比规则与 OR-Tools；
- 导出只对调度主管开放，并走审计日志。

### 13.5 开关与配置页

- 求解器开关；
- 自动采用开关；
- 场景灰度范围；
- 超时和最大订单数；
- 重量/体积安全系数；
- 软约束权重；
- 配置变更必须填写原因并记录版本。

---

## 14. 测试要求

### 14.1 单元测试

- 重量刚好达到安全系数边界；
- 体积刚好达到填充系数边界；
- 单票超过所有车辆；
- 两车才能装下；
- 一辆车被两个货量组重复争抢；
- 车辆离线、停用、占用；
- 司机不可用、证照过期；
- 缺重量、缺体积、缺件数；
- 固定种子下结果可复现；
- 权重变化后排序符合预期。

### 14.2 契约测试

- optimizer 入参缺租户时拒绝；
- 跨租户订单/车辆混合时拒绝；
- 输出订单重复分配时 dispatch 判非法；
- 输出超载时 dispatch 判非法；
- 输出缺订单时进入未分配；
- Feign 超时、500、空响应均可降级；
- 结果 hash 不匹配时不得应用。

### 14.3 集成测试

- 规则可行、OR-Tools 可行且更优；
- 规则可行、OR-Tools 不优，默认采用规则；
- OR-Tools 超时，规则方案可用；
- optimizer 宕机，调度任务不中断；
- 推荐方案采用后成功生成 `work_load_plan`；
- 推荐方案落库前车辆突然离线，WORK 二次校验拒绝；
- 未扫码、未封签仍不能发运。

### 14.4 性能测试

| 规模 | 目标 |
|---|---|
| 50 票 / 10 车 | P95 ≤ 3 秒 |
| 100 票 / 20 车 | P95 ≤ 8 秒 |
| 300 票 / 50 车 | 异步 ≤ 20 秒 |
| 500 票 / 80 车 | 异步 ≤ 30 秒 |
| 10 个并发任务 | 无线程堆积、无原生库崩溃 |
| 连续 100 次求解 | 无内存泄漏、句柄泄漏 |

### 14.5 故障演练

- 停止 `xfwl-optimizer` 容器；
- 模拟 OR-Tools 加载失败；
- 模拟求解超时；
- 模拟返回非法 JSON；
- 模拟 Feign 网络抖动；
- 模拟 Nacos 摘流；
- 模拟模型版本升级后历史快照回放。

### 14.6 合规测试

- 抓包确认 optimizer 不访问公网；
- 日志中无手机号、客户姓名、详细地址；
- 镜像内包含许可证和 NOTICE；
- SBOM 无 GPL/AGPL/SSPL；
- 无企业版依赖；
- 普通调度员不能导出求解快照；
- 采用/放弃/调整均有审计记录。

---

## 15. 分阶段实施计划

### 15.1 P0：规则闭环（必须先完成）

依据《77》：

- 货量聚合；
- 容量硬约束；
- 多车拆分规则；
- 配载方案与装车清单；
- PDA 扫码、封签、发运门禁；
- 车辆/司机占用校验；
- 装载率和装载状态修复。

### 15.2 P1：OR-Tools 独立服务试点

1. 新建 `xfwl-optimizer` 最小服务；
2. 完成 OR-Tools Linux Docker 原生库 smoke test；
3. 实现 `LOAD_CAPACITY` 场景；
4. 实现标准输入输出 DTO；
5. 接入 dispatch 预览，不落库自动应用；
6. 新增求解任务与候选表；
7. 管理端展示规则/求解器对比；
8. 加入 100 票以内测试集；
9. 支持超时降级和历史回放；
10. 试运行后按机构灰度自动采用。

### 15.3 P1.5：路径与时间窗

- 引入 OR-Tools Routing；
- 处理线路内多点装卸、取送货顺序、车辆容量、时间窗；
- 与现有 DFS 线路规划明确分工；
- 增加晚点、距离、成本评分。

### 15.4 P2：Timefold 排班与波次

- 独立 Timefold 兼容 POC；
- 实现司机/车辆/车次资源排班；
- 接入月台、波次、装车口；
- 支持回程车、连续作业、车辆检修；
- 支持计划发布后的非中断式重规划。

### 15.5 P2+：包裹级 3D 装箱

前置：包裹级条码、DWS 尺寸/重量、车型内部尺寸、堆码属性。

交付：

- 3D 坐标模型；
- 朝向、堆叠、重心、易碎约束；
- 多点装卸倒序摆放；
- 管理端/司机端装载位置示意；
- 现场扫码核验与位置偏差报警。

---

## 16. 验收 DoD

### 16.1 功能验收

- [ ] `xfwl-optimizer` 健康检查可返回 OR-Tools 原生库加载状态；
- [ ] 100 票 / 20 车容量配载可在目标时间内返回；
- [ ] 所有输出方案通过重量、体积硬约束；
- [ ] 一车装不下时可自动拆分为多车；
- [ ] 未分配订单有业务可处理原因；
- [ ] 规则方案与 OR-Tools 方案可同时展示并对比；
- [ ] optimizer 故障时自动降级为规则或待人工；
- [ ] 推荐方案必须经 WORK 二次校验后才能落库；
- [ ] 已落库方案仍受扫码、封签、发运门禁约束；
- [ ] 历史求解任务可按快照回放。

### 16.2 工程验收

- [ ] 根 POM 统一管理版本，子模块无散写版本；
- [ ] Linux x86_64 Docker 镜像可启动并通过 smoke test；
- [ ] 服务注册 Nacos，未在网关暴露公网入口；
- [ ] Feign 超时、熔断、降级配置齐全；
- [ ] 无循环 Feign、无 N+1 查询；
- [ ] 求解线程池有界，超时可中断；
- [ ] 受影响模块编译通过；
- [ ] 无 `System.out.println`，日志脱敏；
- [ ] 异常错误码已进入《08-后端接口文档》或本卡附录。

### 16.3 合规验收

- [ ] OR-Tools / Timefold 许可证与 NOTICE 已归档；
- [ ] CycloneDX SBOM 已生成；
- [ ] dependency-check 无未处理高危漏洞；
- [ ] 依赖树无 GPL/AGPL/SSPL；
- [ ] 未使用 Timefold Enterprise 坐标；
- [ ] 运行期无公网请求；
- [ ] 请求、日志、快照不含客户姓名、手机号、详细门牌地址；
- [ ] 求解快照和操作审计可按要求留存；
- [ ] 普通角色无法越权查看或导出全量快照。

### 16.4 文档验收

- [ ] 《60-技术栈评估与演进路线》中的 OR-Tools 路线与本文保持一致；
- [ ] 《77》中的配载方案表仍是最终业务落库真源；
- [ ] 《08-后端接口文档》补充优化任务接口；
- [ ] 《07-数据字典与枚举口径》补充求解场景、状态、引擎枚举；
- [ ] 《02-优先级总览》把 P1 求解器试点挂到智能调度条目；
- [ ] README 或运维文档补充 `xfwl-optimizer` 部署和故障降级说明。

---

## 17. 风险清单与应对

| 风险 | 等级 | 表现 | 应对 |
|---|---|---|---|
| 数据不准导致优化无意义 | 高 | 重量/体积缺失，方案看似可行但现场装不下 | 数据质量闸门 + P0 扫码核验 + DWS P1/P2 |
| 原生库在 Docker 中加载失败 | 高 | 容器启动失败、UnsatisfiedLinkError | 独立镜像、健康检查、固定版本、规则降级 |
| Boot4/JDK25 与 Timefold starter 不兼容 | 中 | 依赖冲突、启动失败 | 独立 SB3.5/JDK21 服务或仅用 core，POC 前不进主工程 |
| 求解结果不透明 | 中 | 调度员不信任推荐 | 展示评分、约束、未分配原因和规则对照 |
| 求解时间不可控 | 中 | 大批量阻塞接口 | 时间限制、异步任务、有界线程池 |
| 过早追求 3D | 中 | 模型复杂、现场无法执行 | 先做一维重量/体积，包裹级数据成熟后再做 3D |
| 软约束权重不合理 | 中 | 推荐结果偏离业务 | 权重配置化、历史回放、灰度试运行 |
| 自动采用造成错误派车 | 高 | 异常资源被自动分配 | 硬约束零容忍 + WORK 二次校验 + 默认人工采用 |
| 许可证漏报 | 中 | 商业发布合规风险 | SBOM、NOTICE、依赖树审计、禁用强 Copyleft |
| 数据外泄 | 高 | 输入传外部 SaaS | 强制本地部署、网络抓包验证、字段最小化 |

---

## 18. 参考资料

1. OR-Tools Java 入门：<https://developers.google.com/optimization/introduction/java?hl=en>
2. OR-Tools Java Maven 安装：<https://developers.google.com/optimization/install/java/pkg_windows>
3. OR-Tools Bin Packing：<https://developers.google.com/optimization/pack/bin_packing?hl=en>
4. OR-Tools Capacity Constraints / CVRP：<https://developers.google.com/optimization/routing/cvrp?hl=en>
5. OptaPlanner 官网：<https://optaplanner.io/>
6. Timefold Solver 官网：<https://timefold.ai/solver>
7. Timefold Maven / Core 依赖说明：<https://docs.timefold.ai/timefold-solver/latest/quickstart/hello-world/hello-world-quickstart>
8. Timefold Spring Boot 集成说明：<https://docs.timefold.ai/timefold-solver/1.x/integration/integration>
9. SCIP 许可证说明：<https://scipopt.org/index.php>
10. 心飞内部文档：《77-零担TMS智能调度配载装车闭环修复任务卡_20261024.md》
11. 心飞内部文档：《60-技术栈评估与演进路线.md》
12. 心飞内部文档：《10-物流行业合规与监管要求.md》
13. 心飞内部文档：《11-合规开发优先级清单.md》

---

## 19. Claude Code 执行指令

1. 不要直接开始写 OR-Tools 代码；先完成《77》P0 规则引擎和扫码封签闭环。
2. P1 第一步只允许建立独立 `xfwl-optimizer` 最小服务，并跑通官方 bin packing hello world。
3. 原生依赖必须在项目目标 Linux Docker 镜像里验证，不接受只在 Windows IDE 跑通。
4. 所有求解输出必须转换成心飞标准 DTO，不允许业务模块直接依赖 OR-Tools 数据结构。
5. 求解器不能直接写库；最终落库必须走 `xfwl-work`。
6. 硬约束不允许做软约束，超载、离线、证照过期、司机冲突必须拒绝。
7. 任何自动应用方案都必须能回退到规则引擎。
8. 引入依赖前必须提交许可证、SBOM、CVE 检查结果。
9. 不允许把客户个人信息、手机号、详细地址传给 optimizer。
10. 所有测试 case 必须包含无可行解、超时、降级和人工调整。
