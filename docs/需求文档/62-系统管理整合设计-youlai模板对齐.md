# 62-系统管理整合设计：youlai 模板全家桶与心飞体系对齐

> 状态：**部分已实施（2026-10-16 核验）**——菜单动态路由（D-42）、`sys_menu` 权限码体系已按《58》落地（41 个权限码 100% 落库、零孤儿）；本文余下未实施部分仅为设计参考。🔴 **实现口径以《58-管理端菜单与路由开发规范》+ `sys_menu` 库表双向核对为准，与本文冲突处以《58》为准**；原"待评审（2026-10-13 初稿）"状态作废。SYS-04/SYS-05 两项用户拍板已登记为《03》**D-68/D-69**（2026-10-18 补登记）。
> 归属：SYS-01 用户 / SYS-02 角色 / SYS-03 菜单（含 D-42 动态路由）/ SYS-04 资源权限 / SYS-06 日志。
> 范围：后端 xfwl-auth（Sa-Token）+ 网关 xfwl-gateway + 前端 xfwl-admin-ui-vue3。不涉及 TMS 业务域。

---

## 1. 背景与关键事实（调研结论）

1. **前端出身不是若依（RuoYi），是有来 youlaitech/vue3-element-admin v4.9.0**（配套后端 youlai-boot）。
   证据：package.json `name=vue3-element-admin`/author 有来开源组织、README 链 youlai gitee、系统接口统一 `/api/v1/**`、分页壳 `{list,total}`、成功码 `"00000"`、动态路由 `/menus/routes`、按钮指令 `v-hasPerm`、租户/套餐等。
   → 整合应对齐 **youlai-boot 契约**，不是 RuoYi。
2. 当前是"youlai 模板壳 + 心飞物流TMS 业务 + xfwl-auth 适配层"混合态：登录/机构/岗位/区划/参数已接真后端；用户/角色/菜单/部门/字典/通知/日志/租户等系统页仍打 `/api/v1`，后端不存在（已 `meta.hidden` 隐藏）。
3. 新 xfwl-auth 现状：登录认证、机构 sys_org、岗位 sys_station、区划 dict_area、参数 sys_config 完整；**sys_user/sys_role/sys_user_role 仅 Feign 只读，无管理端 CRUD**；sys_menu/sys_role_menu/sys_resource/sys_role_resource 只有 DDL 无代码；无 `StpInterface`、无任何 `@SaCheckPermission`（登录返回 permissionsList 恒空）；无审计自动填充；无 Excel 库。
4. 旧 pd-authority（仓库内旧备份目录 `codeback/` 已由用户于 2026-10-15 删除；仓库外 `D:\MyCode\pinda-authority` 目录仍在磁盘但已废弃、本仓库不再依赖；原始实现可在 git 历史改名前提交 `54261db6` 只读查阅）有全套 91 接口（菜单+资源模型、动态路由、AOP 操作日志、登录日志、ds_type 五档数据权限），但栈为 JDK8/SB2.2/Zuul/jjwt/javax——**只借鉴设计，不搬代码**。

## 2. 用户已拍板的三项架构决策（202-10-13）

| 决策点 | 结论 |
|---|---|
| API 整合方式 | **网关新增 `/api/v1/** → xfwl-auth`，xfwl-auth 实现 youlai 契约**（分页 `{list,total}`、`pageNum/pageSize`、成功码 `"00000"`，前端拦截器已兼容数字 0）。TMS 业务继续走 `/api/manager`、`/api/auth`，两套方言互不污染；前端系统模块基本零改造、保留模板升级路径。 |
| 组织模型 | **不建 sys_dept；youlai"部门"映射到心飞 sys_org 机构树 + sys_station 岗位**。用户页左侧筛选树与"所属部门"改用现有 OrgAPI（`/api/auth/org/tree`），岗位用 StationAPI。 |
| 数据字典 | **运行时字典做，但仅承载"用户可配分类"**（如性别、通知类型）；**业务状态机枚举仍走《07》+ 前端 `src/enums`，不进运行时字典**（防止状态被后台改坏）。 |

## 3. 总体架构

```
浏览器
  └─ 网关 xfwl-gateway(:8760, Sa-Token 登录态校验 + 注入 Same-Token/tenantId/userid 头)
       ├─ /api/auth/**      → xfwl-auth     （心飞契约：登录/机构/岗位/区划 + 新增系统管理 v1）
       ├─ /api/v1/**        → xfwl-auth     【新增】youlai 契约系统管理（用户/角色/菜单/字典/通知/日志/文件/me）
       ├─ /api/manager/**   → xfwl-web-manager（TMS 业务 BFF）
       ├─ /api/web-{...}/** → 移动三端 BFF
       └─ /api/netty-service/** → xfwl-netty
```

