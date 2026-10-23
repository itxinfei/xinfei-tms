# 财务结算 · 调度运力 · IoT 逐项任务卡（第三 / 四 / 五梯队）

> 承接《需求文档 v2.4》（§5.4 后续迭代）：
> - **第三梯队（P1）财务结算**：FIN-1 运费结算对账 / FIN-2 司机趟次工资 / FIN-3 账期回款坏账 / FIN-4 每趟利润
> - **第四梯队（P1/P2）调度运力**：DSP-1 多约束路线规划（可解释）/ DSP-2 外协·加盟运力池 / DSP-3 抢派双模
> - **第五梯队（P2）IoT 增值（依赖硬件，按需）**：IOT-1 冷链温控 / IOT-2 重量·视频货损 / IOT-3 能源结算
>
> **前置必读**：`12-ClaudeCode开发纪律与红线.md`、`06-开发规范与需求规格说明书.md`（**结算/调度/运力的需求与业务规则以 SRS §6.5 结算计费(POD)、§6.2 智能调度(DSP)、§6.3 承运商运力(CAP) 为准**，本卡只补实现细节与排期）、`09-多端数据打通与统一领域模型.md`、`20-运营可视化与成本管控任务卡.md`（OPS-4 已定义 `work_task_cost`，**尚未落地建表**）、`13-UI设计规范与页面模板.md`。

---

## 0. 硬约束（不可违反）

- 后端按**全栈升级基线**推进（JDK 21 LTS + Spring Boot 3.5 + Spring Cloud 2025.0；依赖锁定在基线之上不擅自再加升级，安全 CVE 随升级解决）。（**2026-10-08 修订**：原"JDK1.8/不升级任何依赖/CVE不处理"已废止，以全栈升级为准。）
- **不引新中间件**：不用 MongoDB / Redis Stream / MQTT / XXL-JOB / ES / 专用求解器（如 OR-Tools）。
- **新建 MySQL 表允许**；能查询算出的一律不建表。
- IoT 设备上报**复用现有通道**：`xfwl-netty` 的 Netty + HTTP（`POST /netty/push`），**不引 MQTT**。
- 管理端按 **Vue3 升级专项**推进（Vue3 + Element Plus）；本卡财务页按新栈实现，既有页随专项迁移。
- 接口风格：`@RequestMapping("xxx")`（无前导斜杠）+ `@PostMapping("/page")`。
- **★新增接口三层落点约定（2026-10-06 自检补，全卡通用）**：本卡 FIN/DSP/IOT 新增接口定义在 `xfwl-oms`/`xfwl-work`/`xfwl-dispatch`/`xfwl-base` 等**内部服务**，但内部服务**网关无路由**（网关 `ignored-services:'*'`），前端不可直连。所有新增接口必须：
  1. **数据/服务层**：在业务内部服务实现（Feign 互调，如运费规则、VRP 求解）；
  2. **网关暴露层**：由 `xfwl-web-manager` 新建聚合 Controller 暴露，网关路径 `/api/manager/<模块>/*`（如 `/api/manager/freight-bill/*`、`/api/manager/payment/*`、`/api/manager/dispatch/*`、`/api/manager/iot/*`）；C 端查询类走 `xfwl-web-customer`；
  3. **权限（现行模型，见《62》§决策 3）**：接口权限码 = `sys_menu`(type=按钮) 行 `perm`，后端 `@SaCheckPermission` + 角色授权 + 前端 `v-hasPerm`；网关只校验登录态。不要写"往资源表注册 method+url 清单"类前置条件。
  **禁止**把内部服务路径（如 `xfwl-oms /pay/*`）直接写进前端 API 配置——必 404 或被网关拒。
- **★建表与状态码现行口径（2026-10-13 订正，D-63）**：本卡各节 SQL/表名为业务设计参考；实际建表一律按《45》§三执行：① 表名改域前缀（财务= `oms_`，如 `oms_freight_bill`）；② 主键/外键 = `BIGINT UNSIGNED` 雪花（`@TableId(ASSIGN_ID)`）；③ 业务表带 `tenant_id`（MyBatis-Plus `TenantLineInnerInterceptor` 自动拼接）；④ 客户 id 用 `customer_id`（对齐 `oms_settlement_order.settle_object_id`，散客=`GUEST`）；⑤ 新增/变更状态枚举**必须回写《07》§十一**（§11.3 已按现行表名订正）；⑥ 状态码严禁沿用历史形态值——一切以《07》§〇之二为准。

### ⚠️ 关于"外协运力池"的红线澄清（必须先读）
用户已明确**不对接任何外部厂商/三方物流 API**。
**DSP-2 外协运力池 = 把加盟/外协的车和人"录入本系统"进行统一管理**（建档、派单、结算），
**≠ 调用外部厂商系统接口**（不对接顺丰/三通一达/EDI/ERP/外部平台 API）。
实现时**只允许本系统内部 CRUD + 内部派单**，禁止任何对外接口调用。

---

## 1. 现状核对小结

| 域 | 现状 | 结论 |
|---|---|---|
| 运费计算 | ✅ **Drools 运费规则已有**（`orderAmountCalc.drl` + `OrderServiceImpl#calculateAmount`（`:239`，注意方法名不是 `calculateOrderAmount`）内联 KieSession，支持热加载 `ReloadDroolsRulesService` / `RulesReloadController`）。🔴 **订正 2026-10-09**：原判"`DroolsRulesServiceImpl#calcFee` 是死代码（无调用方）"**有误**——它被 `.drl` 的 `then` 块调用（`orderAmountCalc.drl:33/50/67`），Java 侧 grep 看不到该引用；**禁止清理**。唯一死代码是其 `main`（`DroolsRulesServiceImpl.java:34-42`）。算价口径逐条见《52》§1 | 复用，但**计费明细未落库**；附加费按 SRS §6.5 POD-03 扩展现有规则（**不升级 Drools**） |
| 订单金额 | ✅ `Order.amount`、`paymentMethod`(1预结/2到付)、`paymentStatus`(1未付/2已付) | 有字段，无结算流程 |
| 结算/对账/账单 | 🟡 **应收结算单服务层已有**（`SettlementServiceImpl#settle/doSettle` 含幂等 + `SettlementOrder` 实体 + 事件入口 `SettlementDeliveredListener`，**但无对外 Controller、对账逻辑未实现**）；司机工资/账单/坏账**零实现** | 应收侧已通，须补对外接口 + 对账(POD-04)/司机结算(POD-05) |
| 趟次成本 | ⚠️ OPS-4 已**定义** `work_task_cost`，全仓无 DDL、无实体 → 需先建表 | 利润 = 收入 − 成本 |
| 调度链路 | ✅ **`DispatchTask` 5 阶段明确**：订单分类 → **路线规划 `ITaskRoutePlanningService`（实现 `TaskRoutePlanningServiceImpl`）** → 创建运输任务 → 车次车辆司机 `ITaskTripsSchedulingService` → 完善任务+司机作业；用 `@GlobalTransactional`(Seata) | **VRP 升级落点 = `TaskRoutePlanningServiceImpl`** |
| 车辆载重/体积 | ✅ `PdTruck.allowableLoad / allowableVolume`、`PdTruckType` | 多约束 VRP 的约束条件已具备 |
| 线路/车次 | ✅ `PdTransportLine`、`PdTransportTrips`、`PdTransportTripsTruckDriver` | 可复用 |
| 外协/承运商 | ❌ 无实体 | 需建表 |
| 状态机 | ✅ **`StateTransitionValidator` 已完备且已被调用** | **严禁新建/重写状态机**（见《多端数据打通》§3.2 修正） |
| IoT（温控/重量/能源） | ❌ 完全无 | P2，依赖硬件投入 |

