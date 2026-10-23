# Claude Code 开发纪律与红线（心飞物流TMS）

> **文档版本**：v2.0 ｜ **最后更新**：2026-10-11
> **版本履历**：v1.0（初版）→ **v2.0（2026-10-10 重大修订）**：技术栈基线 SB3.5/Cloud2025.0/SCA2025.0.0.0、D-61 重构豁免注、鉴权体系 xfwl-auth(Sa-Token)+新 xfwl-gateway 口径（EC-09 闭合、AccessFilter/j2cache 成历史）。**语义版本规则**：重大口径/结构变更升主版本，局部条目修订不动版本、以日期注记留痕。
> **2026-10-11 局部注记**：§0 总纲、§4 本期范围新增「当前主战场 = 管理端 `xfwl-admin-ui-vue3`」「dev 安全极简口径（中间件口令 123456 可入配置）」「决策边界：用户定方向、实现层不请示（阿里规范/市场规范/国家规范/简单修复直接做）」，权威全文 SRS §1.6。
> **2026-10-13 局部注记**：新增 **§8 横切公共基线红线** 共 **10 条**（§8.1~§8.10），回收 65-v2 §L0 + 66 号基础横切登记的 D-01/02/03/04/05/06/07、X-01/02/03/04、F-02/F-03/F-04/F-05/F-07、BeanUtil:126 教训；每条配 ArchUnit/checkstyle/契约测试检测点。§8 与 65-v2 §L0 附录一一对应，加一条必须同步登记检测点。
> **2026-10-18 局部注记**：① §2 新增第 **15B/15C** 条**文档治理纪律**（"一处真源+他处指针"；史注＝订正即改写、禁轮次堆叠）——docs P0~P3 治理批次定案，配套文档自检脚本 `scripts/doc-check.sh`（00-索引 §六 命令真源，P3 抽出）；② §6 UI 红线条目号原与 §3 末条撞号（两处"20"），已顺改 21~27（全文无"第 2x 条"外部引用，零影响）。
> **用途**：每次启动编码任务前，先把本文档作为系统约束加载。违反红线的改动一律回退。
> **配套权威文档**：`06-开发规范与需求规格说明书.md`（以下简称《规格书》，当前 v3.0；功能需求详细规格已拆出至《55-功能需求详细规格.md》，`SRS §6.x` 引用按《55》解读）。本文档是其执行级摘要。

---

## 0. 总纲
业务优先、解决实际问题、不做花架子。**全栈技术栈按《阶段1 技术栈现代化周计划》升级（JDK 21 LTS + Spring Boot 3.5 + Spring Cloud 2025.0 + Gateway 网关；2026-10-10 修订：由 SB3.3/Cloud2023 上调，见 SRS §2.1）**，任何偏离须经人工确认，不得自作主张。

> **🔴 2026-10-11 用户拍板（当前最高优先，权威全文见 SRS §1.6）**：
> 1. **当前唯一主战场 = 后台管理系统 `xfwl-admin-ui-vue3`**——功能做全做完、页面设计好；闭环为"写完 → 提交 → 部署 → 浏览器可见可用"，后端只为打通管理端所需接口服务，不为架构完美阻塞页面。
> 2. **dev 阶段不做上生产才需要的安全功课**（内网/半成品/无真实用户/不上线）：中间件 dev 口令统一 `123456` 允许直接写进 yml/compose 默认值，不搞密钥外置、不加 dev 安全层；已落地的 Sa-Token/网关/租户机制保持运行，不深化也不拆除。
> 3. 底线仅两条：真实第三方凭证（地图 AK/微信支付宝商户密钥/短信）不入库走注入；上线前另立生产加固清单。
> 4. **决策边界（用户定方向，实现层不请示，权威全文 SRS §1.6-5）**：业务口径/新依赖/表结构重设计/跨模块架构/UI 方向/不可逆操作才问；**符合《阿里巴巴 Java 开发手册》的常规实现、符合市场通行规范（RESTful/状态码/分页/BCrypt/标准组件用法）、符合国家强制规范的合规细节、简单 bug/编译错误/文档已明确的内容，一律直接做不问**；历史"拍板/待确认"记录是留痕不是待办，照做不提请、要改才提；拿不准方向还是实现时默认按实现做并在交付说明里注明。
> 5. **数据库执行授权（2026-10-16 用户拍板，见硬事实 9）**：内网 IP 与测试/开发环境的库**可直接执行 DDL/DML**（备份被改表即可，不必逐次请示）；**公网 IP 与生产库一律禁止**。判据 = 是否公网可达 **或** 是否承载真实用户/生产数据，任一为「是」即禁止。