- `/api/v1/**` 与 `/api/auth/**` 一样过网关登录态；Same-Token 机制不变，外部不能绕网关直达 xfwl-auth:9000。
- xfwl-auth 内 youlai 契约控制器统一放包 `com.xinfei.auth.controller.v1`，返回 youlai 风格 `Result{code:"00000",data,msg}` 与分页 `PageResult{list,total}`（与现有 `auth.common.R`/心飞 PageResponse **分开**，避免污染 Feign 内部契约）。
- 多租户、逻辑删除、雪花 ID、审计约定沿用 xfwl-auth 现有机制。

## 4. 数据模型（走 create-migration，不直接改库）

### 4.1 复用（不改结构）
- `sys_user / sys_role / sys_user_role / sys_org / sys_station / sys_config / sys_tenant`。
- 用户归属：`org_id`（机构）+ `station_id`（岗位），对应 youlai 的 deptId/postId。

### 4.2 重建：`sys_menu`（旧 DDL 是旧菜单结构，无 type/perms，且无种子无代码）
按 youlai 结构重建（**全局表，不带 tenant_id**，沿用租户插件 IGNORE；角色-菜单关联带 tenant_id）：
```
id BIGINT 雪花 PK
parent_id BIGINT        父菜单，根=0
type VARCHAR             🔴 订正（2026-10-18）：以《58》已实现契约为准＝字符串 **C目录/M菜单/E按钮/B外链**；本行 TINYINT 数字设计已作废（禁与前端 C/M/E/B 混用）
name VARCHAR(50)        名称
icon VARCHAR(100)
path VARCHAR(200)       路由路径（目录/菜单）
component VARCHAR(255)  前端组件路径（菜单）
perm VARCHAR(100)       权限标识，如 sys:user:create（按钮必填，如 sys:user:add）
sort_value INT
visible TINYINT         1显示 0隐藏
status TINYINT          1启用 0禁用
redirect VARCHAR(200)
keep_alive TINYINT
always_show TINYINT
external_url VARCHAR(255)
create_by/create_time/update_by/update_time, deleted TINYINT
```
- **按钮权限即 sys_menu 中 type=3 的行**，`perm` 同时供前端 `v-hasPerm` 与后端 `@SaCheckPermission`。
- **弃用 `sys_resource / sys_role_resource`**（旧"网关 method+url 清单鉴权"模型）：Sa-Token 下网关只校验登录态，细粒度走 `@SaCheckPermission(perm)` 注解，权限码统一来自 sys_menu.type=3，不再维护第二套接口清单。两张旧表 DDL 可保留但不实现，文档标注弃用。
- `sys_role_menu(tenant_id, role_id, menu_id)` 已有 DDL，直接用（角色分配菜单/按钮）。

### 4.3 新增表
- `sys_dict_type`：id, tenant_id, name(字典名称), code(字典编码，租户内唯一), status, 审计, deleted。
- `sys_dict_item`：id, tenant_id, dict_type_id, label, value, sort_value, status, remark, 审计, deleted。
  仅放可配分类；业务状态枚举不入此表。
- `sys_notice`：id, tenant_id, title, content(TEXT, 富文本), type, status(0草稿/1已发布/2撤回), publish_time, create_by/..., deleted。
  `sys_notice_read`：id, tenant_id, notice_id, user_id, read_time（uk_notice_user）。
- `sys_login_log`：id, tenant_id, user_id, account, user_name, request_ip, location, browser, os, description(登录结果描述), status(成功/失败), login_time。
- `sys_oper_log`：id, tenant_id, title(模块), business_type(0其他1新增2修改3删除…), request_uri, http_method, request_param, response_result, user_id, user_name, request_ip, cost_time, ex_desc(异常), oper_time。
- 文件存储：头像/附件走 `xfwl-base` 或 xfwl-auth 本地/对象存储（P1 定，dev 先本地卷），`POST/DELETE /api/v1/files`。

### 4.4 审计自动填充
- xfwl-auth 新增 MyBatis-Plus `MetaObjectHandler`：insert 填 create_by/create_time、update 填 update_by/update_time（当前全仓零自动填充，各 Service 手写，借本次统一；存量手写不强制改）。
- create_by 取 `StpUtil.getLoginIdAsLong()`，内部免登录链路置 null。

## 5. 权限模型（Sa-Token）

- 实现 `StpInterface`：
  - `getPermissionList(loginId, loginType)`：经 sys_user_role → sys_role_menu → sys_menu(type=3, status=1) 聚合 `perm` 去重；角色 ADMIN(code=`ADMIN`) 或含 `*` 直接返回 `*`。
  - `getRoleList`：返回角色 code 集合。