---

## 1.5 P0-6b 最小可执行任务卡：应收对账闭环（2026-10-13 拆，认领即可开工）

> **定位**：不实施 FIN-1 全量（价格协议/应付/坏账均不动），只把**已存在的应收半环**（签收→`oms_settlement_order`，status `0待对账/1已对账/2已结算`，服务 `SettlementServiceImpl#settle/doSettle` 已通）拼成对账闭环。**这就是"Excel 对账半月起步"痛点的第一个真实解**。
> **依赖**：P0-6a（已通）。**人日**：5–8（归《02》P0-6b，人日归用户拍，本卡只拆不做重估）。

### 范围（只做 4 件事）
1. **建 1 张表 `oms_freight_bill`**（账单=对账载体，按《45》§三约定）：`id` BIGINT 雪花、`tenant_id`、`bill_no`（uk(tenant_id,bill_no)）、`customer_id`、`period_start`/`period_end` DATE、`order_count` INT、`total_amount` DECIMAL(14,2)、`status` TINYINT `0待确认/1已确认/2已核销`（**新枚举，建表时回写《07》§11.3**）、`remark`、审计字段。**不建**明细表——明细即 `oms_settlement_order`（按 `settle_object_id + period` 归集，避免双写）。
2. **账单生成**：`POST /api/manager/freight-bill/generate`（customer_id+账期 → 归集 status∈{0,1} 的应收结算单，落账单+回写结算单 bill_id 关联字段（`ALTER ADD COLUMN` 一列即可））。同客户同账期**幂等**（已有未完成账单则拒绝并返回单号）。
3. **对账视图**：`GET /api/manager/freight-bill/{id}` 返回账单头+结算单列表+**差异标记**（应收金额 vs 结算单 `receivable_amount` 逐条比对，差异只展示不自动改）。
4. **确认/核销**：`POST /api/manager/freight-bill/{id}/confirm`（0→1，结算单 0→1）、`/write-off`（1→2，结算单 1→2）。**调账走调整记录留痕，禁止 UPDATE 原金额**（本卡 §FIN-1 要点照抄）。

### 不做（守边界，防扩大化）
- 开票（P0-6c，依赖数电票通道选型，见《67》）、利润核算（P0-6d，依赖趟次成本表）、应付/司机结算（FIN-2）、坏账。
- **权限**：按 §0 三层落点第 3 条现行口径——`@SaCheckPermission("finance:freight-bill:*")` + sys_menu 按钮行；**不是**注册资源表。

### DoD（验收即测试）
- 《08》A.1 补本组接口字段实名行 + A.3 缺口销项；《45》§3.4 补表；《07》§11.3 补 status 枚举。
- 链路测试（可入《27》式脚本）：造 2 单签收→生成账单→金额=Σ结算单→确认→两单变 1→核销→变 2；重复 generate 幂等拒绝；越权（无 perm）403；跨租户不可见。
- `mvn compile` 全模块过 + 管理端页面可选（P0 允许纯接口验收，页面归 P1 补）。

> **✅ 前端收口定稿（2026-10-19，批次二）**：后端四能力（generate/reconcile/confirm/write-off）2026-10-16 已通，缺①账单分页列表接口（oms `/freight-bill/page` + Feign + web-manager 聚合，条件 billNo/customerId/status，orderByDesc createTime）；②web-manager 4 处 `finance:freight-bill:*` 通配符改具体码（decision-log 同款缺陷：通配注解仅对持通配权限用户生效，FINANCE 持具体码被 403）——定码 `finance:freight-bill:page|detail|generate|confirm|write-off` 五枚，sys_menu 按钮行逐字一致；③管理端页面 `views/finance/freightBill/index.vue`（列表+生成弹窗+对账详情抽屉[差异只展示]+确认/核销状态 gate），挂《21》§FIN 菜单组 107 财务结算（费用项配置旁，sort 2，菜单 id 250 + 按钮 25001~25005，绑 ADMIN+FINANCE）。


---

# 第三梯队：财务结算（P1）

> 对标 G7 财运通"订单/车辆/运力三大利润中心打通、盈亏实时可见"；通用 TMS 的运费审计与结算。
> **解决**：心飞财务侧完全空白 —— 不算账、不对账、不结算。

## FIN-1 运费结算与对账

### 新建表
**表设计现状（2026-10-16 已建表，P0-6b）**：`oms_freight_bill` **单表方案落地**——bill_no（uk(tenant_id,bill_no)）、customer_id（对齐 `oms_settlement_order.settle_object_id`，散客=GUEST）、period_start/end、order_count、total_amount、status（0待确认/1已确认/2已核销，见 PRD-3）。**不建明细表**——明细即 `oms_settlement_order` 按 `settle_object_id+period` 归集、账单回写 bill_id，避免双写（详见 §1.5）。字段真源《45》§3.4。

> 下方历史双表草案字段（detail：bill_id/order_id/transport_order_id/base_freight/extra_freight/adjust_amount/amount；主表 uk_bill_no、idx_member、idx_status_due）已废弃，仅作 P2 重构参考，与实建冲突处以《45》为准。