## 1. 宪法级红线（违反即不合格）
1. **JDK 固定 21 LTS**（Eclipse Temurin），后端按 Spring Boot 3.5 / Spring Cloud 2025.0 迁移，`javax.*`→`jakarta.*`。
2. **后端依赖按全栈升级迁移**：Spring Boot 2.2.5→3.5、Spring Cloud→2025.0、Alibaba→2025.0.0.0，MyBatis Plus/Druid/Shiro/Drools/Lombok 同步升级至兼容版本；FastJSON 1.x 替换为 FastJSON2 或 Jackson（消除 CVE）。迁移范围与排期以《阶段1 技术栈现代化周计划》为准。
3. **安全 CVE 随升级一并解决**：FastJSON 1.x→2.x、Shiro 升级至安全版本等随全栈升级（第2条）自然消除；升级后若仍有未覆盖的高危 CVE，须登记并评估。
4. **不引入新中间件**（MongoDB/HBase/Spark/Hive/ES/RocketMQ/Sentinel/SkyWalking 等是用户路线图，不是缺失 bug，禁止"顺手"补齐）。⚠️ **XXL-Job 例外（D-21 2026-10-18 修订口径）**：调度域（xfwl-dispatch）已引入 XXL-Job 为既定技术选型；红线＝**业务侧不得擅自新增中间件/调度组件**，确需新增须走《60-技术栈评估与演进路线》评估流程拍板。
5. **不随意重写/不重构** 既有文件（含 `xfwl-admin-ui-vue3`），除非任务明确要求；**「管理端 Vue3 升级专项」任务下允许改动 `xfwl-admin-ui-vue3` 源码与结构**（目标栈 Vue3+Vite+Element Plus+Pinia，见《开发规范》§2.2）。（**2026-10-10 豁免注**：**「全量重构专项」（定案 `D-61`）任务内系统性重构获得授权**——豁免范围、行为护栏（对外行为等价：契约《08》/状态编码《07》/回归《27》）、架构现状（xfwl-auth/xfwl-gateway 替代 pd-authority）均以 `D-61` 为准；专项外本条照常生效。）
6. **网关已完成 Zuul 1.x → Spring Cloud Gateway 迁移**（✅ 2026-10-14 复测，2026-10-18 订正路由数：`xfwl-gateway` = SCG + WebFlux + Sa-Token 1.46，路由硬声明 **8 条**（新增 `/api/file/**` → xfwl-file-server）、`discovery.locator.enabled: false`；旧 Zuul 与 `ignored-services:'*'` 口径全部作废）。Sentinel 仍属用户路线图，不在本期强制引入。
7. **不跨模块写业务**：订单→xfwl-oms、调度→xfwl-dispatch、作业→xfwl-work、基础→xfwl-base、轨迹→xfwl-netty。

## 2. 开发行为纪律
8. **先读后写**：改动前必须 Read/搜索确认现状与调用关系，禁止凭记忆写代码。
9. **复用既有能力**：三端 REST API（`xfwl-web-customer/driver/courier`，见《规格书》§5）已存在，移动端直接调，**后端能不改则不改**。
10. **禁止过度设计（YAGNI）**：不引入当前用不到的抽象/模式/框架/缓存/MQ。
11. **禁止硬编码**：cron/阈值/地图 Key/坐标类型/上报间隔一律外部化到配置文件。
12. **坐标存储统一 WGS84**：后端统一存储 **WGS84（GPS/北斗原始）**；微信小程序用腾讯 GCJ-02、管理端/司机端百度地图用 BD-09，展示层按地图供应商做 WGS84→目标系转换（见 BA-19）。
13. **禁止裸请求**：前端所有 `uni.request` 必须过 `common/request/index.ts` 拦截器（JWT 自动附加；401 清 token 跳登录重登，**后端无 refreshToken，禁实现静默刷新**）。（文件以实际工程后缀为准）
14. **认证/计费规则放后端**：前端只调用不计算核心业务规则（运费、状态机、计费）。
15. **异步 Feign 必须传播请求上下文（2026-10-12 定）**：请求处理过程中，凡在 `CompletableFuture` 等异步线程里发起 Feign 富化调用，**禁止裸用 `CompletableFuture.supplyAsync`**——`RequestContextHolder` 基于 ThreadLocal，不会传到异步线程，Feign 因此丢失 `tenantId`/`userid` 头，下游多租户插件回落 `tenant_id=0`，字典/关联名查询恒空（车型关联货型、车辆车型/车队列名等回填 `null`，前端 `v-for` 取空属性还会整页崩）。统一用 `xfwl-common` 的 `AsyncRequestHeaders.supplyAsync(...)`（提交线程抓头快照、子线程执行后置入、`finally` 清理防串租户）；manager/driver/courier/customer/dispatch 五个服务的 `XfCompletableFuture` 已全量迁移到此入口，Feign 出口 `RequestHeaderInterceptor` 已支持"无请求上下文时回退读异步快照"。新写异步富化一律走该工具；前端对后端富化数组（如 `goodsTypes`）仍要过滤 `null` 兜底。
15A. **代码变更必须走 Git 仓库，禁止 scp/直改服务器源码热部署（2026-10-13 用户定，违反即返工）**：唯一允许的部署链路是 **本地改 → 本地编译/自测 → `git diff` 报用户确认 → commit（中文 `type: 简述`，结尾 Co-Authored-By）→ push 双远端 → 服务器 `git pull` → ci 容器构建 → compose 滚动部署 → 接口回归**。严禁 scp 源码、严禁 ssh 直改服务器工作区后构建（脏工作区/分叉/pull 冲掉/覆盖并行会话/无留痕）；例外仅限"仓库链路故障+用户当场同意"，事后回填仓库并登《99》台账。dev 库数据修正先报语句与 WHERE 清单经确认再执行，并同步修订 `sql/` 种子脚本。
15B. **文档口径"一处真源 + 他处指针"（2026-10-18 立，docs P0~P3 治理批次定案）**：同一事实（账号口令、颜色 Token、环境契约、DDL、状态码、字段名、专项方案细化等）只许一份**真源文档**承载全量，他处一律写"见《真源》§x"指针，**禁止复述取值**——两处维护必漂移（前车鉴：账号口令曾内联散写 10+ 处，P2 才清干净）。现行真源锚点：账号口令《57》／字段契约《08》附录 A.1／状态枚举《07》§〇之二／颜色 Token《13》§2.1／三端公共环境契约《51》§三·A／安全实施细化《72》《74》／库表结构《45》。写新内容前先定真源落点；发现第二处复述即改为指针。
15C. **史注纪律＝订正即改写、禁轮次堆叠（2026-10-18 立）**：订正过时口径时**直接把正文改写为现行口径**，至多原处留一行"（原写 xxx，2026-10-18 按 yyy 订正）"式史注；**禁止**在正文按轮次堆"v1 改了什么、v2 又改了什么"的流水（文件头版本履历一行一版可以，正文内禁止）。判据：读者只需要"现在的口径＋为什么和以前不一样的一句话"，不需要重放决策过程（反面教材：《69》§10.2 曾四版处置方案并存，P2 压缩 44→9 行才收口）。