- 登录成功后把角色/权限随 `GET /api/v1/users/me` 下发（前端守卫在 `userInfoEndpointEnabled=true` 时拉取），填充 userInfo.roles/perms，驱动菜单与 v-hasPerm。
- 写接口在 Controller 加 `@SaCheckPermission("sys:user:add")` 等注解；**P0 先对系统管理域加注解**，TMS 业务域维持登录态即可（后续按需增强）。

## 6. 后端接口清单（xfwl-auth 实现，对齐前端现有调用）

> 全部 `/api/v1` 前缀（经网关 StripPrefix 后 xfwl-auth 侧路径）。成功返回 `{code:"00000",msg,data}`；分页 `data:{list,total}`。

- **认证/当前用户**：`GET /users/me`（roles/perms/nickname/avatar/deptId=orgId）、`GET /users/profile`、`PUT /users/profile`、`PUT /users/password`、`POST /users/mobile/code`+`PUT /users/mobile`+`DELETE /users/mobile`、email 同构、`POST/DELETE /files`。
- **用户 SYS-01**：`GET /users`(分页 pageNum/pageSize/keywords/status/orgId/createTime)、`GET /users/{id}/form`(含 roleIds/orgId/stationId)、`POST /users`、`PUT /users/{id}`、`PUT /users/{id}/password/reset`、`DELETE /users/{ids}`、`GET /users/options`、`GET /users/template`、`GET /users/export`、`POST /users/import`。
- **角色 SYS-02**：`GET /roles`、`/options`、`/code-options`、`GET /roles/{id}/form`、`GET /roles/{id}/menu-ids`、`PUT /roles/{id}/menus`(number[])、`POST /roles`、`PUT /roles/{id}`、`DELETE /roles/{ids}`。
  - youlai 还有 `/roles/{id}/dept-ids`（数据权限）——本期 dept→org，**数据权限列 P2**，P0 该端点返回空数组占位，前端"自定义数据权限"先隐藏。
- **菜单 SYS-03（D-42）**：`GET /menus/routes`（当前用户动态路由树，结构对齐前端 RouteItem）、`GET /menus`（管理树）、`/options`、`GET /menus/{id}/form`、`POST /menus`、`PUT /menus/{id}`、`DELETE /menus/{id}`。
- **字典**：dict type `GET/POST/PUT/DELETE /dicts`、`/options`、`/{id}/form`；dict item `GET /dicts/{code}/items`、`/items/options`、`POST/PUT/DELETE .../items`。前端 DictSelect/DictTag/SSE 失效（SSE 可 P1 用 Redis pub/sub 或先轮询/手动刷新）。
- **通知**：`GET /notices`、`/{id}/form`、`/detail`、`POST/PUT/DELETE`、`PUT /notices/{id}/publish|revoke`、`PUT /notices/read-all`、`GET /notices/my`。
- **日志 SYS-06**：`GET /logs`（分页，type=login/oper 或分两类）、`DELETE /logs/{ids}`；操作日志由 AOP 注解自动写，登录日志由登录流程写。

### 部门接口（去 youlai dept，前端改造）
- 前端 `api/system/dept` 改为薄封装到机构：`getOptions/getTree` → 现有 `/api/auth/org/tree`；删除用户页对 `/api/v1/depts` 的依赖。角色页数据权限部门树 P2 再接机构树。

## 7. 前端改造点（xfwl-admin-ui-vue3）

1. 守卫：D-42 落地后把 `appConfig.userInfoEndpointEnabled` 置 `true`；动态路由开关恢复（permission store `generateRoutes` 已完整，取消静态旁路），登录后拉 `/menus/routes` 建路由。
2. 静态业务菜单：动态菜单上线后，心飞物流TMS 业务菜单改为 sys_menu 种子下发（一次性迁移现有 router/index.ts 业务菜单为菜单种子 SQL），静态路由仅保留 login/redirect/分享页等。**这是切换关键，需保证菜单种子与现有页面路径完全一致**。
3. 用户页：dept 树→org 树；"所属部门"字段保持表单字段名 deptId 但绑定机构值（或前端层映射 orgId，二选一在实施时定，倾向直接用 orgId 改表单）；性别字典保留（走新字典 /dicts/gender/items/options，需灌性别种子）。
4. 角色页：菜单权限树接 `/menus`，菜单 ids 接 `/roles/{id}/menu-ids`；隐藏"自定义数据权限"。
5. 取消系统页 `meta.hidden`（随各模块后端就绪分批放开）：P0 放开 user/role/menu；P1 放开 notice/log/profile；dict 在字典后端就绪后放开；tenant/app 维持隐藏（P2/不做）。
6. 登录 mock 降级：D-42 上线后删除 guards.ts 的 FALLBACK_USER_INFO 旁路（真实 me 为主）。