### 接口（`@RequestMapping("freight-bill")`）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/freight-bill/generate` | 按客户+账期生成账单（归集期内已完成订单） |
| POST | `/freight-bill/page` | 账单分页（客户/状态/到期日筛选） |
| GET | `/freight-bill/{id}` | 账单详情 + 明细 |
| POST | `/freight-bill/{id}/adjust` | 争议调账（写 `adjust_amount`，留痕） |
| GET | `/freight-bill/reconcile` | **对账视图**：账单 vs 运单 vs 支付记录差异 |

### 要点
- **计费明细落库**：把 Drools 算出的运费结果**持久化**到明细（现状只算不存，无法对账追溯）。
- 调账必须留痕，禁止直接改原金额。

### 合规约束（R10-1 · P1 · 依据《合规开发优先级清单》#16 /《物流行业合规与监管要求》R10）
- **运价透明可查**：运费计算规则（Drools 规则）须对司机/客户**透明展示**，结算单含计费明细（基础运费 + 附加项），禁止暗箱；呼应 §10.4 价格固化。
- **运费保障**：支持结算保障 / 垫付机制（COD / 月结账期），司机结算单（FIN-2）与回款（FIN-3）闭环，避免运费拖欠争议。
- 上述费率/结算规则变更须**留痕可审计**（呼应 R6-3 审计）。

### FIN-1.0a 应收 / 应付双账（P2 扩展 · 参照 EvolveTMS/BCKFreight · 2026-10-07 并入）

> 现状设计 `oms_freight_bill` 是**应收（客户/加盟方）**；承运商应付（外协运费）在 DSP-2 提到"走 FIN-1 账单"但无独立应付表。补**应付侧**视图，应收/应付分开建模（EvolveTMS 模式）：

- **新表（P2，xfwl-work 库）**：`oms_carrier_payable`（承运商应付账单：carrier_id / 账期 / 趟次 / 应付总额 / 已付金额 / 状态 0待结算 1部分结算 2已结算 / 到期日 / 备注）+ 明细 `oms_carrier_payable_detail`（task_transport_id / 应付运费 / 扣款(货损/晚点) / 补贴 / 小计）。
- **接口**（归 FIN-1 或 DSP-2 `@RequestMapping("freight-bill")`，P2）：`/freight-bill/payable/generate`（按承运商+账期生成）、`/freight-bill/payable/page`、`/freight-bill/payable/{id}/pay`（登记付款）。
- **联动**：承运商对账确认走 L-1 承运商端门户（`/carrier/settlement/statement`）；扣款依据 N-6 KPI/货损异常，留痕可审计。
- **DoD**：应收/应付两账分开查询与导出；付款登记留痕；不引新中间件。

#### FIN-1.0a-EXT 运费审计（FA-1，**P1** · 对标 MercuryGate/Blue Yonder · 2026-10-07 第三轮并入）
> 结算闭环关键环节：系统算费金额（rated）与承运商实际开票金额（invoiced）自动比对，差异超阈值转争议，杜绝"票多少付多少"。
- **字段扩展（不新增表）**：`oms_carrier_payable_detail` 加 `rated_amount`（系统算费）、`invoiced_amount`（发票金额，取 oms_invoice）、`diff_amount`（差额=invoiced-rated）、`audit_status`（0未审计 1一致 2有差异 3争议中 4已裁决）。
- **规则（yml 可配）**：`|diff_rate| ≤ 阈值（默认 2%）` 判一致；超阈值 audit_status=2 并生成差异记录；差异由财务复核（裁决按 rated 或 invoiced，填裁决原因）。
- **接口**（`freight-bill`）：`/freight-bill/payable/audit`（批量比对）、`/freight-bill/payable/diff/page`（差异列表）、`/freight-bill/payable/diff/{id}/resolve`（裁决）。
- **DoD**：明细可查算费/发票/差额；超阈值自动标差异；裁决留痕；不新增表、不引新组件。

### FIN-1.1 客户价格协议（P1 扩展 · 对标 G7 财运通结算 · 2026-10-06 并入）

> **解决**：B 端客户（企业/月结）没有专属计价，只能吃默认 Drools 规则 → 无法谈价、无法月结对账。

**表设计口径**（`oms_price_agreement` 客户价格协议；脚本落 `sql/settlement/`）：
member_id（月结/企业客户）、line_id / goods_type_id（空=不限）、weight_min/max 重量段、price_type（1按票 2按吨 3按方）、price 单价、discount 月结折扣（0.90=9折）、effective_from/to、status（1生效 0停用）；idx_member。

- **计费衔接**：Drools 计算前先查客户有效协议（member_id+线路+货物类型+重量段命中）→ **命中走协议价，未命中走默认规则**；协议变更留痕（R10-1 透明可查）。
- **接口**（归 FIN-1 `@RequestMapping("freight-bill")` 扩展）：`POST /price-agreement`（增改）、`GET /price-agreement/page`、`POST /price-agreement/{id}/disable`。
- **管理端页**：客户管理 → 价格协议（`customer/priceAgreement`，P1）。

> **✅ 第一期定稿（2026-10-19，POD 计费结算簇批次一，与 POD-06 客户侧费率卡同一物）**：
> 1. **计费衔接落点 = `xfwl-oms OrderServiceImpl#calculateAmount`（OrderServiceImpl.java:229）**：在 Drools `fireAllRules` **之前**按 协议命中（member_id + goods_type_id 可空不限 + totalWeight 落 weight_min/max 段 + 当日∈effective_from~to + status=1 + 未 deleted）查 `oms_price_agreement`；**命中** → `金额 = price ×（price_type=2按吨? totalWeight : 1）× discount`，直接返回（**跳过 Drools**，返回 Map 形态与现口径一致：`orderDto`+`amount`）；**未命中** → 原路走 Drools（零改动，保证散客与既有单据完全不受影响）。⚠️ 按票/按吨换算与折扣口径为本定稿新增（原卡未定义），折扣存 0.90 形态小数；协议价计算结果**记日志含 agreement_id** 可追溯（呼应 R10-1）。
> 2. **接口与权限码**（xfwl-oms 内部 `PriceAgreementController` + xfwl-web-manager 聚合 `/api/manager/price-agreement/*`，注解**具体码禁通配**，对齐 2026-10-19 DecisionLog 口径）：`POST /`（增改，`finance:price-agreement:save`）、`GET /page`（`finance:price-agreement:page`，分页 `page`+`pageSize` 驼峰）、`POST /{id}/disable`（`finance:price-agreement:disable`）。
> 3. **同批扩 FA-10 附加费第一期**：建 `oms_fee_item`（费用项配置：fee_code ∈ TOLL/LOADING/WAIT/OVERWEIGHT/FUEL/EMPTY_RETURN，**与 RPT-01 成本项口径对齐**；fee_name、calc_type 1固定/2按运费比例、fixed_amount、ratio、status）+ `oms_order_fee`（订单费用明细：order_id、fee_code、fee_name 快照、amount、source 1自动/2人工、calc_basis 计算依据、operator、remark）；接口 `/api/manager/fee-item/*`（page/save/disable）+ `/api/manager/order-fee/*`（page 按 orderId 查、POST 人工补录）。**第一期明细独立存 `oms_order_fee` 不改写 order.amount**（避免破坏现有 Drools 主流程与既有对账），展示层汇总+逐项可解释（ORD-03 口径）；等待费自动计时依赖 xfwl-work 作业数据后批接入。
> 4. 菜单：`sys_menu` 显式 id + tenant_id=1（utf8mb4 通道），价格协议页挂客户/会员管理组，费用项配置挂财务组；`sys_role_menu` 绑 ADMIN+FINANCE。