## 3. 质量与交付
16. **编译保证**：每次改动后受影响模块 `mvn -o -q compile` 通过（本地 JDK 21 LTS）；前端 `npm run build` 通过。
17. **契约同步**：新增/变更接口必须更新《规格书》§5、§6；前后端字段一致。
18. **日志脱敏**：手机号/证件脱敏；禁止 `System.out.println`。
19. **无 N+1**：禁止循环内逐条查库，批量用 `in`/批量接口。
20. **文档同步**：改动同步到 `docs/` 相关文档；新增 API 补入 §5。

## 4. 本期范围（做）
- **🔴 当前主战场（2026-10-11 用户拍板，优先级高于本节其余全部条目；权威全文 SRS §1.6）**：
  - **后台管理系统 `xfwl-admin-ui-vue3` 功能完成度与页面质量**——按《55》《14》《16》把页面做全、做完整、设计好，每完成一项即构建部署到 dev（`bash /data/xinfei-deploy/deploy-fe.sh admin`），**浏览器可见可用 = 该项 DoD**；
  - **dev 安全极简口径**：中间件口令统一 `123456`（MySQL/Redis/RabbitMQ/Nacos），允许默认值入 yml/compose；不主动做生产级加固；已跑着的认证/网关/租户机制保留运行，不深化不拆除。**例外（2026-10-11 用户拍板）：Nacos 控制台鉴权已开启**（密钥本已配置、补齐开关），账号 nacos/123456，见硬事实 7——此为用户显式要求，不属于"主动加固"，后续不得当作多余安全层关掉。底线：真实第三方凭证不入库、上线前另立加固清单。
- P0（排序让位于上述管理端主战场）：三端移动前端（客户/快递员/司机）→ POD 电子签收 → 运输闭环看板 + 在途监控 → 统一登录。
- P1：承运商/外协运力、运单拆合单、回单归档核销联动计费、附加费与多维对账、司机结算、异常类型化+索赔、节点通知、客户 Share Link、绩效考核、移除 Seata 并收口本地事务、状态回调补偿、调度可视化。
- P1（**用户明确新增的真实需求，不得当花架子砍掉**）：
  - **北斗定位（BDS）**：`LocationRecord`/`LocationEntity` 补 `coordSystem`+`source`（P0 最小改造；统一坐标转换与 JT/T 808 完整接入为 P1）；车载终端 JT/T 808 接入（复用现有 Netty）。
  - **司机视频接入**：云厂商 RTC 或 GB/T 28181 / JT/T 1078 国标，**不自建 RTMP 集群**；后端当前无任何 rtc/stream 接口，前端入口先置灰占位。
- P1：**支付闭环**（到付 P0 / 在线支付 P1）。后端 `PUT /mailing/pay/{id}` 与 `Order.paymentMethod/paymentStatus` 已存在。