## 8. 分期与 DoD

### P0 — RBAC 地基（菜单驱动 + 用户角色闭环）
1. 网关 `/api/v1/**` 路由（含 Same-Token/登录态）。
2. sys_menu 重建 DDL + 心飞现有业务菜单种子迁移 SQL（与现静态路由一致）。
3. MetaObjectHandler；youlai Result/PageResult 适配基类。
4. StpInterface + /users/me + 角色/权限下发；动态路由开关恢复。
5. 菜单 CRUD、角色 CRUD+分配菜单、用户 CRUD+启停+重置密码+分配角色（org/station）。
6. 前端 user/role/menu 三页去 dept 依赖、接真接口、取消 hidden。
- **DoD**：登录后菜单由后端下发且与现页面一致；新建角色只授部分菜单→对应用户只见这些菜单、按钮受 v-hasPerm/@SaCheckPermission 双控；admin 超管全权；用户可被停用（停用后登录被拒，复用现有登录校验）；本地 mvn 编译 + 前端 type-check/build 全过，经网关实测通过。

### P1 — 管理必备增强
个人中心（资料/改密/头像/手机邮箱，dev 验证码走日志/固定码不接真实通道）、用户 Excel 导入导出（xfwl-auth 引 EasyExcel）、操作日志（AOP 注解+异步落库，参考旧设计，敏感字段脱敏）、登录日志、通知公告（富文本+已读）、字典管理（仅可配分类+性别种子）、文件上传。

### P2 — 按需/预留
数据权限（按 sys_org tree_path，参考旧 ds_type 五档，角色页自定义机构）、租户/套餐/应用管理（SaaS 化）、codegen（仅开发期）、workflow（《60》Flowable 预留）、动态表单（@form-create，已预置）。

## 9. 风险与纪律
- **动态菜单切换是最高风险点**：菜单种子必须 100% 覆盖现有业务页路径，否则页面"消失"。切换时保留静态路由一版作回退，灰度（先 me+权限、再动态路由）。
- 菜单/权限种子属基础数据，SQL 走 `sql/` 幂等迁移，dev 库执行前先评审。
- 不引第二个同类库/中间件；EasyExcel 是 P1 唯一预期新依赖。
- 守《59》：codegen/workflow/dynamic-form 页面与依赖全保留，仅不接后端、不挂菜单。
- 不碰 TMS 业务域接口；系统管理接口在 xfwl-auth 实现。

---

## 10. 后期业务功能对底座的要求（读《55》《20》《21》《32》《34》《49》《51》后补齐）

> 目的：底座一次把"扩展位"留对，后期上财务/运营/IoT 不返工；但 dev 单租户阶段不提前实现 SaaS/工作流等重型能力。

### 10.1 身份双轨：员工账号 ≠ C 端会员（必须显式区分）
- **内部员工**走 xfwl-auth `sys_user`（RBAC + 机构/岗位），驱动管理端、司机 App、快递员端；**C 端客户**走 xfwl-user 的 `member`（会员，数据按 memberId 隔离，USR-01/02）。不合并成一张用户表。
- 员工账号要能**关联业务档案**：司机账号↔base_truck_driver、快递员账号↔base_courier，含所属机构/承运商；这是 DRV-01"禁止越权看他人任务"、DIS-02"扫码越权拒单"、PER-01 绩效、EXC 责任追溯的前提。
- 一套 sys_user 支撑四端登录（D-15/16：同端点、`token` 头、无 refreshToken）；**动态菜单只服务管理端**，移动三端页面固定在各 uni-app（D-62，不做移动端动态菜单）。
- 用户-角色保持**多对多**（一人多岗：调度兼监控、车队长等），岗位与角色双轨。

### 10.2 角色蓝图（种子一次规划全，P0 先建在用的）
超管 ADMIN、调度员、运营/监控、车队长/车队主管、**财务专员、财务主管（D-45：财务必须独立角色，否则自批自核）、审计员（跨财务/运营只读+导出，建议内置）**、仓库/网点、客服、司机、快递员、客户(会员，不在 sys_role)、**承运商 CARRIER（P2 承运商门户用，先占位）**。
- 被引用角色不可删；不给角色默认兜底全权（SYS-02）。