### FIN-1.2 电子发票（P1 扩展 · 对标 G7 财运通结算 · 2026-10-06 并入）

> ✅ **前端收口定稿（2026-10-19，POD 计费结算簇批次四）**：后端五端点已通（2026-10-16），本批补齐——① xfwl-web-manager `InvoiceController` 5 处通配 `finance:invoice:*` 改具体码 `finance:invoice:apply|issue|mail|void|page`（对齐 2026-10-19 DecisionLog 口径）；② 管理端 `api/invoice` + `views/finance/invoice` 页（列表+申请开票弹窗选已确认账单（freight-bill/page status=1，幂等拒绝由后端 400 兜底）+开具/邮寄/作废操作）；③ 菜单 SQL：252 电子发票（挂 107 财务结算，sort 3）+ 按钮 25201~25205（apply/issue/mail/void/page），绑 ADMIN+FINANCE；④ 《08》A.1 #15 权限码同步改具体码。不做清单不变（不接税控/不查真伪）。

> **解决**：对账后要开票，现无发票记录。**测试/开发环境不接真实税控**（税盘/百望等外部对接属"外部厂商 API"边界，不实现），只做申请-开具-邮寄状态管理。

**表设计现状（2026-10-16 已建表，P0-6c，不接真实税控）**：`oms_invoice`——invoice_no（issue 自编 `TEST-`+ts，uk(tenant_id,invoice_no)）、bill_id（→oms_freight_bill）、customer_id（对齐 settle_object_id）、amount（=账单 total_amount）、type（1普票 2专票）、status（1~5 见 PRD-3）、tax_no、mail_address、invoice_time、void_reason（status=5 必填）；idx_bill。字段真源《45》§3.4。
bill_id 关联账单（FIN-1）、member_id、amount、type（1普票 2专票）、status（状态机见 PRD-3）、invoice_no（税控开具后回填，测试环境自编）、tax_no、mail_address、invoice_time；idx_bill。

- **接口**（归 FIN-1 扩展）：`POST /invoice/apply`（按账单申请）、`POST /invoice/{id}/issue`（标记开具，测试环境自编发票号）、`POST /invoice/{id}/mail`（邮寄）、`GET /invoice/page`。
- **不做**：不接真实税控开票（边界条款）；不做发票真伪查验。

## FIN-2 司机趟次工资结算

### 新建表
**表设计口径**（`work_driver_settlement` / `work_driver_settlement_detail`；脚本落 `sql/work/`）：
- `work_driver_settlement` 司机结算单：settle_no、driver_id、period_start/end、trip_count 完成趟次、base_wage 底薪、trip_fee 趟次费合计、allowance 补贴（油补/过路/餐补）、deduction 扣款（违章/货损/迟到）、total_amount 应发、status（0待审 1已审 2已发放，见 PRD-3）；uk_settle_no、idx_driver。
- `work_driver_settlement_detail` 按趟次明细：settlement_id、task_transport_id、driver_job_id、amount、remark；idx_settle、idx_task。

### 接口（`@RequestMapping("driver-settlement")`）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/driver-settlement/generate` | 按司机+周期生成（归集 `DriverJob.status=4` 的趟次） |
| POST | `/driver-settlement/page` | 结算单分页 |
| GET | `/driver-settlement/{id}` | 详情 + 逐趟明细 |
| POST | `/driver-settlement/{id}/audit` | 审核 / 发放 |
| GET | `/driver-settlement/driver/{driverId}` | 某司机历史结算 |

> **✅ 第一期定稿（2026-10-20，POD 计费结算簇批次五，FIN-2 司机趟次工资）**：
> 1. **两表落地 `sql/work/`**（表名按《45》§3.10 八域定案 `work_` 前缀，字段按《45》§二）：`work_driver_settlement`（settle_no uk(tenant_id,settle_no)、driver_id〔=work_driver_job.driver_id 同源〕、period_start/end、trip_count、base_wage 底薪、trip_fee 趟次费合计、allowance 补贴、deduction 扣款、total_amount 应发、status **0待审/1已审/2已发放**（§11.3 已登记）、audit_remark、paid_time、remark + tenant_id/审计/deleted，idx_driver）、`work_driver_settlement_detail`（settlement_id、task_transport_id、driver_job_id、amount、remark，uk(settlement_id,driver_job_id) 一作业单只入一单、idx_task）。**不落 driver_name 快照**——web-manager 聚合层实时装配（对齐 driver-job-manager 惯例）。
> 2. **接口六枚**（xfwl-work 内部 `driver-settlement/*` + Feign `xfwl-service-work-api` + web-manager 聚合 `/api/manager/driver-settlement/*`，注解**具体码禁通配**，域前缀 `finance:driver-settlement:*`）：`POST /generate`（driverId+periodStart/periodEnd+baseWage/allowance/deduction 可选手工项+remark；归集周期内 DriverJob.status=4 且未被未删结算单引用的趟次〔按 actualArrivalTime∈period〕，明细每趟 amount=yml `xinfei.settlement.trip-fee` 每趟固定费率〔默认 0，禁硬编码〕，total=base+trip+allowance−deduction；同司机期间重叠已有未删单 → 400 返回已有 settle_no；无可归集趟次 → 400）、`GET /drivers`（候选司机：periodStart/periodEnd → distinct driverId+tripCount）、`POST /page`（settleNo/driverId/status）、`GET /{id}`（头+明细）、`POST /{id}/audit`（0→1）、`POST /{id}/pay`（1→2，paid_time=now）。**状态流转直接条件 UPDATE（where status=前值），不建状态机**。原 `/{id}/audit` 一接口拆 audit+pay 两枚（对齐 freight-bill confirm/write-off 惯例）；`/driver/{driverId}` 历史查询并入 page 的 driverId 过滤，不单设接口。
> 3. **管理端** `views/finance/driverSettlement/index.vue`（列表+生成弹窗选候选司机+详情读抽屉+审核/发放 gate），菜单挂 107 财务结算组 sort 4（菜单 id 253 + 按钮 25301~25306，绑 ADMIN+FINANCE）。
> 4. **第一期不做**：按里程/提成/按吨计酬（TaskTransport 无实际里程字段、POD-06 承运商费率卡未建，先落"每趟固定费率"一种并配置化）、打款通道对接（D-27：发放=线下打款后内部记账留痕）、审批驳回重提流（0→1→2 单向，异常走备注+重开）、司机端 App 工资条、`work_task_cost` LABOR 联动核对（后置 FIN-4 批次）。