## 5. 本期不做（花架子，一律不启动）
- EDI/ERP/多式联运/BI/重型 WMS/多租户 SaaS/预约月台。
- ⚠️ **冷链**：定位 **P2 储备**（依赖温控 IoT 硬件），温度越限告警 `TEMP_ABNORMAL` 统一落 `iot_alarm_record`、复用 OPS-0 告警中心（见《21》§IoT、《01》P2、《20》§2）；非本期 P0/P1，但不再视为"永不做"。
- 新中间件引入（MongoDB/HBase/Spark/Hive/ES/RocketMQ/Sentinel/SkyWalking 等用户路线图项，非缺失 bug，禁止"顺手"补齐，与第4条一致）。⚠️ XXL-Job 已由调度域引入为既定选型（D-21 2026-10-18 修订），不属"待引入"项；红线＝业务侧不擅自新增中间件/调度组件，新增须走《60》评估流程。

## 6. UI / 页面红线（大模型写页面最易翻车，必须遵守）
21. **只用组件库**：移动端 100% 用 uview-plus 组件，**禁止**用 `<div>`+`<style>` 替代布局/按钮/卡片。
22. **Token 全集中**：颜色/间距/字号/圆角/阴影只允许引用 `uni.scss` + `common/styles/variables.scss`，**禁止**页面内写死 hex/px/魔法数字。
23. **三端同视觉**：客户/快递员/司机共用同一套 Token 与组件，仅角色化强调（司机端按钮更大），**禁止**三端各一套。
24. **结构走模板**：六类页面（列表/详情/表单/地图/任务大厅/POD）套用《13-UI设计规范与页面模板.md》§4 骨架，**禁止**自由布局。
25. **三态必做**：加载中/空/错误统一用组件库，缺一不可。
26. **禁止第二套 UI/图标库**、禁止 emoji 当图标、禁止缺三态、禁止绕过 `u-navbar`、禁止 `uni.request` 裸调（过拦截器）。
27. 写任何页面前先读《13-UI设计规范与页面模板.md》。

