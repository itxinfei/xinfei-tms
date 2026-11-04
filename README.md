<div align="center">

<p align="center"><img src="docs/brand/logo-full-login.png" alt="心飞物流 TMS" width="240"/></p>

<p>
  <b>面向运输企业与承运商的开源运输管理系统（TMS）</b><br/>
  管理端加三个移动端，业务从寄件一路走到结算
</p>

<p>
  <img src="docs/badges/license.svg" alt="License"/>
  <img src="docs/badges/spring-cloud.svg" alt="Spring Cloud"/>
  <img src="docs/badges/jdk.svg" alt="JDK 25"/>
  <img src="docs/badges/gateway.svg" alt="Spring Cloud Gateway"/>
  <img src="docs/badges/sa-token.svg" alt="Sa-Token"/>
  <img src="docs/badges/nacos.svg" alt="Nacos"/>
  <img src="docs/badges/mysql.svg" alt="MySQL"/>
  <img src="docs/badges/redis.svg" alt="Redis"/>
  <img src="docs/badges/minio.svg" alt="MinIO"/>
  <img src="docs/badges/elasticsearch.svg" alt="Elasticsearch"/>
  <img src="docs/badges/xxl-job.svg" alt="XXL-Job"/>
  <img src="docs/badges/kafka.svg" alt="Kafka"/>
  <img src="docs/badges/drools.svg" alt="Drools"/>
  <img src="docs/badges/or-tools.svg" alt="OR-Tools"/>
  <img src="docs/badges/spring-ai.svg" alt="Spring AI"/>
  <img src="docs/badges/kuaidi100.svg" alt="快递100"/>
  <img src="docs/badges/doris.svg" alt="Apache Doris"/>
  <img src="docs/badges/flowable.svg" alt="Flowable"/>
  <img src="docs/badges/jimu-report.svg" alt="积木报表"/>
  <img src="docs/badges/debezium.svg" alt="Debezium"/>
  <img src="docs/badges/resilience4j.svg" alt="Resilience4j"/>
  <img src="docs/badges/mapstruct.svg" alt="MapStruct"/>
  <img src="docs/badges/zipkin.svg" alt="Zipkin"/>
  <img src="docs/badges/druid.svg" alt="Druid"/>
  <img src="docs/badges/baidu-map.svg" alt="百度地图"/>
  <img src="docs/badges/netty.svg" alt="Netty"/>
  <img src="docs/badges/vue.svg" alt="Vue"/>
  <img src="docs/badges/uniapp.svg" alt="uni-app"/>
</p>
</div>

<p align="center"><b>简体中文</b> · <a href="README.en.md">English</a></p>

> 心飞物流TMS 是一套开源的运输管理系统：管理端、客户端、快递员端、司机端四个端共用同一套后端，技术栈是 JDK 25 + Spring Boot 4.1.1 + Vue 3.5。业务从寄件、订单、智能调度、作业、轨迹到回单；调度规则写在 Drools 里，轨迹接入是自己搭的 Netty 服务，认证用 Sa-Token，支持多租户。

<p align="center">
<b><a href="#sec-tech"><code>15</code></a></b> 个微服务 · <b><code>4</code></b> 个端 · <b><code>106</code></b> 条功能需求 · <b><code>71</code></b> 张数据表 · <b><code>27</code></b> 项技术栈
</p>
<p align="center">
项目主页：<a href="https://itxinfei.github.io/xinfei-tms/" target="_blank">https://itxinfei.github.io/xinfei-tms/</a>
</p>


---

## 目录