### 10.3 数据权限：本期"归属过滤"满足 D-44，五档引擎留 P2
- **D-44 网点数据隔离是 P0 硬需求**（加盟网点子账号只见本网点、总部见全部、禁止共用账号以便审计到人）。P0 底座做法（不做通用引擎）：
  1. 用户**强制归属 org/station**（用户表单必填机构）；
  2. 提供"当前登录人机构/网点"统一上下文（Sa-Token session 已有 orgId，网关注入）；
  3. 业务列表（取派大厅、看板下钻、绩效、对账）在各域按当前用户机构**点对点过滤**，随业务域迭代落地，不在本期改全部查询。
- **P2 再做旧 pd-authority 的 ds_type 五档**（全部/本机构/本机构含下级/自定义/本人，配合 sys_org tree_path 物化路径）。本期角色页"自定义数据权限"隐藏、`/roles/{id}/dept-ids` 返空数组占位，**不建 role_org 表、不做 InnerInterceptor**。
- 会员维度隔离（memberId）、业务范围（CAP-03 货物类型/作业区域）是另外两个维度，不混入机构数据权限。

### 10.4 后期菜单/权限码占位（只放 perm 占位，不做页面后端）
菜单表设计成可承载未来三大业务域 + P2 储备域，本期用种子放目录/权限点位，不实现功能：
- **财务结算**（P1）：账单/对账/收付款账龄/发票/价格协议/客户信用/司机结算/运费审计/利润驾驶舱（FIN-1~4，状态机 4 套、调账>5% 二级审批、坏账>1万审批）。
- **运营监控**（P0/P1）：通知中心、SOP 异常处置预案、车辆/司机看板、时效、围栏、趟次成本/油耗、绩效（OPS-0~5；work_task_cost/base_truck_fuel 是该域仅有的新建表）。
- **IoT 设备**（P2，按硬件开通）：终端设备(JT/T808 接入 B1)、温控、载重、油卡、视频(GB28181/JT1078 或云 RTC)；设备挂车辆，菜单/设备类型字典/留存策略先占位。
- **P2 六域**：整车 FTL、快递 EMS、智能分拣 ASS、仓储 WMS、冷链 CLD、充运 EV（§6.23~6.27）。
- 增值模块（POD/视频/IoT/控制塔）未来按"菜单+perm 集合"做套餐订阅（《34》），故**所有增值能力在 sys_menu/perm 里留位**，本期不做套餐表/计费。

### 10.5 字典/参数分四层，不要混成一张字典表
1. **代码枚举（不进字典、禁运行时改）**：12 级订单状态机、SignStatus、告警类型、坐标系等——走《07》+ 前端 `src/enums`（D-48 七档映射即按此）。
2. **运营可配字典（本期 sys_dict_type/item）**：性别（本期种子）、证件类型、计费方式、成本类型(FUEL/TOLL/REPAIR/...)、温区、违章/驾驶行为、客户分级、设备类型/协议、通知通道、各状态机名称等《07》§11 规划态编码。删除保护：字典项下有数据禁删（R-01）；**禁止第二套字典表/服务（D-20/BA-18）**。
3. **系统参数/阈值（沿用 sys_config，P1 做在线配置页）**：轮询间隔、ETA 阈值、超速/疲劳/停留阈值、打卡半径(约500m)、心跳超时、证件预警天数(30/15/7, D-37)、审计差额(默认2%)、坏账比例、各类保留期等——散在 yml 的阈值后期都要超管在线可配，免改阈值重发版。
4. **业务规则/模板（独立管理，不进通用字典）**：Drools 运价规则（热加载）、费率卡版本化(POD-06)、通知模板与触发节点、面单打印模板。

### 10.6 消息：三层分开，勿合成一个模块
- **领域事件总线 = RabbitMQ**（唯一 MQ，不引 MQTT）：内部解耦（下单/支付/结算等）。
- **统一告警中心 = 既有 xfwl-netty AlarmRecord/AlarmController**：异常"触发→落库→分派→认领/处理→关闭"五态（P1 扩 0/1 两态）、SOP、超时升级，留 assignee/level/handle_remark 字段位；前端勿写死 10 类/5 态。
- **通知中心 = sys_notice（P1 本期只做管理端站内公告 + 已读）**；业务节点触达（TRK-03 节点通知、延误 Q-E-16、改投 D-53、到期提醒 CMP）后期走"模板+触发节点可配、同单同节点幂等一次、失败重试+投递状态"，渠道预留短信网关/微信订阅消息/App Push 适配位；**dev 验证码/短信不接真实通道（日志或固定码）**。