## 7. 开工前必做
- 读《规格书》第 1 章 + 第 12 章 + 本文件 + 《13-UI设计规范与页面模板.md》。
- 确认任务归属模块与对应 API（§5）。
- **硬事实（违反即失败）——🔴 唯一真源＝根 CLAUDE.md §4，本节为摘要，冲突时以 CLAUDE.md 为准（2026-10-18 逐条校正现值）**：
  1. **所有请求必须走网关，且必须带端前缀**（真源 `xfwl-gateway/src/main/resources/application.yml` routes 段，2026-10-18 实测 **8 条路由**全部 `StripPrefix=2`，`discovery.locator.enabled: false` ⇒ 不在该文件里的路径一律 404，**没有"资源表注册"这一层**）：
     `/api/manager`（管理端 → xfwl-web-manager）、`/api/web-driver`（司机）、`/api/web-courier`（快递员）、`/api/web-customer`（客户）、`/api/netty-service`（轨迹）、`/api/auth`（认证）、`/api/v1`（认证下的菜单等 v1 接口 → xfwl-auth）、`/api/file`（文件 → xfwl-file-server，其中 `/api/file/view/**` 匿名放行）。
     ⚠️ 管理端前缀是 `/api/manager`，**不是** `/api/web-manager`（旧写法作废）。
     **直连 8161~8164 会被 `TokenAuthInterceptor` 401 拒绝**（端口仅供健康检查）。
  2. JWT 请求头字段是 **`token`**（不是 `Authorization`）；有效期 7200s，**无 refreshToken** → 401 清 token 重登。
     ✅ **管理端鉴权已统一（2026-10-10 实测，EC-09 闭合）**：旧 Vue2 `pd-admin-ui`（OAuth2 bearer + refresh_token）已删除；新 `xfwl-admin-ui-vue3` 已用统一 `token` 头（`src/utils/request.ts`）且无 refreshToken（`token-refresh.ts`：401 清状态跳登录）。本红线"JWT 头=token、无 refreshToken"现约束**全部前端**。
     后端登录 **已存在** `POST /api/auth/anno/login` + `GET /api/auth/anno/captcha?key={uuid}`（源码 `xfwl-auth/.../controller/AnonController.java` `@RequestMapping("/anno")`，**只有** captcha/login/logout 三个端点），**不得新建登录服务**。
     契约实名：入参 `LoginParamDTO{tenantCode, account, password, key, code}`；出参 `R<LoginDTO>{token, userId, account, name, avatar, orgId, tenantId, permissionsList}`（**扁平结构，无 `user` 嵌套、无 `Token{token,expire}`**）。旧 `/anno/check`、`/anno/verify`、`/anno/token` **已不存在**，勿再按旧 Vue2 契约对接。
  3. 轨迹查询只认 `businessId`+`type`（**无 `orderId`**）；`/trace/page` 才支持 `transportTaskId`。客户按订单查轨迹需后端改造，**不得前端伪造参数**。
  4. **新增对外接口的权限落点 = `sys_menu`（含按钮型 `perm`）+ 角色绑定 `sys_role_menu`**（无自动注册，菜单/权限须手工登记，否则登录后侧边栏与按钮不可见）。现行鉴权链（**2026-10-13 实测，2026-10-18 订正服务/组件名与白名单**）：网关 `SaTokenConfigure` 只校验登录态（白名单 `/api/auth/anno/**`、`/api/file/view/**`）→ `ForwardSameTokenFilter` 注入 `Same-Token/tenantId/userid` → 下游 `TokenAuthInterceptor`（xfwl-common）兜底 → 接口级 `@SaCheckPermission`（**2026-10-14 复测＝20 个 Java 文件 / 117 处，仅 `xfwl-auth` 7 + `xfwl-web-manager` 13，其余服务 0 处**；数据源 `StpInterfaceImpl` 读 `sys_menu`(type=B) 的 `perm`，ADMIN 角色放行 `*`）。**禁止**再往旧 `sys_resource`/资源清单表插数据、也**禁止**据其判断接口是否放行。
     ❌ **禁止**把三端前缀加入忽略鉴权绕过——跳过 token 校验会导致下游 `TokenAuthInterceptor` 401。
  5. **网关鉴权关键配置（2026-10-10 更新，2026-10-18 订正白名单）**：j2cache 修复为**旧网关历史**（新 `xfwl-gateway` 用 Sa-Token + Redis，不用 j2cache）；新增白名单/过滤改 `SaTokenConfigure`（匿名放行 `/api/auth/anno/**`、`/api/file/view/**`）与 `ForwardSameTokenFilter`（注入 Same-Token/tenantId/userid）。
     验收探针：`curl -o /dev/null -w "%{http_code}" "http://192.168.20.130:8760/api/auth/anno/captcha?key=probe"` → **期望 200**（经网关到 xfwl-auth）。菜单端点**已实现（`D-42`，2026-10-13 sys_menu 迁移上线）**：`GET /api/v1/menus/routes` 返回按角色 RBAC 过滤后的路由树（无 token 期望 401，带有效 token 期望 200）。
     若又现 `500`：**先看网关日志定位过滤器**（新网关错误前缀不再是 `pre:AccessFilter`），不要盲目重启。
  6. **严禁新建/重写状态机**：`StateTransitionValidator` 已实现且在用（`TaskTransportServiceImpl:243/287/331`、`TransportOrderServiceImpl:74/84`）。真实缺口是**跨端同步桥**（司机发车未联动 Order 状态 6=运输中），不是校验器。
  7. **环境地址**：服务实跑 Docker 网 `172.21.0.x`，宿主机 `192.168.20.130`；Nacos `8848`（ns `1cb93ce4-dc0e-4730-b759-d35fd7ed93c3`，group **`xinfei-tms`**）。**Nacos 鉴权 2026-10-11 起已开启**（`NACOS_AUTH_ENABLE=true`，无 token 访问 403）：dev 运行账号 **`xinfei/123456`**（命名空间级读写，角色 `ns-xinfei-tms`（2026-10-15 傍晚由 `ns-pinda-tms` 改现名）；2026-10-15 全量收敛后 14 个微服务 + CI 实测全用此账号，真源＝`/data/ci/apps.env`；yml 默认值 `${NACOS_USERNAME:nacos}/${NACOS_PASSWORD:123456}` 仅在无注入时兜底）；超管与备用账号见《57》§一。新增服务必须配 Nacos 账号，否则拉配置 403 启动失败。**真实第三方密码不入库**；dev 中间件统一口令 123456 按 §5.2 口径可入 yml/compose 默认值。
  8. 🔴 **数据库口径（2026-10-10 重构，现行）**：旧「一服务一库、8 个物理库」已删除（删除前完整备份 `legacy_drop_backup_20261010.sql`），现 = **单一物理库 `xinfei_tms`（八域设计内 51 张表；2026-10-14 实测 BASE TABLE = 55，差额 4 张非设计内表见《45》§3.9，其中 `xfwl_payment_order`（2026-10-15 由 `pd_payment_order` 改名）违反八域前缀纪律）+ 业务表 `tenant_id` 多租户**；表按 `sys_/dict_/base_/oms_/disp_/work_/member_/iot_` 八域前缀组织（**逻辑分域、物理单库、未来可拆**）；雪花 BIGINT 主键、统一审计字段、`deleted` 逻辑删除、无物理外键（⚠️ 实测例外：在用表 `xfwl_payment_order.id` 是 VARCHAR(64) 雪花字符串且无八域前缀，见《45》§3.9）；**订单状态 = 紧凑 TINYINT 1~12（4~5 位形态值如 23xxx = 幻觉，停手查《07》）**。建表权威 DDL = `sql/*.sql`，汇总《45》、枚举《07》；旧 docs/sql、TMS项目建库脚本禁止再执行。跨域纪律 = 写严格归属、读可适度跨域。种子登录：企业编码 `xinfei` / `admin` / `123456`。
  9. 🟢 **数据库执行授权（2026-10-16 用户明确授权，与 §8 红线不冲突）**：
     - **内网 IP 的数据库、测试环境、开发环境 → 允许直接执行 DDL/DML**，包括 `sql/` 下的幂等迁移脚本，执行前 `mysqldump` 备份被改表即可，**不再逐次请示**。
     - **公网 IP 的服务器、生产数据库 → 仍属红线，一律禁止**（含其上的 DDL、DML、迁移脚本）。
     - **判据**：目标地址是否公网可达 **或** 是否承载真实用户/生产数据，**两者任一为「是」即禁止**；都不满足才可执行。
     - 现行开发库口径：`xinfei_tms` @ `192.168.20.130:3306` = 用户笔记本虚拟机的 **dev 单库，非生产**，属可执行范围；未来云服务器公网部署后，其库一律按生产红线处理。
     - 执行纪律：迁移脚本必须 `IF NOT EXISTS` / `ON DUPLICATE KEY UPDATE` 幂等可重跑；带 `+migrate Up/Down` 段与作者日期；执行后立刻跑校验 SELECT 回报结果。