## FIN-3 账期 / 回款 / 坏账预警

### 新建表
**表设计口径**（`oms_customer_credit` / `oms_payment_record`；脚本落 `sql/settlement/`）：
- `oms_customer_credit` 客户账期信用：member_id、credit_period_days、credit_limit、used_amount、risk_level（0正常 1关注 2预警 3黑名单）；uk_member。
- `oms_payment_record` 收付款流水：bill_id、type（1应收登记 2回款 3坏账核销 4退款）、amount、pay_method（1现金 2转账 3在线 4到付核销）、occur_time、operator；idx_bill、idx_occur。

### 接口（`@RequestMapping("payment")`）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/payment/receive` | 回款登记（回写账单 `paid_amount` 与状态） |
| POST | `/payment/bad-debt` | 坏账核销 |
| GET | `/payment/aging` | **应收账龄报表**（0-30/31-60/61-90/90+ 天） |
| GET | `/payment/overdue` | 逾期账单列表（触发预警） |

### 要点
- 逾期预警：每天 `@Scheduled` 扫 `due_date < now 且 status != 2` 的账单 → 写告警（`iot_alarm_record` 扩 `bizType=BILL`）或站内通知。
- **超信用额度时拦截下单**（可选，配置开关）。

## FIN-4 每趟 / 每单利润（"老板看数"）

### 不建表 —— 查询计算
```
趟次利润 = Σ(该趟关联订单 Order.amount)  −  Σ(work_task_cost where task_transport_id)
订单利润 = Order.amount − 分摊成本
```
数据来源：`work_task_cost`（OPS-4）+ `TransportOrderTask` → `TransportOrder.orderId` → `Order.amount`。

### 接口（`@RequestMapping("profit")`）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/profit/task/{taskId}` | 单趟利润（收入、成本明细、毛利、毛利率） |
| GET | `/profit/order/{orderId}` | 单订单利润 |
| GET | `/profit/summary` | 利润汇总（按车队/车辆/线路/月份，支持排序找亏损线） |
| GET | `/profit/ranking` | 线路/车辆利润排行（找出最赚与最亏） |
| GET | `/profit/drill/{dimension}` | **联动钻取（P1 · 承接 OPS-4 §6.7）**：dimension=order/truck/line/fleet，订单→车辆→趟次→利润逐层下钻 |

### 要点
- **V1 只做毛利**（收入 − 直接成本），不做分摊间接成本（避免过度设计）。
- 管理端**利润驾驶舱页**：卡片 + 排行（对标 G7"打开老板看数就能看到利润"）。
- **移动端老板视图（P1 扩展 · 2026-10-06 并入）**：复用司机端 App（admin 角色可见）或管理端 H5 适配，**只看 KPI**（今日业务量 / 毛利 / 在途异常数 / 成本占比 / 亏损线 Top3），调 `/profit/summary` + `/profit/ranking`，**不做明细操作**。

---

# 第四梯队：调度与运力（P1/P2）

## DSP-1 多约束路线规划（可解释）

### 目标
现状 DFS 路径规划**过程黑盒不可解释**（已知痛点）。对标满帮/货拉拉/G7 的智能调度。

### 落点（已确认）
**`xfwl-dispatch` 的 `TaskRoutePlanningServiceImpl`**（`DispatchTask` 第 2 阶段调用）。第 4 阶段 `ITaskTripsSchedulingService` 负责车次/车辆/司机匹配。

### 升级：从 DFS → 多约束启发式（**不引求解器依赖**）
约束（数据均已具备）：
| 约束 | 数据来源 |
|---|---|
| 车型匹配 | `PdTruckType`、`PdTruck.truckTypeId` |
| 载重 | `PdTruck.allowableLoad` vs 货物 `OrderCargo.totalWeight` |
| 体积 | `PdTruck.allowableVolume` vs `OrderCargo.totalVolume` |
| 时效 | `TaskTransport.planArrivalTime` / 线路时长 `PdTransportLine` |
| 多点取送 | `OrderLocation` / 订单收发地址 |
| 司机资质 | `PdTruckDriverLicense`、`PdTruckDriver.drivingAge` |

**算法建议（JDK21 纯 Java，零新依赖；2026-10-10 修订：随全栈升级基线由 JDK8 改为 JDK21）**：
1. 先按约束做**可行性剪枝**（车型/载重/体积/时效不达标直接排除）。
2. DFS/贪心构造初始解 → **局部搜索改进**：2-opt（路径内换位）+ 插入法（订单换线）。
3. **保留可解释性**：每一步决策记录原因。

### 新增表（可解释性，解决"黑盒"痛点）
**表设计口径**（`disp_dispatch_decision_log` 调度决策日志；脚本落 `sql/dispatch/`）：
job_id、business_id、order_id、stage（CLASSIFY 分类 / ROUTE 路线 / TRUKSCHED 车次调度）、decision 决策结果、reason 决策原因（约束命中/成本最低/时效最优）、rejected 被否决项及原因；idx_job、idx_order。