### 10.7 文件：统一入口，不各域自建
- 各域已统一复用 `attachment/upload`（回单 POD、异常 DIS-03、油费 RPT-01、应急 MON-04）。P1 在底座落地 `/api/v1/files`：对象存储可换（dev 本地卷，后期 OSS/MinIO）、按 **bizType+业务单据 id 多态绑定**、保留期可配、敏感附件访问审计与脱敏（证件/保险号，《12》17条）。
- 水印（时间+经纬度+任务号，≤50m 复核）在前端 canvas 合成，后端不引图像库；离线补传幂等（DRV-04）。
- 关键文件纳入备份（《32》D5）。

### 10.8 审批：本期不上引擎，留轻量扩展位
- 明确需求（POD-08 运费申请：司机→调度→财务；工资单 待审→确认→发放；坏账核销；EXC 理赔三段式）都是**固定 1~2 级、角色明确、按金额阈值升级**，不要求 BPMN（《60》E-02 Flowable 仅徽章预留、零开发）。
- 本期用"**独立财务角色 + perm 权限点 + 业务状态机 + optLog 留痕**"满足；底座预留"审批单/节点/意见"的扩展位，未来报销/坏账/SLA 多类审批多了再抽象，不提前建工作流表。

### 10.9 日志/审计与导出
- 操作日志 AOP 注解 + 异步事件落库（参考旧 pd-authority SysLogAspect 设计重写），敏感字段正则脱敏（password/手机/证件/银行卡）；登录日志记录 UA/IP/结果。
- **差异化留存（D-08/D-30）**：普通日志 ≥6 个月；调度改派/运费调整/拒单理由等关键业务 ≥3 年，理由取枚举非自由文本。预留归档表与归档作业位（日志本身不可物理删除，SYS-06）。
- **统一导出能力（P1）**：财务+合规有 10+ 导出点（账单/账龄/发票/变更/告警/处理记录）。做"异步导出任务 + 字段白名单 + 导出即留痕 + 临时文件即删"的统一能力，而非每页各写；审计员导出专属角色。

### 10.10 多租户：留机制、不做功能（SaaS 在 P2）
- 现状已是"**单库 xinfei_tms + 全表 tenant_id + 租户插件**"的带租户字段单租户系统（SRS §1.4 红线：本期不建多租户 SaaS；《60》P2 才商业化）。
- 本期：新建系统表带 tenant_id+审计+deleted；**sys_menu 全局不带租户、sys_role_menu 带租户**（未来"套餐菜单→租户角色菜单"零改表）；sys_tenant 表与网关 tenantId 透传保留；不写租户注册/切换/套餐/配额代码。
- **种子化交付**：菜单/角色/字典/参数一律用幂等迁移 SQL 交付——未来《34》行业模板快速复制（一个新客户=一份菜单+角色+字典种子包）靠它，本期不做模板管理 UI。

### 10.11 定时任务与系统监控
- **现状纠偏**：xfwl-dispatch 已在 2026-10-10 迁移到 **XXL-Job 2.4.1**（xxl-job-admin 容器在跑，台账 #8ca0fca），与《20/21》"不引 XXL"的旧口径不一致——**以现状为准，不回退**。调度主流程/到期扫描/离线扫描/账龄等走 XXL；系统内"任务 CRUD+执行日志"管理菜单本期不做（XXL 控制台已承担），仅在系统管理预留菜单位（P2 视需要做只读嵌入）。
- 业务监控（车辆/看板/告警）≠ 系统监控（主机/中间件/APM，《32》D4 上线 P0），后者用运维设施，不塞进系统管理菜单。

### 10.12 凭据与外部集成
- 支付/短信/地图/电子签/实名的 appKey/secret **不入业务库**，走 Nacos 配置（D-26）；youlai 的 app/应用管理（OAuth client）随开放平台后置 **P2 不做**（D-25 无开放 API/ISV）。未来若做，appSecret 必须加密存储、查询脱敏、禁止 className 反射加载（《22》R-02/R-03 RCE 教训）。
- Nacos 控制台当前默认无鉴权（《26》§1.1）——属上线安全加固项（D2 等保），不在本特性内，登记跟进。