- **报障前必做**：接口报错时**先探活再读代码**。
  ```bash
  # 1) 探活：404=进程在，502/拒绝=进程挂了
  curl -s -o /dev/null -w "%{http_code}\n" --max-time 5 "http://192.168.20.130:9000/actuator/health"
  # 2) 对比：直连服务 vs 经网关，谁错？（**2026-10-10 更新**：旧 `menu/router` 探针随 pd-authority 删除失效，改用 xfwl-auth 免登录端点 `/anno/captcha`）
  curl -s -o /dev/null -w "%{http_code}\n" "http://192.168.20.130:9000/anno/captcha?key=probe"
  curl -s -o /dev/null -w "%{http_code}\n" "http://192.168.20.130:8760/api/auth/anno/captcha?key=probe"
  ```
  直连 200 + 经网关 500 → **网关侧问题**；两边都 502 → **进程挂了，重启即可**。
  📖 完整流程见《28-环境故障排障手册.md》。

- **四个已踩过的坑（改相关代码前必读）**：

  | 坑 | 现象 | 正确做法 |
  |---|---|---|
  | **菜单为空** | 登录成功但**左侧菜单为空** | 路由＝`GET /api/v1/menus/routes`（`controller/v1/MenuController.routes()`，`StpUtil.getLoginIdAsLong()`）→ `sys_user_role` → `sys_role_menu` → `sys_menu`(status=1)；**角色没绑菜单就是空，不是代码 bug**；`ADMIN` 角色放行全量 |
  | **改权限后"不生效"** | 改了 `sys_menu`/`sys_role_menu` 感觉没变 | xfwl-auth 无进程内缓存（实测无 `@Cacheable`/Caffeine）→ **刷新页面即生效，无需重启任何服务** |
  | **新接口 404** | 服务里写了方法，前端调不通 | 路由只在 `xfwl-gateway/src/main/resources/application.yml` 声明（`discovery.locator.enabled: false`＝内部服务默认不对外）；新增对外接口须显式加 `Path=/api/<端>/**` + `StripPrefix=2`，**没有"注册资源表"这一层** |
  | **Nacos group 混淆** | 改了配置不起作用 | 全部服务显式用 `group: xinfei-tms`；`DEFAULT_GROUP` 已清理，勿往那里写 |
  | **状态值口径** | 前端写死数字、各端理解不同 | 🚫 **禁止硬编码状态数字**（`status == 6` ❌）。取值与中文对照一律查《07-数据字典与枚举口径.md》；⚠️ 陷阱：订单状态仅 1~12，4~5 位形态值（23xxx）不存在；`DriverJobStatus.CONFIRM`="改派" 而 `TransportTaskStatus.CONFIRM`="待确认"（**同码不同义**）、`MANUAL_DISTRIBUTED` 中英语义相反 |

  ⚠️ **"接口返回 200" ≠ "鉴权生效"**（旧 AccessFilter 清单关卡已随 pd-authority 删除；网关只做登录校验 + 端前缀角色 `checkRoleOr`，细粒度权限＝`sys_menu.perm` + `@SaCheckPermission`，实测仅 `xfwl-auth`/`xfwl-web-manager` 在用，其余服务 0 处）。验证权限真生效：换一个合法但无该权限的角色 Token 重调，仍 200 即说明权限点未落地。

- **开工前必读顺序**：《05-需求文档v2_现状梳理与竞品借鉴.md》（**最先读，现状实测**）→ 本文件 → 《13-UI设计规范与页面模板.md》→ 《06-开发规范与需求规格说明书.md》第 1/12 章。
  环境类事实以《26-联调就绪检查清单.md》《28-环境故障排障手册.md》为准。

## 8. 横切公共基线红线（2026-10-13 新，测试计划 v2/65 号 §L0 + 66 号基础横切登记 D/F/X 回流）

> **背景**：2026-10-13 全量测试（62~66 号）证明——**同一类横切问题被不同 AI 会话反复写错**：分页 normalize、异常 advice、空集合守卫、`Long id` 校验、网关角色绑定、字段契约命名。本节把每条钉成红线，配对应检测点（见 65-v2 §L0 附录），新写代码违反即回退。