- [一、项目是什么](#sec-intro)
- [二、适合谁用，和同类方案的区别](#sec-advantages)
- [三、解决的痛点](#sec-pain)
- [四、四端构成](#sec-ends)
- [五、功能范围](#sec-features)
- [六、技术方案](#sec-tech)
- [七、快速开始](#sec-quickstart)
- [八、系统截图](#sec-screens)
- [九、相关资料](#sec-docs)
- [十、配套虚拟机（环境镜像）](#sec-vm)
- [十一、授权、服务与联系](#sec-contact)
- [十二、里程碑](#sec-milestones)
- [十三、常见问题（FAQ）](#sec-faq)
- [十四、参与贡献](#sec-contrib)

---

<a id="sec-intro"></a>

## 一、项目是什么

运输行业常见的四种业态是快递、快运、专线、三方。形态不一样，麻烦差不多：车、司机、线路散在各处，不好统一管；运输过程看不见；调度和计费靠人；成本与时效也没法考核。

这套系统把运力准备、下单、调度、运输、派送、签收、结算放进一条流程里，车辆、司机、线路的数据集中在一处，能出统计和考核报表。

> 市面上的 TMS 大致两类：商业版年费 3-30 万，实施周期半年起步；开源的多半还停在 JDK 8 + Vue 2。这个项目的源码公开，个人学习研究免费；下单到结算的业务都自己实现，用的也是当前版本的技术栈；四个端都在仓库里，拉下来能跑，改起来也够用。

---

<a id="sec-advantages"></a>

## 二、适合谁用，和同类方案的区别

### 2.1 谁适合用心飞

- 中小车队（10-50 辆）：想做数字化，但年费 3-30 万的商业 TMS 负担不起
- 物流初创团队：需要一个功能全、技术栈新、能跑起来的四端 TMS 当 MVP
- Java 学习者：一套代码里能看到微服务、规则引擎、Netty、多租户的实战写法
- 二次开发商：给客户做定制交付时，可以省下三个月搭底子的时间

### 2.2 与同类方案对比

| 能力                       | 心飞物流TMS            | 同类开源 TMS      | 商业 TMS     |
| ------------------------ | ----------------- | ------------- | ---------- |
| 四端协同（管理+客户+快递员+司机）       | ✅                 | ❌ 仅管理端        | ✅          |
| JDK 25 + Spring Boot 4.1 | ✅                 | ❌ 多为 JDK 8    | 部分已升级      |
| 多租户 SaaS                 | ✅                 | ❌             | ✅          |
| 智能调度（Drools 规则引擎）        | ✅                 | ❌ if-else 硬编码 | ✅          |
| Netty 自建轨迹接入             | ✅                 | ❌             | ✅          |
| 年费                       | **0（BUSL-1.1，学习/研究免费）** | 0             | **3-30 万** |
| 二开自由                     | ✅ 代码全开源           | ✅             | ❌ 黑盒交付     |

> 「同类开源」泛指 Gitee/GitHub 上主流开源 TMS 项目，不针对特定项目；如有不准确欢迎 Issue 纠正。

### 2.3 界面

<table>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-login.png" alt="管理端登录"/>
     <sub><b>管理端登录</b> · 统一账号 + 验证码校验</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-dashboard.png" alt="管理端仪表盘"/>
     <sub><b>工作台仪表盘</b> · 订单 / 告警 / 待办与图表统计</sub>
</td>
</tr>
</table>

> 运营监控中心大屏（真实运行环境录制）：

![大屏演示动画](docs/screenshots/demo-bigscreen.gif)

### 2.4 功能进度

|                     |                   |                |                |
| ------------------- | ----------------- | -------------- | -------------- |
| ✅ 订单管理              | ✅ 智能调度            | ✅ 作业管理         | ✅ 轨迹追踪         |
| ✅ 多租户认证             | ✅ 基础数据            | ✅ 运单管理         | ✅ 数据报表         |
| ✅ 四端 App            | ✅ 权限 / 菜单         | ✅ 车辆 / 线路      | ✅ 人员管理         |
| 🔲 财务结算             | 🔲 智能排线（OR-Tools） | 🔲 回单 OCR      | 🔲 动态 ETA      |
| 🔲 AI 客服（Spring AI） | 🔲 ES 检索          | 🔲 对象存储（MinIO） | 🔲 消息管线（Kafka） |

---

<a id="sec-pain"></a>

## 三、解决的痛点

| 行业痛点                   | 心飞物流TMS 的解法                |
| ---------------------- | ------------------------- |
| 车辆 / 驾驶员 / 线路分散，难以统一考核 | 车辆、司机、线路进统一基础数据，考核按统计报表出 |
| 调度靠人工、配载凭经验            | 定时调度和路径规划生成运输任务，再分车次、车辆和司机  |
| 运输过程黑盒，在途不可见           | 车装 GPS，位置实时回传，轨迹能回放，偏航、超速、离线出告警  |
| 运费计算不透明、易出错            | 计费规则放在 Drools 里，改规则不用改代码，算过的费用可查 |
| 客户查件无渠道、签收无留证          | 客户端 App 能查单，签收留电子回单和 POD 照片 |
| 数据孤岛、统计靠手工             | 数据聚到一处，运输量、时效、成本的报表自动出      |

---

<a id="sec-ends"></a>

## 四、四端构成

| 端        | 名称         | 使用对象    | 核心能力             |
| -------- | ---------- | ------- | ---------------- |
| 后台系统管理端  | 心飞物流TMS 管理端 | 公司内部管理员 | 基础数据维护、订单管理、运单管理 |
| 客户端 App  | **心飞速运**   | 外部客户    | 寄件、查询物流信息        |
| 快递员端 App | **心飞快递员**  | 公司内部快递员 | 接收取派件任务          |
| 司机端 App  | **心飞司机宝**  | 公司内部司机  | 接收运输任务、上报位置信息    |

---

<a id="sec-features"></a>

## 五、功能范围

### 5.1 整体业务流程

![整体业务流程](docs/business-flow.png)

```
寄件下单 → 订单创建 → 智能调度（Drools）→ 司机接单
→ 作业执行 → 轨迹追踪（Netty）→ 回单签收 → 对账结算
```

### 5.2 核心业务模块

| 模块                  | 功能描述                             |
| ------------------- | -------------------------------- |
| **订单管理**            | 寄件下单、货物管理、订单全生命周期状态管理、运费试算       |
| **智能调度**            | 订单分类、路径规划、运输任务创建、车次 / 车辆 / 司机分配  |
| **运输管理（行车管理）**      | 运输任务执行、司机作业单、在途状态跟踪              |
| **GPS 车辆定位 / 在途监控** | 车辆实时定位、轨迹回放、偏航与异常监控              |
| **配送执行**            | 取派件任务、运单管理、电子签收（POD）             |
| **车辆 / 线路 / 车次管理**  | 车队、车辆、车型、线路、车次等运输资源维护            |
| **人员管理**            | 司机、快递员、岗位与组织管理                   |
| **客户门户**            | 客户端 App 寄件、查单、物流跟踪               |
| **回单与结算**           | 回单签收留证、费用结算、对账                   |
| **数据报表**            | 运输量、时效、成本、绩效等多维统计                |
| **基础信息维护**          | 组织机构、菜单、权限、字典等支撑数据               |
| **统一认证与权限**         | 基于开源 Sa-Token 的用户、角色、菜单、多租户认证与鉴权 |

### 5.3 订单状态机（示例）

订单从待取件走到已签收，中途可能转成拒收或取消：

```
待取件 → 已取件 → 待装车 → 运输中 → 网点出库 → 待派送 → 派送中 → 已签收
                                                  └→ 拒收 / 已取消
```

---

<a id="sec-tech"></a>

## 六、技术方案

### 6.0 项目结构

```
xinfei-tms/
├── 微服务（15 个）
│   ├── xfwl-gateway/           # 统一网关（Spring Cloud Gateway）
│   ├── xfwl-auth/              # 认证中心（Sa-Token + 多租户）
│   ├── xfwl-base/              # 基础数据（机构 / 车辆 / 线路 / 货物）
│   ├── xfwl-oms/               # 订单管理（OMS）
│   ├── xfwl-dispatch/          # 智能调度（Drools + XXL-Job）
│   ├── xfwl-work/              # 作业管理（取派件 / 运输任务）
│   ├── xfwl-user/              # 用户与会员
│   ├── xfwl-file-server/       # 附件服务（POD 照片上传/查看，无数据库）
│   ├── xfwl-netty/             # 轨迹接入（Netty TCP）
│   ├── xfwl-aggregation/       # 数据聚合
│   ├── xfwl-optimizer/         # 智能配载（OR-Tools + 规则引擎）
│   └── xfwl-web/               # BFF 聚合层（四端接口）
│       ├── xfwl-web-manager/   #   管理端 BFF
│       ├── xfwl-web-courier/   #   快递员端 BFF
│       ├── xfwl-web-customer/  #   客户端 BFF
│       └── xfwl-web-driver/    #   司机端 BFF
├── 支撑库（2 个 jar）
│   ├── xfwl-common/            # 公共组件（工具 / 上下文 / 拦截器）
│   └── xfwl-service-api/       # 服务间 API 契约（Feign 接口）
├── 前端（4 端）
│   ├── xfwl-admin-ui-vue3/     # 管理端（Vue3 + Element Plus）
│   └── xfwl-mobile/            # 移动三端（uni-app Vue3）
│       ├── customer/         #   客户端 · 心飞速运
│       ├── courier/          #   快递员端 · 心飞快递员
│       └── driver/           #   司机端 · 心飞司机宝
├── deploy/                   # Docker Compose 编排与部署脚本
└── docs/                     # 文档 + 截图 + 徽章
```

### 6.1 系统架构

![系统架构](docs/system-arch.png)

> 系统分四层。展示层是管理端 Vue3 和移动三端 uni-app；网关层是 Spring Cloud Gateway，做 Sa-Token 鉴权和 Same-Token 防直连；服务层是 15 个业务微服务，注册和配置都走 Nacos；数据层是 MySQL 单库分域，加 Redis 和 RabbitMQ。

### 6.2 微服务架构

![微服务架构](docs/microservice-arch.png)

> 微服务按业务域拆分：`xfwl-auth`（认证）、`xfwl-gateway`（网关）、`xfwl-base`（基础数据）、`xfwl-oms`（订单）、`xfwl-dispatch`（调度）、`xfwl-work`（作业）、`xfwl-netty`（轨迹接入）、`xfwl-aggregation`（聚合）、`xfwl-optimizer`（配载求解）等，通过 OpenFeign 互调，Nacos 统一注册发现。

### 6.3 技术架构

![技术架构](docs/tech-arch.png)

#### 后端技术栈

| 分类 | 技术 | 版本 | 说明 |
|---|---|---|---|
| **基础** | JDK | 25 (LTS) | 虚拟线程支持 |
| | Spring Boot | 4.1.1 | Boot 4 大版本，Jakarta EE 10 |
| | Spring Cloud | 2025.1.2 | 配套 Boot 4.1 |
| | Spring Cloud Alibaba | 2025.1.0.0 | Nacos 注册/配置 |
| | Spring Cloud Gateway | 5.0.2 | 响应式网关（WebFlux） |
| **持久层** | MyBatis-Plus | 3.5.17 | boot4 starter，MyBatis-Spring 4.0.0 |
| | Druid | 1.2.28 | boot4 starter 连接池 |
| | MySQL | 8.0.x | utf8mb4 单库分域 |
| **认证** | Sa-Token | 1.46.0 | boot4 starter，多租户 |
| **规则引擎** | Drools | 8.38.0.Final | 运费计算与调度规则 |
| **调度** | XXL-Job | 2.4.1 | 分布式任务调度 |
| **其他** | FastJSON2 | 2.0.53 | JSON 序列化 |
| | springdoc-openapi | 3.1.1 | API 文档（Boot4 兼容） |
| | MapStruct | 1.6.3 | DTO/Entity 映射 |
| | Lombok | 1.18.42 | 注解处理器 |
| | EasyExcel | 3.3.4 | Excel 导入导出 |
| | EasyCaptcha | 1.6.2 | 登录验证码 |

#### 中间件

| 中间件 | 版本 | 端口 | 用途 |
|---|---|---|---|
| MySQL | 8.0.46 | 3306 | 业务数据库 |
| Redis | 7.x | 6379 | 缓存 + 会话 |
| Nacos | 2.3.2 | 8848 / 9848 | 注册中心 + 配置中心 |
| RabbitMQ | 3.12 | 5672 / 15672 | 消息总线 |
| XXL-Job | 2.4.1 | 8090 | 分布式任务调度 |

#### 前端技术栈

| 端 | 技术 | 说明 |
|---|---|---|
| 管理端 | Vue 3.5 + Vite + Element Plus + Pinia + ECharts | 后台管理系统 |
| 司机端 | uni-app Vue3 + uview-plus | H5 移动应用 |
| 快递员端 | uni-app Vue3 + uview-plus | H5 移动应用 |
| 客户端 | uni-app Vue3 + uview-plus | H5 移动应用 |

### 6.4 技术选型

#### 6.4.1 已经落地的部分

| 技术                            | 在系统中的作用                  |
| ----------------------------- | ------------------------ |
| **JDK 25 虚拟线程**               | 高并发 IO 场景吞吐提升，降低线程开销     |
| **Drools 8.38 动态规则引擎**        | 运费计算与业务规则配置，规则可热更新       |
| **XXL-Job 2.4.1 分布式任务调度**     | 统一编排智能调度等周期任务（替代 Quartz） |
| **Sa-Token + JWT 多租户认证中心**    | 统一登录认证与无状态鉴权，支持多租户与验证码   |
| **Spring Cloud Gateway 统一网关** | 路由转发、登录态校验与防直连           |
| **Netty**                     | 高并发 TCP 通道，支撑轨迹上报与设备接入   |
| **OR-Tools 9.12（xfwl-optimizer）** | 装载方案求解；OR-Tools 不可用时降级到规则引擎 |
| **百度地图**                      | 地址解析、距离计算、轨迹展示           |
| **Druid 连接池**                 | 连接管理与 SQL 监控             |

#### 6.4.2 为什么不选另一个

| 选型                          | 原因                                      |
| --------------------------- | ---------------------------------------- |
| **Sa-Token** 而非 Shiro       | 更轻量、多租户原生、中文文档全、Boot4 生态没有兼容包袱           |
| **XXL-Job** 而非 Quartz       | 分布式调度自带管理台，Quartz 只能自研调度面                |
| **Drools** 而非 if-else       | 调度规则可视化、可热更新、不用改代码发版                     |
| **单库分域** 而非 8 分库            | dev / 中小规模运维成本低，规模化再演进时序库                |
| **Resilience4j** 而非 Hystrix | Hystrix 停更，R4 是 Spring Cloud 官方容错方向（规划中） |

#### 6.4.3 后续规划

- **P1 近期**：MinIO 对象存储（回单/签收文件）· Zipkin 链路追踪 · Resilience4j 容错 · MapStruct 对象映射
- **P2 中期**：Kafka 消息流 · Debezium CDC · Elasticsearch 检索 · OR-Tools 智能排线 · Doris OLAP · 百度鹰眼
- **P3 远期**：Spring AI 智能客服 · 快递100 开放平台 · Flowable 工作流 · 积木报表

### 6.5 数据库设计

业务表都在**同一个 MySQL 库**（`xinfei_tms`）里，按业务域分表前缀，量大了可以按域拆开：

| 业务域     | 主要表内容                            |
| ------- | -------------------------------- |
| 基础数据    | 货物类型、车辆、车队、线路、组织、站点等             |
| 订单（OMS） | 订单、货物信息、订单位置等                    |
| 物流作业    | 取派件任务、运输任务、司机作业单、运单等             |
| 智能调度    | 订单分类、路线缓存等调度数据                   |
| 认证与权限   | 租户、用户、角色、菜单、资源等                  |
| 轨迹与位置   | 车辆轨迹与位置信息（规模化后将引入时序库，见 §6.4 规划项） |

---

<a id="sec-quickstart"></a>

## 七、快速开始

### 7.1 五分钟跑起来

```bash
git clone https://gitee.com/itxinfei/xinfei-tms.git
cd xinfei-tms
docker-compose up -d        # 起中间件：MySQL / Redis / RabbitMQ / Nacos
# ① 初始化数据库：建库与种子脚本随配套虚拟机镜像内置（见「10. 配套虚拟机」）；
#    sql/ 脚本目录不随本仓库分发，需要可联系作者索取
# ② 启动 xfwl-auth → xfwl-gateway → 业务微服务（mvn spring-boot:run 或部署脚本）
# ③ 启动管理端（pnpm i && pnpm dev）与移动端（HBuilderX 运行 uni-app）
```

> 预计 5-10 分钟。部署、配置与二次开发说明见「9. 相关资料」；配套虚拟机镜像（中间件全预置）见「10. 配套虚拟机」，可向作者索取。

---

<a id="sec-screens"></a>

## 八、系统截图

> 截图取自真实运行环境，2026-10-15 更新。

### 8.1 后台管理端

<table>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-login.png" alt="管理端登录"/>
     <sub><b>管理端登录</b> · 统一账号 + 多租户 + 验证码校验</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-dashboard.png" alt="管理端仪表盘"/>
     <sub><b>工作台仪表盘</b> · 订单 / 告警 / 待办与图表统计</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-order.png" alt="订单管理"/>
     <sub><b>订单管理</b> · 状态筛选 / 分页列表（60 条真实数据）</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-waybill.png" alt="运单管理"/>
     <sub><b>运单管理</b> · 运单状态 / 干线运输（45 条）</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-dispatch.png" alt="运输任务"/>
     <sub><b>运输任务</b> · 智能调度生成的运输任务（33 条）</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-pickup.png" alt="取派件任务"/>
     <sub><b>取派件任务</b> · 取件 / 派件任务（44 条）</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-driver-job.png" alt="司机作业单"/>
     <sub><b>司机作业单</b> · 司机提货 / 卸货作业（13 条）</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-alarm.png" alt="告警中心"/>
     <sub><b>告警中心</b> · 车辆偏离 / 超速 / 离线告警（22 条，未处理 14）</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-truck.png" alt="车辆管理"/>
     <sub><b>车辆管理</b> · 车辆档案与状态（22 条）</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-driver.png" alt="司机管理"/>
     <sub><b>司机管理</b> · 司机档案 / 心飞自营车队（10 条）</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-line.png" alt="线路管理"/>
     <sub><b>线路管理</b> · 干线 / 接驳线路（22 条）</sub>
</td>
<td align="center" width="50%"></td>
</tr>
</table>

### 8.2 移动端 App

<table>
<tr>
<td align="center" width="100%">
<b>客户端 · 心飞速运</b>（H5，:8084）<br/><br/>
<img src="docs/screenshots/mobile-customer-home.png" alt="客户端首页" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-customer-order.png" alt="我要寄件" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-customer-list.png" alt="我的运单" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-customer-track.png" alt="运单详情" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<br/><sub>首页（进行中订单）· 我要寄件下单 · 我的运单 · 运单详情 / 物流动态</sub>
</td>
</tr>
<tr>
<td align="center" width="100%">
<b>快递员端 · 心飞快递员</b>（H5，:8083）<br/><br/>
<img src="docs/screenshots/mobile-courier-list.png" alt="取派任务列表" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-courier-detail.png" alt="任务详情" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<br/><sub>取派任务列表 · 任务详情（去签收 / 入库 / 交接 / 核验）</sub>
</td>
</tr>
<tr>
<td align="center" width="100%">
<b>司机端 · 心飞司机宝</b>（H5，:8082）<br/><br/>
<img src="docs/screenshots/mobile-driver-home.png" alt="司机宝首页" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-driver-location.png" alt="位置上报" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<br/><sub>首页 / 运输任务 · 位置上报开关（已开启）</sub>
</td>
</tr>
</table>

### 8.3 运维与中间件

<table>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-deploy-status.png" alt="部署状态总览"/>
     <sub><b>部署状态总览</b> · 全部微服务健康监测</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-nacos.png" alt="Nacos 控制台"/>
     <sub><b>Nacos 注册 / 配置中心</b> · 服务与配置统一管理</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-rabbitmq.png" alt="RabbitMQ 管理台"/>
     <sub><b>RabbitMQ 管理台</b> · 消息队列运行监控</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-xxljob.png" alt="XXL-Job 调度中心"/>
     <sub><b>XXL-Job 任务调度中心</b> · 定时任务与调度报表</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-docker.png" alt="Docker 容器总览"/>
     <sub><b>Docker 容器总览</b> · 微服务与中间件容器健康状态</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-gitea-workflow.png" alt="Gitea CI/CD 工作流"/>
     <sub><b>Gitea CI/CD 工作流</b> · deploy-backend 按序部署 + 逐服务健康检查（网关最后切）</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-mysql.png" alt="MySQL 数据库"/>
     <sub><b>MySQL 8.0 数据库</b> · 终端直连查询 · xinfei_tms 全部 71 张表（InnoDB）</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-redis.png" alt="Redis 键管理"/>
     <sub><b>Redis 7 键管理</b> · Sa-Token 会话缓存与 TTL</sub>
</td>
</tr>
</table>

---

<a id="sec-docs"></a>

## 九、相关资料

仓库内可直接查阅的资料如下；需求、接口与数据字典等内部设计文档不随源码分发，需要可联系作者：

| 资料                                                         | 说明                                                     |
| ------------------------------------------------------------ | -------------------------------------------------------- |
| [docs/architecture/](docs/architecture/)                     | 架构视图源文件（业务流程 / 运行时拓扑 / 数据归属 / 订单生命周期等 .dot 图与配套脚本） |
| [deploy/](deploy/)                                           | 一键部署资产：中间件与应用 Docker Compose、CI/CD workflow、前后端部署脚本、.env 脱敏示例 |
| [docs/sql/区划数据/](docs/sql/区划数据/)                     | 行政区划四级数据（CSV）与坐标 / 拼音生成工具链，初始化区划种子参考 |
| [docs/brand/](docs/brand/)                                   | 品牌资产库（Logo 全尺寸 / favicon）                       |
| [SECURITY.md](SECURITY.md) · [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) · [CONTRIBUTING.md](CONTRIBUTING.md) | 开源社区文件：安全漏洞披露渠道 / 行为准则 / 贡献指南 |
| [LICENSE](LICENSE)                                           | BUSL-1.1 协议全文（个人学习研究免费，企业商用需授权）     |

**设计图**（位于 `docs/`）：`system-arch.png`（系统架构）、`microservice-arch.png`（微服务架构）、`tech-arch.png`（技术架构）、`business-flow.png`（整体业务流程）。

---

<a id="sec-vm"></a>

## 十、配套虚拟机（环境镜像）

> 学习和部署都依赖配套的虚拟机镜像。中间件、数据库数据和各服务运行需要的配置都在镜像里，导入后不用另外搭环境，也不用迁移数据。

### 10.1 镜像里有什么

| 类别 | 组件 | 镜像内状态 |
| --- | --- | --- |
| 数据库 | MySQL 8.0 | 业务库 `xinfei_tms`（全部表与演示数据）、调度库 `xxl_job`，数据已落盘持久化 |
| 缓存 | Redis 7 | Sa-Token 会话 / J2Cache 二级缓存，`dump.rdb` 已持久化 |
| 消息队列 | RabbitMQ | 业务交换机 / 队列 / 账号定义已持久化 |
| 注册 / 配置中心 | Nacos | 命名空间 `pinda-tms`，所有微服务的配置（含各服务 dataId）都已发布在镜像内 |
| 任务调度 | XXL-Job | 调度中心与执行器配置已就绪 |
| 反向代理 | Nginx | 前端静态资源 + 后端网关反代，已在镜像内配置好 |
| 源码 / CI | Gitea + Runner | 项目源码仓库与 CI/CD 流水线已内置 |

### 10.2 几点说明

- 数据都在镜像里：MySQL / Redis / RabbitMQ 的业务数据、Nacos 的配置、各微服务运行要用的 `.env` 环境变量都包含在内，导入后不用另外灌数据，也不连外部服务。
- 虚拟机内网 IP 为 `192.168.20.130`（VMware NAT 网段），导入后直接访问；如需改 IP 或网段，参见 `docs/05-部署文档.md`。
- 默认账号：`pdwl / 123456`（SSH）、`MySQL root / 123456`；应用管理端默认登录方式见 `docs/` 登录说明或联系作者。

### 10.3 如何获取

镜像通过百度网盘分发，链接不公开，需要请联系作者（见第十一章）。

---

<a id="sec-contact"></a>

## 十一、授权、服务与联系

<div align="center">

<table>
<tr>
<td align="center" width="50%">

### 11.1 付费技术服务

本项目采用 BUSL-1.1 协议：个人学习、研究、教学、毕设免费；企业商用（生产环境、私有化部署、对外提供服务等）需联系作者获取商业授权。此外也接商业支持：

- 二次开发 / 功能定制（对接平台、新增业务、UI 调整）
- 私有化部署交付（服务器、域名、环境一条龙，含配套虚拟机镜像）
- 项目讲解 / 毕设答疑（架构、源码、面试考点）
- 全量建库脚本 + 演示数据
- 日常技术答疑与长期维护支持

<b>商务联系 QQ：<code>747011882</code></b>

</td>
<td align="center" width="50%">

### 11.2 加入社区

![微信公众号二维码](docs/心飞为你飞.jpg)

<sub>扫码关注微信公众号「心飞为你飞」</sub>


仓库：[gitee.com/itxinfei/xinfei-tms](https://gitee.com/itxinfei/xinfei-tms)<br/>
作者主页：[gitee.com/itxinfei](https://gitee.com/itxinfei)

</td>
</tr>
</table>


### 11.3 觉得有用的话

如果这个项目帮到你，点个 Star 就行。

</div>

### 11.4 赞助支持

> 本项目在 AI 辅助下开发，后续迭代要持续投入时间和 AI 工具成本。如果心飞物流TMS 对你有帮助，欢迎赞助作者继续更新。

> 赞助联系：加 QQ `747011882` 洽谈，赞助者名单会更新到 README；微信 / 支付宝扫码入口还在准备。

### 11.5 商业授权与商标

- **协议性质**：本项目以 BUSL-1.1 授权（见 [LICENSE](LICENSE)），不是 OSI 开源协议——个人学习 / 研究 / 教学 / 毕设免费，但任何营利性、生产环境或商用使用均须事先取得书面商业授权。
- **商业授权条款与报价**：企业 / 组织商用须购买商业许可，授权范围、计费方式与报价单见 [COMMERCIAL-AGREEMENT.md](COMMERCIAL-AGREEMENT.md)。
- **自用还是商用，怎么算**：见 [COMMERCIAL-FAQ.md](COMMERCIAL-FAQ.md)（含灰色地带判定与违规后果）。
- **商标**：「心飞物流TMS」「心飞物流」「心飞为你飞」及项目 Logo 为作者商标 / 品牌，未经书面授权不得用于商业宣传、产品命名或二次分发标识。
- **企业使用**：需要发票、SLA 或闭源二开授权的，直接联系作者签署商业协议。

### 11.6 授权激活与体验流程

#### ① 获取体验码（免费，7 天全功能）

1. 关注作者的微信公众号，发送关键词 **`心飞物流`**，自动回复中复制体验密钥（形如 `xxx.yyy` 的签名串）；
2. 登录管理端，进入 **系统管理 → 授权管理**；
3. 将密钥粘贴到输入框，点击 **「激活授权」**；
4. 激活成功后「体验版」水印消失、体验提示不再弹出，全部功能可用 7 天（刷新 / 重新登录均保持生效）。

#### ② 体验到期后（可无限续期）

- 每轮体验 7 天，到期后页面恢复「体验版」水印，授权管理页显示红色"体验已到期"；
- 不需要新码：点击「重新激活（同一体验码再体验 7 天）」按钮，或重新粘贴同一密钥，即可再获得一轮 7 天体验；
- 顶部通知会提前提醒（每 3 天最多弹一次，不打扰使用）。

#### ③ 购买正式授权（企业 / 商用）

1. 联系作者（见 [11.5 商业授权与商标](#115-商业授权与商标) / [COMMERCIAL-AGREEMENT.md](COMMERCIAL-AGREEMENT.md)）确认授权范围与报价；
2. 付款后，作者通过邮件单独发送正式授权密钥（绑定付款主体全称，含授权类型 / 实例数 / 有效期）；
3. 同样在 **系统管理 → 授权管理** 粘贴激活，即变更为"已授权"状态，长期有效；
4. 正式授权的可公开验真记录见 [AUTH-REGISTRY.md](AUTH-REGISTRY.md)（密钥指纹登记簿，可自行校验授权真伪）。

#### ④ 实现方式

- 密钥是 RSA-2048 签名串，激活与校验全部在本地完成，不需要联网；签名锁定授权对象 / 类型 / 有效期，改动任意字符都会验签失败。
- 体验版水印由前端全局水印组件实现，功能不受限，只作授权状态提示，不阻断任何业务接口。
- 授权状态接口只返回状态与试用天数，不收集 IP、业务数据等敏感信息。
- 机制设计与边界（含防滥用 / 防伪说明）详见 [LICENSE-KEY-DESIGN.md](LICENSE-KEY-DESIGN.md)；未授权版本仍受 BUSL-1.1 约束（见 [COMMERCIAL-FAQ.md](COMMERCIAL-FAQ.md)）。

---

<a id="sec-milestones"></a>

## 十二、里程碑

| 时间      | 进展                                                         |
| ------- | ---------------------------------------------------------- |
| 2026-09 | ✅ 项目立项：四端骨架 + 13 微服务 + 全链路业务闭环                             |
| 2026-10 | ✅ 技术栈现代化：JDK 25 + Spring Boot 4.1.1 + Vue3 + Sa-Token 多租户 + XXL-Job |
| 2026-10-21 | ✅ Boot 4 依赖对齐：MyBatis-Plus boot4 starter（3.5.17）+ springdoc-openapi 3.1.1 + Sa-Token boot4（1.46.0）+ Druid boot4（1.2.28）；commons-io 2.17.0 / httpclient 4.5.14 安全升级 |
| 2026-Q4 | 🔲 轨迹链路升级：Kafka + TDengine / 鹰眼 + ES 运单检索                  |
| 2027-H1 | 🔲 智能排线 OR-Tools + MinIO 回单 + Doris 报表                     |
| 2027-H2 | 🔲 Spring AI 智能客服 + 回单 OCR + 动态 ETA                        |

---

<a id="sec-faq"></a>

## 十三、常见问题（FAQ）

**Q: 能商用吗？**  
A: 个人学习、研究、教学、毕设免费；企业商用（生产环境、私有化部署、对外提供服务等）需联系作者获取商业授权（BUSL-1.1）。

**Q: Gitee 仓库页为什么显示"未知许可证"？**  
A: BUSL-1.1 是较新的协议，暂未被 Gitee 平台的许可证徽章收录，属平台限制；协议全文以仓库 [LICENSE](LICENSE) 文件为准，不影响授权效力。

**Q: 技术栈为什么这么新（JDK 25 + Spring Boot 4.1）？**  
A: 2026 年的新项目没有历史包袱，直接采用最新 LTS，避免日后被迫升级。

**Q: 有文档吗？**  
A: 有，见「9. 相关资料」——架构视图、部署资产、品牌库与协议全文都随仓库提供；内部需求与设计文档不随源码分发，可联系作者。

**Q: 移动端支持小程序吗？**  
A: uni-app 三端架构（App / H5 / 小程序），当前以 H5 为主，小程序发布前需配置真实 appid。

---

<a id="sec-contrib"></a>

## 十四、参与贡献

<div align="center">

![Gitee Stars](https://gitee.com/itxinfei/xinfei-tms/badge/star.svg)

</div>

欢迎提交 Issue 与 Pull Request。环境准备、提交规范、开发红线与 PR 流程见 [CONTRIBUTING.md](CONTRIBUTING.md)；提交 PR 前建议先在 Issue 里聊一下方向，确认一致再动手。安全漏洞请不要走公开 Issue，披露渠道见 [SECURITY.md](SECURITY.md)；社区行为规范见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

---

<p align="center"><sub>心飞物流 TMS · 开源运输管理系统 · Spring Cloud · JDK 25 · Vue 3 · uni-app</sub></p>