## 11. 两处张力——已拍板（2026-10-13）
1. **SYS-04 资源注册纪律 → 正式裁替为声明式权限码，且管理端一次性全覆盖。**
   - **决策**：废弃"手工往 pd_auth_resource 注册 method+url 清单、网关白名单拦截"模型；统一为 **sys_menu(type=3 按钮).perm 权限码 + `@SaCheckPermission` + 角色授权 + 前端 `v-hasPerm` 一套**。旧 sys_resource/sys_role_resource 两表 DDL 保留不实现、标注弃用。
   - **覆盖强度（用户拍板：管理端一次性全覆盖）**：xfwl-web-manager 对外的全部管理端接口（约 120 个映射）P0 内全部定义权限码、加注解、建菜单按钮种子并授权；不是"只保高危写操作"的渐进式。内部 Feign（/internal，Same-Token）与移动三端 BFF（粗角色+数据归属过滤）不走按钮权限码。
   - **权限码命名规范（🔴 2026-10-18 订正：统一三段式 `域:实体:动作`，动作词以《58》已落库口径为准）**：如 `order:list / order:create / order:update / order:cancel / order:export`、`truck:create`、`sys:user:reset-password`。动作约定：`list`(分页/查询) `detail` `create` `update` `delete` `export` `import` + 业务动作（`cancel/audit/dispatch/handle` 等）；**两段式（`order:add`）与 add/edit 动作词写法作废**。只读 list/detail 也要有权限码（菜单可见即可查）。
   - **授权现实**：ADMIN(code=ADMIN) 恒为 `*`（全通）；dev 现阶段主要使用 admin，故全覆盖不会锁死使用；其余业务角色（调度/车队/财务/审计…）按角色在 sys_role_menu 种子里授对应码，随业务推进细化。
2. **SYS-05 机构/网点停用归属 → 按业界"软关联 + 占用校验 + 数据快照"四条规则定全局口径（用户拍板）。**
   1. **历史数据不迁移**：订单/运单/任务/账单保存当时机构快照（冗余机构名），停用机构不改任何历史单据的归属；
   2. **停用=只读归档**：停用后不可再被新业务选中（下拉过滤）、不可新增用户、不可派新单；在途任务不中断，由上级机构收尾；
   3. **账号联动**：机构停用其下账号不能登录（登录链路除校验 user.status 外再校验 org.status）；员工调岗改挂新机构后账号恢复正常，账号生命周期独立于机构；
   4. **停用前占用校验**：机构下存在在途任务/未结事项时拒绝停用并提示先改派/调岗（与删机构"有下级拒绝"同范式）。
   - 该语义作为**全局生命周期规则**统一适用于 用户/角色/机构/岗位/字典：停用=禁新业务+禁登录（仅账号），数据保留、不级联物理删除。写入《07》《55》SYS-05。

### 11.1 管理端权限"全覆盖"在本微服务架构下的落地（关键技术方案）
现状：登录态只在网关校验，下游 xfwl-web-manager 仅靠网关注入的 `userid` 头（TokenAuthInterceptor 只验头存在），**没有 Sa-Token 会话，不能直接用 @SaCheckPermission**。要在 BFF 全覆盖，采用 Sa-Token 官方"同 Redis 共享会话"方案：
1. **登录时落权限到 token-session**：xfwl-auth 登录成功后把 `roles`(角色 code) 与 `perms`(权限码集合) 写入 `StpUtil.getTokenSession()`（Redis 已共享）；ADMIN 写 `*`。
2. **xfwl-web-manager 接入 Sa-Token**：引 `sa-token-spring-boot3-starter` + `sa-token-redis-jackson`，token-name=`token`、同一 Redis；网关转发时**保留原始 `token` 请求头**（确认 ForwardSameTokenFilter 不去掉它）。BFF 即可用 `StpUtil` 读到同一登录态。
3. **StpInterface 统一实现放 xfwl-common**（各需鉴权服务共享）：`getPermissionList` 直接从 token-session 读登录时写入的 perms（避免每个服务都反查库/Feign）；权限变更（角色授权改动）后使相关用户 token-session 失效或主动刷新。
4. **BFF 控制器加注解**：管理端写/读方法加 `@SaCheckPermission("...")`；保留外层网关登录态校验为第一道、注解为第二道（细粒度）。内部 Feign `/internal/**` 不加按钮权限（Same-Token 保护）。
5. **新增接口纪律（替换旧 SYS-04）**：新增管理端接口 = 定义权限码 → sys_menu 建按钮点 → 方法加 `@SaCheckPermission` → 给角色授权；前端按钮用同一码 `v-hasPerm`。代码评审核对，杜绝"有接口无权限点"。
> 成本：底座（token-session perms + BFF 接 Sa-Token + 公共 StpInterface + 网关透传确认）约 1.5–2 天；xfwl-web-manager ~120 端点批量定义权限码/注解/菜单按钮种子约 2–3 天（按模块批量、配合 ADMIN 全通不阻塞联调）。合计比"只做系统域"多约 2–3 人日，换取管理端上线即细粒度可控、满足财务隔离(D-45)与等保(D2)。