### 8.1 【D-02】`CompletableFuture` 里禁止把 Set/List 直转 ArrayList 打 Feign
- **现象**：`XfCompletableFuture.areaMapFuture` 空 `areaSet` → `new ArrayList<>(areaSet)` = 空 List → 下游 `findAll(null, [])` 无 WHERE 条件 → **`dict_area` 全表 665,552 行** → xfwl-auth/xfwl-web-customer heap 95%+ → 熔断。5 处同源（dispatch:123 / courier:29 / customer:27 / driver:71 / manager）。
- **红线**：**任何 `xxxFeign.findAll/list/page(集合参数)` 之前，必须显式判空**：`if (CollectionUtils.isEmpty(set)) return Rx.list(Collections.emptyList());`（或对应 `Map` 空）。禁止"相信下游会处理空集合"。
- **检测点**：ArchUnit 自定义规则 `noFeignCallWithUnguardedCollection` + SpotBugs `NP_NULL_ON_SOME_PATH` + 单测：空入参必须走降级、不打网络。

### 8.2 【D-03】`@ControllerAdvice` 每服务自写=反模式，公共异常映射必须下沉 `xfwl-common`
- **现象**：manager / driver / courier / customer / dispatch 各自写 `GlobalExceptionHandler`，`Exception` 分支返回的 `code`/`msg`/HTTP 状态码**语义不一**（D-07/X-04），前端无法统一处理。
- **红线**：**新模块禁止再写自己的 `GlobalExceptionHandler`**；公共异常（业务异常、`MethodArgumentNotValidException`、`NotLoginException`、`FeignException`、未捕获 `Exception`）由 `xfwl-common` 提供基类，服务只允许在**特有异常**上 extend。
- **检测点**：ArchUnit 规则 `onlyOneGlobalExceptionHandlerPerStack`（同栈包内 `@ControllerAdvice` 只允许 `Xxx extends PdBaseAdvice`）。

### 8.3 【D-01 / D-04】`Long.valueOf(外部字符串)` 必须走工具类，禁止裸调
- **现象**：`CargoController.java:224` 等 **27 处**裸调 `Long.valueOf(keyword)`，用户搜中文关键字 → `NumberFormatException` → 500。
- **红线**：接收外部字符串转 `Long` 一律用 `xfwl-common` 的 `IdUtils.parseOrNull(String)`（空/非数字返回 `null`）；禁止在 controller/service 直接 `Long.valueOf` / `Long.parseLong` 处理用户输入。
- **检测点**：checkstyle `IllegalMethodCall`（`Long.valueOf` / `Long.parseLong` 参数来自 `HttpServletRequest`/`@RequestParam String` 时报警）+ ArchUnit 规则 `noRawLongValueOfInControllers`。

### 8.4 【D-05】分页参数 normalize 以 xfwl-netty 为唯一基准，禁止各写各
- **现象**：`page=0` / `size=-1` / `size=100000` 在不同服务行为各异：xfwl-netty 正确 clamp；manager 用 `Integer.MAX_VALUE`；driver 直接透传 → SQL 报错。
- **红线**：**所有分页入参必须过 `xfwl-common` `PageQuery.normalize()`**，字段名统一 `pageNum`/`pageSize`（沿用 MyBatis-Plus），`pageSize` 上限 500，`pageNum` 下限 1。新写分页接口禁止裸接收 `page`/`size`/`limit`/`offset`。
- **检测点**：ArchUnit `controllerPageParamMustUsePageQuery`（controller 方法签名出现 `int page|int size|Integer limit` 直接失败）。

### 8.5 【X-01】网关 `/api/{端名}/**` 必须显式绑定角色，`checkLogin` 不等于鉴权
- **现象**：`SaTokenConfigure.java:41-43` 仅 `SaRouter.match("/**", r -> StpUtil.checkLogin())`，未做角色路由。本轮实测：driver/courier/customer/admin 四端 token 交叉调用彼此端点，**10/12 组合 200**（当前靠 `RequestContext.getUserId()` 过滤看起来"数据是空"，一旦第 2 租户补齐即真实越权）。
- **红线**：网关 `SaTokenConfigure` **每个 `/api/web-xxx/**` 前缀必须 `SaRouter.stop()` 前加 `StpUtil.checkRole("DRIVER")`/`COURIER`/`CUSTOMER`/`MANAGER`**；只 `checkLogin` 不 `checkRole` 视为未完成。
- **检测点**：ArchUnit `gatewayMustBindRolePerPrefix`；集成测试 `cross-end-token-matrix` 必须全部 403（探针见 docs/测试/harness/probe-basics.cjs §D）。