### 接口
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/dispatch/decision/{jobId}` | 某次调度的决策过程（管理端"为什么这么调度"） |
| POST | `/dispatch/simulate` | **调度试算**（不落库，对比不同参数结果） |

### 红线
- **禁止引入 OR-Tools / 任何求解器依赖**（不引新中间件/新依赖；⚠️ 2026-10-08 订正：原文以"不升级技术栈"作理由，该口径已废止，现行约束是"按升级基线迁移、但不引路线图外的新依赖"）。
- **禁止为了"智能"牺牲可解释性** —— 每条调度决策必须能说出原因。
- 不改动 `DispatchTask` 的 5 阶段结构与 `@GlobalTransactional`。

## DSP-2 外协 / 加盟运力池

> ⚠️ **红线**：仅"录入本系统管理"，**禁止调用任何外部厂商 API**。
>
> **✅ 第一期定稿（2026-10-19，调度运力簇批次三，承运商档案+挂靠+运力池查询）**：
> 1. **三表落地 `sql/base/`**（表名对齐《45》§3.3 补登）：`base_carrier`（code uk(tenant_id,code)、name、license_no 道路运输许可证号、license_expiry 有效期【预留资质过期排除，第一期仅展示不自动排除】、contact_name/phone、settle_type 1现结/2月结/3趟结、status **1合作中/0暂停/2黑名单**（对齐《55》CAP-01 三态）、remark）、`base_carrier_truck`（carrier_id、license_plate、truck_type_id、allowable_load/volume、status 1正常/0禁用/2在途，idx_carrier）、`base_carrier_driver`（carrier_id、name、phone、license_no、status 同车辆三态，idx_carrier）。全表按《45》§二：BIGINT UNSIGNED 雪花 + tenant_id + 审计字段 + `@TableLogic` deleted，collation `utf8mb4_0900_ai_ci`。**不建** CarrierQualification/Contract/Bid/ScoreCard 表（资质并入主表两字段；竞价/记分卡后置）。
> 2. **落点 xfwl-base 新建 carrier 分包**（对齐模块内 XfTruck 惯例：`XfCarrier` 实体/`IPdCarrierService`/`XfCarrierServiceImpl`/`XfCarrierMapper`/`CarrierController`，内部接口带 RequestContext 身份校验，网关无路由）；**Feign 放 `xfwl-service-base-api`**（CarrierFeign+Fallback）。
> 3. **接口**（内部 `carrier/*`，web-manager 聚合 `/api/manager/carrier/*`，注解**具体码禁通配**，域前缀 `base:carrier:*`）：`POST /`（增改，`base:carrier:save`）、`GET /page`（`base:carrier:page`，code/name/status 筛选）、`GET /{id}`（详情+挂靠车辆/司机，`base:carrier:detail`）、`POST /{id}/disable|enable`（`base:carrier:disable`）、`POST /{id}/truck`、`POST /{id}/driver`（挂靠增改，复用 save 码）、`GET /capacity`（运力池：status=1 且挂靠车辆按车型/载重/关键词筛，`base:carrier:capacity`）。
> 4. **管理端页** `views/base/carrier/index.vue`：列表+写弹窗（居中 append-to-body）+详情读抽屉（居右，含车辆/司机挂靠管理）；菜单挂基础数据组（id 建菜单阶段实测），绑 ADMIN。
> 5. **第一期不做**：`POST /carrier/dispatch` 派单外协（涉 xfwl-dispatch 调度链路与 `TaskTransport.carrier_id` 扩列，随调度批次）、竞价 CarrierBid、记分卡、KPI 考核、资质过期自动排除（仅展示有效期）、承运商端自助门户。

### 新建表
**表设计口径**（`base_carrier` / `base_carrier_truck` / `base_carrier_driver`；脚本落 `sql/base/`）：
- `base_carrier` 承运商/外协：code、name、contact_name/phone、settle_type（1现结 2月结 3趟结）、status（0禁用 1正常）、remark；uk_code。
- `base_carrier_truck` 外协车辆：carrier_id、license_plate、truck_type_id、allowable_load/volume、status（0禁用 1正常 2在途）；idx_carrier。
- `base_carrier_driver` 外协司机：carrier_id、name、phone、license_no、status（0禁用 1正常 2在途）；idx_carrier。

### 接口（`@RequestMapping("carrier")`）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET/PUT | `/carrier`、`/carrier/{id}` | 承运商 CRUD |
| GET | `/carrier/{id}/trucks`、`/carrier/{id}/drivers` | 外协车辆/司机列表 |
| GET | `/carrier/capacity` | **可用运力池查询**（按车型/载重/区域筛选空闲外协车） |
| POST | `/carrier/dispatch` | 派单给外协（生成运输任务，标记来源=外协） |

### 要点
- `TaskTransport` 需区分**自营/外协**（可加 `carrier_id` 字段，为空即自营）。
- 外协结算走 FIN-1 的账单（对承运商的应付）。

### 承运商 KPI 考核（P1 扩展 · 行业基准：教材 TMS/芸柚/HashMicro · 2026-10-06 并入）

> **解决**：外协车"谁准时率高、谁货损多、谁异常多"无量化，靠感觉；KPI 考核给派单决策与结算（续约/淘汰）数据支撑。

- **指标**（纯统计查询，零表改动）：准时率（任务实际到达 vs 计划到达）、货损率（异常货损件数/总件数）、异常率（`iot_alarm_record` 按承运商聚合）、月完成趟次。
- **接口**（归 DSP-2 `@RequestMapping("carrier")` 扩展，P1）：`GET /carrier/{id}/kpi`（单承运商考核卡）、`GET /carrier/kpi/list`（承运商考核对比表，支持按月筛选）。
- **落点**：管理端承运商管理页加"考核"入口；考核结果仅展示，不做自动封禁（避免误伤，红线保留人工判定）。
- **DoD**：指标口径可解释（在接口文档注明计算公式）；可按月/承运商筛选；不建新表。

### 承运商端自助门户（P1 扩展 · 参照联云 SLMS 承运商端 · 2026-10-06 并入）

> **解决**：外协承运商（加盟）接单/节点确认/回单/对账全在微信电话里来回，无自助入口。联云把承运商端做成了独立产品线（SLMS：全部运单/未指派/待调度/已调度/待称重/已交接/已装车/运输中/到达签收/已签收/异常闭环/黑名单记录）。G7 亦强调外协"统一管理、可控运力池"。本项目 DSP-2 已有外协池，补**承运商端入口**即闭环。

- **形态（P1）**：司机端 App 增加"承运商"身份（复用统一账号体系，外协司机登录即承运商视图），不新开发独立 App——承运商/外协司机看到：**待接单任务（接/拒）→ 节点确认（装车/在途/到达/签收，同司机端节点）→ 回单上传（复用 POD）→ 运费对账（应收对账单确认，复用 FIN-1 账单）**。
- **接口**（归 DSP-2 `@RequestMapping("carrier")`，P1）：`GET /carrier/task/list`（待接单任务）、`POST /carrier/task/{id}/accept|reject`（接/拒单）、`GET /carrier/settlement/statement`（承运商应收对账单）。
- **权限**：承运商身份复用 driver 角色扩展或新增 `CARRIER` 角色（`sys_role` + `sys_role_menu` 授权），路由走 `/api/web-driver` 承运商视图。
- **不做**：独立承运商门户系统（SLMS 级）、承运商入驻审批流（P2 再议）。

### 承运商黑名单记录（P2 扩展 · 参照联云 SLMS 黑名单 · 2026-10-06 并入）

- 在 DSP-2 KPI 基础上，管理端可**人工拉黑/备注**承运商（原因+时间+操作人，入 `base_carrier` 扩展字段或新表 `base_carrier_blacklist`）；调度派单时黑名单承运商不出现在 `/carrier/capacity` 候选；仅人工判定，不自动封禁（红线）。
- **DoD**：黑名单可增删查；拉黑后调度候选自动排除；操作留痕。

## DSP-3 抢派双模

### 新建表
**表设计口径**（`disp_task_grab_record` 抢单记录；脚本落 `sql/dispatch/`）：
task_transport_id、driver_id、grab_time、result（0抢单成功 1被抢走 2取消）；**uk_task_driver（task+driver 唯一）即并发控制载体**、idx_task。

### 接口（`@RequestMapping("dispatch-assign")`）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/dispatch-assign/mode` | 获取派单模式（自动派 / 抢单，按机构配置） |
| POST | `/dispatch-assign/mode` | 配置派单模式 |
| GET | `/dispatch-assign/pool` | **抢单池**（司机端：可抢任务列表） |
| POST | `/dispatch-assign/grab` | 司机抢单（并发控制：同一任务只允许一人成功） |

### 要点
- 并发控制：抢单用**数据库唯一键 `uk_task_driver` + 条件更新**控制，不用分布式锁（不引中间件）。
- 抢单成功后更新 `DriverJob` 与 `TaskTransport.assignedStatus`。

---

# 第五梯队：IoT 增值（P2，依赖硬件，按需）

> **前提**：需采购/配备相应硬件。**无硬件则本期不做**。
> **上报通道统一复用 `xfwl-netty` 的 Netty + HTTP（`POST /netty/push`），不引 MQTT。**

## IOT-1 冷链温控
**表设计口径**（`iot_temperature` 冷链温控记录；脚本落 `sql/iot/`）：
truck_id、task_transport_id、temperature(℃)、humidity、device_time 设备采集时间；idx_task、idx_truck_time。
- 阈值配置进 yml（如 2~6℃），超阈值 → 告警类型 `TEMP_ABNORMAL`（写入 `iot_alarm_record`，复用 OPS-0 告警中心）。
- 接口：`POST /iot/temperature/report`（上报）、`GET /iot/temperature/task/{taskId}`（曲线 + 超标点）。
- 前端：温度曲线图（管理端 ECharts）+ 司机端超标提醒。

## IOT-2 重量 / 视频货损监控
**表设计口径**（`iot_weight` 载重监控记录；脚本落 `sql/iot/`）：
truck_id、task_transport_id、weight(kg)、device_time；idx_task。
- 异常判定：装货后/运输中重量骤降 → 告警 `WEIGHT_ABNORMAL`（防盗/防货损）。
- **视频复用已规划的司机视频能力（VID）**，不另起视频系统。
- **合规约束（R2-4 · P1 · 依据《合规开发优先级清单》#9 /《物流行业合规与监管要求》R2/R11）**：司机视频（盲区预警 / 驾驶员状态）数据须**直传监管平台或本平台**，不得经不可控的中间转发环节（GB/T 28181 / JT/T 1078 国标接入，或云厂商 RTC 直传）。视频/状态结果落库用于 OPS-5 驾驶行为分析，但**原始视频流路径须可审计、不落地到非授权节点**。
- 接口：`POST /iot/weight/report`、`GET /iot/weight/task/{taskId}`。

## IOT-3 能源（油卡 / 加油）结算
- 复用 OPS-4 的 `base_truck_fuel`（加油记录），扩展字段区分**油卡/现金**。
**表设计口径**（`base_fuel_card` 油卡；脚本落 `sql/base/`，或改为 `base_truck_fuel` 扩展字段二选一，实施时定）：
card_no、truck_id、carrier_id 外协、balance 余额、status（0禁用 1正常）；uk_card。
- 加油记录关联油卡 → 与加油站/油卡对账（`GET /fuel/reconcile`）。
- **前提**：需与加油站或油卡服务商有合作；无合作则不做。

---

## 2. 新建表 DDL 汇总
> 🔴 **表名订正（2026-10-18）**：表中 `pd_*` 前缀为历史写法，**建表时一律按八域前缀定名**（**财务=`oms_`**，如现役 `oms_settlement_order`；其余 `work_`/`base_`/`disp_`/`iot_` 按域，口径见《45》§3.10 与本卡 §0 L25 订正），脚本落 `sql/<业务域>/` 并走 create-migration；字段级 DDL 以 `sql/` 脚本为真源，本文只保字段口径。

| 表 | 库 | 用途 | 梯队 |
|---|---|---|---|
| `oms_freight_bill` / `oms_freight_bill_detail` | xfwl-work(或新建财务库) | 客户运费账单与明细 | FIN-1 |
| `work_driver_settlement` / `work_driver_settlement_detail` | 同上 | 司机趟次结算 | FIN-2 |
| `oms_customer_credit` / `oms_payment_record` | 同上 | 账期信用、收付款流水 | FIN-3 |
| （FIN-4 利润 **不建表**，查询计算） | — | — | FIN-4 |
| `disp_dispatch_decision_log` | xfwl-dispatch | 调度决策可解释日志 | DSP-1 |
| `base_carrier` / `base_carrier_truck` / `base_carrier_driver` | xfwl-base | 外协运力池 | DSP-2 |
| `disp_task_grab_record` | xfwl-work/xfwl-dispatch | 抢单记录 | DSP-3 |
| `iot_temperature` | xfwl-netty | 冷链温控 | IOT-1 |
| `iot_weight` | xfwl-netty | 载重监控 | IOT-2 |
| `base_fuel_card` | xfwl-base | 油卡 | IOT-3 |

> 另：`TaskTransport` 建议加 `carrier_id`（区分自营/外协），为空即自营 —— **唯一建议的存量表加字段项**。

## 3. 验收 DoD

- [ ] 能按客户账期生成运费账单，明细可追溯到每单；支持调账留痕与对账视图。
- [ ] 能按司机生成趟次结算单（逐趟明细），支持审核发放。
- [ ] 应收账龄报表可出；逾期账单能触发预警。
- [ ] 能算出单趟/单订单毛利，管理端利润驾驶舱可见排行。
- [ ] 路线规划引入车型/载重/体积/时效约束，且**调度决策可解释**（决策日志可查）。
- [ ] 外协承运商/车辆/司机可建档并派单，**且未调用任何外部厂商 API**。
- [ ] 抢单池可用，同一任务并发只一人成功。
- [ ] IoT 三项**仅在硬件具备时**落地；上报走 `/netty/push`，未引 MQTT。
- [ ] **未新增任何中间件**，JDK 21 编译通过，未在升级基线之上额外升级依赖。
- [ ] 阈值与规则进配置，无硬编码。

## 4. 实施顺序

```
第三梯队（先做，财务闭环）
  FIN-1 运费结算 → FIN-2 司机结算 → FIN-3 账期回款 → FIN-4 利润驾驶舱(依赖 OPS-4 成本)

第四梯队
  DSP-1 多约束可解释路线规划（痛点：黑盒）
  → DSP-2 外协运力池（加盟模式需要）
  → DSP-3 抢派双模

第五梯队（按需，硬件具备才做）
  IOT-1 冷链温控 → IOT-2 重量视频货损 → IOT-3 能源结算
```

> 依赖提醒：**FIN-4 依赖 OPS-4（`work_task_cost`）先完成**，否则无成本数据算不出利润。

---

# PRD 必选模块补全（P0-6 财务结算域 · 2026-10-07 补入；2026-10-18 P2 压缩）

> 原文 365 行按 P2 拍板压缩为要点版（PRD 方法论过程、Persona 细节、重复矩阵已删，原文见 docs-P2基线 tar）。**路由结论**：Fintech-compliance + Enterprise SaaS 双路由 → Heavyweight 深度 → 必选模块全套。⚠️ 段内 `pd_*` 表名为历史写法，建表按八域前缀定名（《45》§3.10），建表脚本走 `sql/settlement|work/` + create-migration。

## §PRD-1 量化痛点（4 项 · 已核实）

| 痛点 | 量化口径 |
|---|---|
| 计费只算不存（Drools 结果不落账单明细） | 历史运单计费明细可追溯率 = 0% |
| 月结对账靠 Excel 三方比对 | 单客户月结对账 ≥8 小时，差异率 ≥5% |
| 司机工资手算 | 单司机月度结算 ≥1 小时，差错率 ≥3% |
| 无利润报表 | 月度亏损线路识别滞后 ≥30 天 |

不解决的后果：无法签月结企业客户 / 对账工时随单量线性增长 / 应收账龄与坏账敞口不可控 / 不满足 R10-1 运价透明与 R6-3 审计留痕。

## §PRD-3 四子项与状态机

| 子项 | 内容 | 落点 |
|---|---|---|
| P0-6a | 运费明细落库（base_freight + extra_freight + amount；价格协议命中优先）+ FA-1 运费审计（rated vs invoiced 超 2% 转争议） | xfwl-oms/xfwl-work 内部 + `/api/manager/freight-bill` 聚合（D-09 三层） |
| P0-6b | 对账单生成（按客户+账期归集）+ 对账视图 + 调账留痕 | 同上 |
| P0-6c | 账单确认→发票申请；状态管理；不接真实税控（测试环境 `TEST-` 前缀自编票号） | 同上 |
| P0-6d | 单趟/单订单毛利（收入−直接成本），不建表查询计算，老板驾驶舱+排行 | xfwl-work 内部 + `/api/manager/profit` |

**状态机口径（唯一真源，禁硬编码）**：
- 账单（oms_freight_bill）：**实建三态** `0 待确认 → 1 已确认 → 2 已核销`（《07》§11 已登记；confirm 0→1、write-off 1→2 并联动结算单）。旧“待结算→部分结算→已结算→坏账”四态草案**已作废**，回款冲抵/坏账扩展态归 P1 届时按《07》回写流程增补。
- 司机结算单：`0 待审 → 1 已审 → 2 已发放`。
- 发票：`1 待申请 → 2 已申请 → 3 已开具 → 4 已邮寄 / 5 已作废`（作废须填原因，留痕）。

## §PRD-6 Fintech 合规模块（7 项 · 结论行）

1. **监管依据**：R10-1 运价透明、R6-3 审计 ≥6 月（发票操作留痕 operator/时间/invoice_id/amount/状态）。
2. **权限矩阵**：财务专员执行 / 财务主管审批（调账、发票、坏账、信用）/ 司机客户承运商仅本人 / 审计员只读+导出；RBAC 落地走 `sys_menu` + `@SaCheckPermission`（基线见《55》附录 B.3）。
3. **数据脱敏**：对账/账单视图手机号脱敏；金额字段 DECIMAL 精度统一 (12,2)。
4. **风控规则**：单笔调账 > 阈值需二次审批；坏账核销双人复核；账期信用超额自动拦截下单（月结客户）。
5. **审计日志**：调账/裁决/核销/开票/作废五类操作全留痕 ≥6 月。
6. **异常补偿与对账**：账单生成幂等（同客户+账期不重复生成）；回款冲抵按 FIFO；差异进争议池人工裁决。
7. **用户披露**：客户账单页展示计费规则口径（R10-1）。

## §PRD-7 质量门禁（要点）

- 14 项 PRD checklist 自检通过（背景量化/角色矩阵/状态机/指标/依赖/合规等，过程记录已删）。
- 反模式清查：无"自动智能一键"类空诺词；所有阈值进配置不硬编码。
- DoD 关键项：账单明细可追溯到每单；调账留痕可审计；发票状态机全流转可查；并发生成账单幂等；未新增中间件。