## 12. 修订后的分期（并入后期扩展要求）
- **P0（RBAC 地基 + D-42 动态菜单 + D-44 归属 + D-45 财务角色 + 管理端权限全覆盖 + SYS-05）**：网关 `/api/v1`；youlai 壳；MetaObjectHandler；sys_menu 重建+业务菜单种子迁移；StpInterface+/users/me+/menus/routes+动态路由切换（静态版留回退）；菜单 CRUD；角色 CRUD+菜单/按钮授权（角色种子含财务/审计/调度/车队等）；用户 CRUD+启停+重置密码+多角色+**必挂机构/岗位**；dept→org；前端 user/role/menu 接真、去 hidden。**另含（§11 拍板）**：①登录写 token-session(roles/perms)、xfwl-common 公共 StpInterface、**xfwl-web-manager 接 Sa-Token 并对约 120 个管理端接口全量定义权限码+@SaCheckPermission+菜单按钮种子**（ADMIN `*` 全通不阻塞）、确认网关透传 token 头；②SYS-05 机构停用四规则（占用校验、账号联动登录拦截、历史快照不迁移、下拉过滤）。
- **P1（管理必备 + 合规导出）**：个人中心、用户 Excel（EasyExcel）、**统一导出能力**、操作日志(AOP/脱敏/6月)+登录日志、**关键操作 3 年留存与归档位**、通知公告+已读、字典(可配分类+性别种子)、文件上传；**业务列表按登录人机构过滤随域落地(D-44，移动/业务查询点对点)**；sys_config 在线配置页。
- **P2（预留不开发）**：五档数据权限、租户/套餐/应用、Flowable、动态表单、codegen 接后端、承运商门户、IoT/财务/运营三大业务域（底座只放菜单/字典/权限点位）。

---

## 13. 管理端权限码清单（#10 真源，2026-10-13 初版）

> 命名：`<资源>:<动作>`。动作约定 `list/detail/add/edit/delete/export/import` + 业务动作(cancel/audit/handle/enable/disable/save...)。
> 三方对齐：后端 @SaCheckPermission = sys_menu(type=B).perm = 前端 v-hasPerm。ADMIN 恒 `*`。
> 查询分页端点多为 **POST /page（动作是 list，不是 add）**，批量落注解时按方法语义而非 HTTP 动词推断。

### 13.1 已落地样板
- order（OrderController，BFF /order-manager/order）：`order:list`(POST /page)、`order:detail`(GET /{id})、`order:edit`(POST /{id})、`order:cancel`(PUT /{id}/cancel)。

### 13.2 web-manager 其余控制器 → 资源前缀（#10 按域批量补）
| 控制器 | 基路径 | 资源/权限码 |
|---|---|---|
| TransforCenterBusinessController | /transfor-center/bussiness | 按业务资源细分：`truck:*`、`truckType:*`、`fleet:*`、`driver:*`、`driverLicense:*`、`goodsType:*`、`transportLine:*`、`transportLineType:*`、`transportTrips:*`（每个含 list/detail/add/edit/delete/enable，license/trips 为子资源 detail/edit） |
| BusinessHallController | /business-hall | `goodsType:*`（营业大厅货物类型，注意与基础档案 goodsType 区分，按实际归属定）、`courier:*`、courierScope |
| MemberController | /user-center/member | `member:list/detail/add/edit/delete` + enable/disable 归 edit |
| CargoController | /order-manager/cargo | `cargo:list/add/edit/delete` |
| AgencyController | /agency | `agency:tree/list/detail`、`agencyScope:edit` |
| TransportOrderController | /transport-order-manager | `transport:list/detail`（POST /page=list） |
| TransportTaskController | /transport-task-manager | `task:list/detail/edit`（point/{id}=detail） |
| PickupDispatchTaskController | /pickup-dispatch-task-manager | `pickup:list/edit` |
| DriverJobController | /driver-job-manager | `driverJob:list` |
| SysConfigController | /sys-config | `config:list/detail/add/edit/delete` |
| DashboardController | /dashboard | `dashboard:view`（看板只读，登录可访问；可不强校验） |
| UserCenterController | /userCenter | info/message：`profile:view`、`notice:read` |
| CommonController | /common | 下拉简单数据：登录即可读，**不设权限码**（form 依赖） |
| TruckLocationController | /transfor-center | truck-place-info：`truck:detail` |
| OrderLocusController | /orderLocus | 轨迹：`order:detail` 或 `track:view` |

### 13.3 实施注意
- **公共下拉/表单依赖（CommonController.*Simple、org tree、station）只要求登录，不加 @SaCheckPermission**，否则新建表单拉不到下拉。
- BFF 的 Feign 出站调 /internal 走 Same-Token，不挂按钮权限。
- 每加一批权限码，同步在 sys_menu 建对应 type=B 按钮种子（挂业务菜单下），并给角色授权；否则非 ADMIN 角色 403。
- 按钮种子 SQL 放 `/sql/auth/`（不进 git，交用户执行）。