### 8.6 【X-02】`@PathVariable Long id` 必须用数字正则约束，禁止通配吞具体动词
- **现象**：`TransportOrderController.java:112-113` `@GetMapping("/{id}")` + `@PostMapping("/page")` 并存，Spring 把 `GET /page` 匹配为 `/{id}`="page" → `Long.valueOf("page")` → 500。
- **红线**：任何 `@GetMapping("/{id}")` / `@PostMapping("/{id}/xxx")` 等**ID 通配**，一律写作 `@GetMapping("/{id:\\d+}")`；同 controller 存在 `/page` `/list` `/count` `/export` 这类**具体动词路径**时，ID 路径**必须**收紧数字正则。
- **检测点**：checkstyle `PathVariableMustHaveRegex` + ArchUnit `pathVariableLongMustBeDigitScoped`。

### 8.7 【D-07 / X-04】HTTP 状态码与业务 `code` 映射统一表，禁止各写各
- **现状分布**：401 网关返回 `code=401`, xfwl-auth 返回 `code=50001`；403 三端各有；404 部分服务返回 200 + `code=404` 语义混乱。
- **红线**：**HTTP 状态码 = 传输层事实**（400 参数错、401 未登录、403 越权、404 路由不存在、405 方法不支持、500 未捕获异常）；**业务 `code` = 领域语义**（如 `ORDER_NOT_FOUND`），映射表真源在《08》附录。新增异常必须回查此表；不允许"500 包 404" / "200 包 500"。
- **检测点**：契约测试 `error-code-matrix`（docs/测试/66 号 §2 X-04 探针）。

### 8.8 【BeanUtil:126 / xfwl-netty 分页】改代码前必 grep 同族已有实现
- **现象**：同族功能（区域缓存、字段回填、分页、异常处理）在多个模块各自实现，新写的 AI 会话不知道已有版本 → 二次实现不一致（**F-03/F-05/F-07 均属此族**）。
- **红线**：动以下 5 类代码前，**必须先 `grep -r` 同族实现**、并在 commit message 列出"参考了 XXX:<行号>"：
  1. `XfCompletableFuture.*` 富化 — 5 处
  2. `GlobalExceptionHandler` — 每模块 1 处
  3. 分页 `page`/`size` 参数处理 — 每 controller
  4. `Result` / `R` / `PageResult` 组装 — xfwl-common
  5. 字段名 `xxxAgency` / `userNumber` / `count` — 见《08》附录
- **检测点**：Code Review 检查项；CI 编译前静态"同族 grep 报告"（P3 落地）。

### 8.9 【D-06 / X-03】Feign 客户端必配超时 + TimeLimiter 不许关
- **现象**：`xfwl-web-customer` Feign 无 `connect-timeout / read-timeout`，且主动设 `resilience4j.timelimiter.configs.default.cancel-running-future=false`（等价"关 TimeLimiter"）→ 下游熔断时本服务 30s 挂起。admin 打 `/api/web-courier/**` 实测同 30s 超时（X-03）。
- **红线**：**每个 `@FeignClient` 服务必须在 yml 显式配**：`feign.client.config.<name>.connect-timeout` ≤ 3000ms、`.read-timeout` ≤ 5000ms；`resilience4j.timelimiter` 对 Feign 组**必须为 enabled**（`cancel-running-future=true`）。禁止在 `application.yml` / `xfwl-gateway.yml` 里出现"关闭 TimeLimiter"的配置。
- **例外**：`AsyncRequestHeaders` 走 `disable-thread-pool=true` 的 semaphore 同线程模式（c76f3a6 租户透传根因），此时 TimeLimiter 语义由 read-timeout 承担，不许再叠一层。
- **检测点**：ArchUnit `FeignClientMustHaveTimeout`（扫每个 `@FeignClient` 服务对应 yml 键存在）+ 契约测试 `feign-timeout-probe`（下游 mock hang 6s → 上游必须 ≤5s 返回 fallback，不允许 30s）。

### 8.10 【F-04】空返回必须 `null`，禁止 new 空壳 DTO
- **现象**：`CargoController.java:264-265` `Result.ok().put("data", new CargoTranTaskDTO())`——DTO 空构造 → 前端 `v-if="detail.xxx"` 全 truthy → 渲染假卡片。
- **红线**：**没有数据 = `data:null`**，不许 `new XxxDTO()`。所有 `Result.ok().put("data", ...)` / `R.ok().data(...)` 分支，若语义是"资源不存在"必须显式 `null`；前端相应判 `data == null` 而不是判对象字段。
- **检测点**：ArchUnit `noEmptyDtoAsSuccessData`（扫 controller 返回值 `new \w+DTO\(\)` 出现在 `Result.ok` / `R.ok` 里即 fail）+ 契约测试 `empty-detail-returns-null`。

---

**§8 与测试计划联动**：本节 10 条（§8.1~§8.10）一一对应 `docs/测试/65-全量测试计划_v2.md` §L0 附录「§12 §8 条目 ↔ ArchUnit/checkstyle 规则名」表；每加一条 §8 红线，必须同时在 65-v2 §L0 登记检测点，否则视为未完成。

---
*任何与《规格书》冲突的"优化"默认无效，须先修订文档并经人工确认。*

