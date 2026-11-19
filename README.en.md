<div align="center">

# Xinfei Logistics TMS

<p>
  <b>An Open-Source Transportation Management System (TMS) for carriers &amp; forwarders</b><br/>
  <span>Admin console plus three mobile apps, running from booking to settlement</span>
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
  <img src="docs/badges/kuaidi100.svg" alt="Kuaidi100"/>
  <img src="docs/badges/doris.svg" alt="Apache Doris"/>
  <img src="docs/badges/flowable.svg" alt="Flowable"/>
  <img src="docs/badges/jimu-report.svg" alt="JimuReport"/>
  <img src="docs/badges/debezium.svg" alt="Debezium"/>
  <img src="docs/badges/resilience4j.svg" alt="Resilience4j"/>
  <img src="docs/badges/mapstruct.svg" alt="MapStruct"/>
  <img src="docs/badges/zipkin.svg" alt="Zipkin"/>
  <img src="docs/badges/druid.svg" alt="Druid"/>
  <img src="docs/badges/baidu-map.svg" alt="Baidu Map"/>
  <img src="docs/badges/netty.svg" alt="Netty"/>
  <img src="docs/badges/vue.svg" alt="Vue"/>
  <img src="docs/badges/uniapp.svg" alt="uni-app"/>
</p>

</div>

<p align="center"><a href="README.md">简体中文</a> · <b>English</b></p>

> Xinfei TMS is an open-source transportation management system: the admin console and the customer, courier and driver apps share one backend, built on JDK 25 + Spring Boot 4.1.1 + Vue 3.5. The chain runs from booking, orders, intelligent dispatch and operations through tracking to proof of delivery. Dispatch rules live in Drools, tracking ingestion is a self-built Netty service, and auth is Sa-Token with multi-tenancy.

<p align="center">
<b><a href="#sec-tech"><code>15</code></a></b> microservices · <b><code>4</code></b> apps · <b><code>106</code></b> functional requirements · <b><code>71</code></b> database tables · <b><code>27</code></b> technologies
</p>

<p align="center">
<b>Live demo</b>: <a href="https://itxinfei.github.io/xinfei-tms/" target="_blank">https://itxinfei.github.io/xinfei-tms/</a> ｜ Account <code>admin / 123456</code>
</p>

---

## Table of Contents

- [1. What &amp; Why](#sec-intro)
- [2. Who It's For and What's Different](#sec-advantages)
- [3. Pain Points and What It Does About Them](#sec-pain)
- [4. The Four Apps](#sec-ends)
- [5. Feature Overview](#sec-features)
- [6. Technical Approach](#sec-tech)
- [7. Quick Start](#sec-quickstart)
- [8. Screenshots](#sec-screens)
- [9. Documents](#sec-docs)
- [10. Pre-built VM Image](#sec-vm)
- [11. Licensing, Services and Contact](#sec-contact)
- [12. Milestones](#sec-milestones)
- [13. FAQ](#sec-faq)
- [14. Contributing](#sec-contrib)

---

<a id="sec-intro"></a>

## 1. What &amp; Why

The four most common logistics business models are **express, less-than-truckload, dedicated lines and third-party logistics (3PL)**. Regardless of the model, the core pain points are the same: vehicles, drivers and routes are scattered and hard to manage; the transport process is opaque; dispatch and billing rely on manual work; cost and service-level performance cannot be evaluated.

Xinfei TMS puts resource preparation, ordering, dispatch, transport, delivery, signing and settlement into one flow. Vehicle, driver and route data sit in one place, and statistics and evaluation reports come out of it.

> Commercial TMS products run ¥30K–300K a year and take about six months to implement; most open-source ones still sit on JDK 8 + Vue 2. This project publishes its source and is free for personal learning and research. The business from ordering to settlement is implemented here, the stack is current, and all four apps ship in the repo, so it runs once cloned and leaves room for secondary development.

---

<a id="sec-advantages"></a>

## 2. Who It's For and What's Different

### 2.1 Six Points

| # | Point | Note |
| --- | --- | --- |
| 1 | **4 ends, one backend** | Admin, customer, courier and driver apps; most open-source TMS ship only an admin console |
| 2 | **JDK 25 + Spring Boot 4.1.1 + Vue 3.5** | Current stack; most similar projects still sit on JDK 8 + SB 2.x + Vue 2 |
| 3 | **Dispatch via Drools + XXL-Job** | Rules and freight calculation sit in a rule engine instead of if-else; distributed scheduling replaces Quartz |
| 4 | **Self-built Netty tracking ingestion** | `xfwl-netty` is a separate service with no third-party SDK |
| 5 | **Sa-Token multi-tenancy** | One codebase serving several tenants |
| 6 | **Roadmap in the repo** | Kafka, TDengine / Yingyan, OR-Tools and Spring AI are planned; see §6.4 |

### 2.2 Who Is It For?

- **Small and medium fleets** (10–50 vehicles): want digitalization but cannot afford ¥30K–300K annual fees
- **Logistics startups**: need a runnable 4-end TMS as an MVP
- **Java learners**: one codebase to practice microservices, rule engine, Netty and multi-tenancy
- **Secondary developers**: deliver custom work for clients without spending 3 months on groundwork

### 2.3 Comparison

| Capability | Xinfei TMS | Similar open-source TMS | Commercial TMS |
| --- | --- | --- | --- |
| 4-end synergy (Admin+Customer+Courier+Driver) | ✅ | ❌ admin only | ✅ |
| JDK 25 + Spring Boot 4.1 | ✅ | ❌ mostly JDK 8 | partially |
| Multi-tenant SaaS | ✅ | ❌ | ✅ |
| Intelligent dispatch (Drools) | ✅ | ❌ if-else | ✅ |
| Netty self-built tracking ingestion | ✅ | ❌ | ✅ |
| Annual fee | **0 (learning/research)**; commercial use requires license | 0 | **¥30K–300K** |
| Freedom to customize | ✅ source-available (free for personal learning/research, commercial use requires license) | ✅ | ❌ black box |

> "Similar open-source TMS" refers to mainstream open-source TMS projects on Gitee/GitHub in general, not any specific project; corrections are welcome via Issue.

### 2.4 See It In Action

<table>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-login.png" alt="Admin login"/>
  <br/><sub><b>Admin Login</b> · unified account + captcha</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-dashboard.png" alt="Admin dashboard"/>
  <br/><sub><b>Dashboard</b> · orders / alarms / todos &amp; charts</sub>
</td>
</tr>
</table>

### 2.5 Feature Progress Matrix

|  |  |  |  |
| --- | --- | --- | --- |
| ✅ Order Management | ✅ Intelligent Dispatch | ✅ Operations | ✅ Tracking |
| ✅ Multi-Tenant Auth | ✅ Master Data | ✅ Waybill | ✅ Reports |
| ✅ 4-End Apps | ✅ Permission / Menu | ✅ Vehicle / Route | ✅ Personnel |
| 🔲 Settlement | 🔲 OR-Tools Route Planning | 🔲 POD OCR | 🔲 Dynamic ETA |
| 🔲 AI Assistant (Spring AI) | 🔲 ES Search | 🔲 MinIO Object Storage | 🔲 Kafka Pipeline |

---

<a id="sec-pain"></a>

## 3. Pain Points and What It Does About Them

| Industry pain point | How Xinfei TMS solves it |
| --- | --- |
| Vehicles / drivers / routes scattered, hard to evaluate | Vehicles, drivers and routes sit in one master-data set; reports cover all of them |
| Manual dispatch, experience-based load planning | Scheduled dispatch and route planning create transport tasks, then assign trips, vehicles and drivers |
| Opaque in-transit process | GPS reports vehicle position, tracks can be replayed, and deviation, overspeed or offline events raise an alarm |
| Opaque freight calculation, error-prone | Billing rules live in Drools, so changing a rule needs no code change and every calculation can be traced |
| Customers have no way to track, no POD evidence | The customer app shows shipment status; signing keeps an electronic receipt and POD photos |
| Data silos, manual statistics | Data lands in one place, and volume, service level and cost reports come out of it |

---

<a id="sec-ends"></a>

## 4. The Four Apps

| End | Name | Users | Core capabilities |
| --- | --- | --- | --- |
| Admin Console | Xinfei TMS Admin | Internal admins | Master data, order management, waybill management |
| Customer App | **Xinfei Express** | External customers | Booking, shipment tracking |
| Courier App | **Xinfei Courier** | Internal couriers | Pickup &amp; delivery tasks |
| Driver App | **Xinfei Driver** | Internal drivers | Transport tasks, location reporting |

---

<a id="sec-features"></a>

## 5. Feature Overview

### 5.1 End-to-End Business Flow

![Business flow](docs/business-flow.png)

```
Booking → Order creation → Intelligent dispatch (Drools) → Driver accepts
→ Operations → Tracking (Netty) → POD → Settlement
```

### 5.2 Core Modules

| Module | Description |
| --- | --- |
| **Order Management** | Booking, cargo management, full lifecycle status, freight estimation |
| **Intelligent Dispatch** | Order classification, route planning, transport task creation, trip/vehicle/driver assignment |
| **Transport Management** | Task execution, driver job sheets, in-transit status tracking |
| **GPS Tracking / Monitoring** | Real-time vehicle location, track replay, deviation &amp; anomaly alerts |
| **Delivery Execution** | Pickup/delivery tasks, waybill management, electronic POD |
| **Resource Management** | Fleet, vehicle, vehicle type, line, trip maintenance |
| **Personnel Management** | Driver, courier, position &amp; organization management |
| **Customer Portal** | Customer app: booking, tracking, logistics timeline |
| **POD &amp; Settlement** | POD evidence, cost settlement, reconciliation |
| **Data Reports** | Volume, service level, cost, performance statistics |
| **Master Data** | Organization, menu, permission, dictionary |
| **Unified Auth &amp; Permission** | Sa-Token based user / role / menu / multi-tenant auth |

---

<a id="sec-tech"></a>

## 6. Technical Approach

### 6.0 Project Structure

```
xinfei-tms/
├── Microservices (15)
│   ├── xfwl-gateway/           # Unified gateway (Spring Cloud Gateway)
│   ├── xfwl-auth/              # Auth center (Sa-Token + multi-tenant)
│   ├── xfwl-base/              # Master data (org / vehicle / line / cargo)
│   ├── xfwl-oms/               # Order management (OMS)
│   ├── xfwl-dispatch/          # Intelligent dispatch (Drools + XXL-Job)
│   ├── xfwl-work/              # Operations (pickup/delivery & transport tasks)
│   ├── xfwl-user/              # Users & members
│   ├── xfwl-file-server/       # Attachments (POD photos upload/view, no DB)
│   ├── xfwl-netty/             # Tracking ingestion (Netty TCP)
│   ├── xfwl-aggregation/       # Data aggregation
│   ├── xfwl-optimizer/         # Load planning (OR-Tools + rule engine)
│   └── xfwl-web/               # BFF layer (4-end APIs)
│       ├── xfwl-web-manager/   #   Admin BFF
│       ├── xfwl-web-courier/   #   Courier BFF
│       ├── xfwl-web-customer/  #   Customer BFF
│       └── xfwl-web-driver/    #   Driver BFF
├── Monolith (coexists, zero code copy)
│   └── xfwl-monolith/          # Single-process assembly: embedded gateway + Feign loopback
├── Support libs (2 jars)
│   ├── xfwl-common/            # Common components (utils / context / interceptors)
│   └── xfwl-service-api/       # Inter-service API contracts (Feign)
├── Frontend (4 ends)
│   ├── xfwl-admin-ui-vue3/     # Admin (Vue3 + Element Plus)
│   └── xfwl-mobile/            # Mobile 3 ends (uni-app Vue3)
│       ├── customer/         #   Customer · Xinfei Express
│       ├── courier/          #   Courier · Xinfei Courier
│       └── driver/           #   Driver · Xinfei Driver
├── deploy/                   # Docker Compose orchestration & deployment scripts
└── docs/                     # Docs + screenshots + badges
```

### 6.1 System Architecture

![System architecture](docs/system-arch.png)

> The system has four layers. The presentation layer is the admin console (Vue 3) and the three mobile apps (uni-app). The gateway layer is Spring Cloud Gateway, which handles Sa-Token auth and Same-Token anti-direct-access. The service layer is 15 business microservices registered and configured through Nacos. The data layer is a single MySQL database split by domain, plus Redis and RabbitMQ.

### 6.2 Microservice Architecture

![Microservice architecture](docs/microservice-arch.png)

> Microservices split by business domain: `xfwl-auth`, `xfwl-gateway`, `xfwl-base`, `xfwl-oms`, `xfwl-dispatch`, `xfwl-work`, `xfwl-netty`, `xfwl-aggregation`, `xfwl-optimizer`, etc., communicating via OpenFeign, registered and discovered through Nacos.

### 6.2.1 Monolith (xfwl-monolith)

To lower the deployment and ops barrier for small and mid-sized customers, the repo also ships a **monolith** alongside the microservices, coexisting without interference:

- **Zero business-code copy**: `xfwl-monolith` only does assembly; all Controller/Service/Mapper/Entity are reused from the microservice modules;
- **Microservices untouched**: every adaptation lives in the monolith assembly layer, no microservice business or config files are changed;
- **Zero frontend change**: the same Vue3 admin and uni-app, identical API contracts; only switch the proxy target from gateway `8760` to monolith `8080`;
- **Embedded gateway**: `GatewayRoutingFilter` replicates the gateway routes and StripPrefix, injecting Same-Token, userid and tenantId headers;
- **Feign loopback**: Nacos disabled; SimpleDiscoveryClient statically points the ~49 Feign clients to this process, with module prefixes hitting the right controllers and no hand-written delegates;
- **Unified prefixes + security/exception replication**: `MonolithSecurityConfig` and `MonolithExceptionHandler` centralize login interception and HTTP error semantics; `MonolithSaTokenDao` keeps the KEEPTTL write compatible with old Redis (&lt; 6.0).

> The monolith defaults to port 8080 (Tomcat) and Netty TCP 8192, using the single `xinfei_tms` database and the same Redis — one-command startup on a single machine; switch to microservices when horizontal scaling is needed.

### 6.3 Technology Architecture

![Technology architecture](docs/tech-arch.png)

#### Backend Stack

| Category | Technology | Version | Notes |
|---|---|---|---|
| **Core** | JDK | 25 (LTS) | Virtual threads |
| | Spring Boot | 4.1.1 | Jakarta EE 10 |
| | Spring Cloud | 2025.1.2 | Aligned with Boot 4.1 |
| | Spring Cloud Alibaba | 2025.1.0.0 | Nacos registry/config |
| | Spring Cloud Gateway | 5.0.2 | Reactive (WebFlux) |
| **Persistence** | MyBatis-Plus | 3.5.17 | boot4 starter, MyBatis-Spring 4.0.0 |
| | Druid | 1.2.28 | boot4 starter connection pool |
| | MySQL | 8.0.x | utf8mb4, single-db-by-domain |
| **Auth** | Sa-Token | 1.46.0 | boot4 starter, multi-tenant |
| **Rule Engine** | Drools | 8.38.0.Final | Freight calculation & dispatch rules |
| **Scheduling** | XXL-Job | 2.4.1 | Distributed task scheduling |
| **Others** | FastJSON2 | 2.0.53 | JSON serialization |
| | springdoc-openapi | 3.1.1 | API docs (Boot4 compatible) |
| | MapStruct | 1.6.3 | DTO/Entity mapping |
| | Lombok | 1.18.42 | Annotation processor |
| | EasyExcel | 3.3.4 | Excel import/export |
| | EasyCaptcha | 1.6.2 | Login captcha |

#### Middleware

| Middleware | Version | Port | Purpose |
|---|---|---|---|
| MySQL | 8.0.46 | 3306 | Business database |
| Redis | 7.x | 6379 | Cache + session |
| Nacos | 2.3.2 | 8848 / 9848 | Registry + config center |
| RabbitMQ | 3.12 | 5672 / 15672 | Message bus |
| XXL-Job | 2.4.1 | 8090 | Distributed scheduling |

#### Frontend Stack

| App | Technology | Notes |
|---|---|---|
| Admin | Vue 3.5 + Vite + Element Plus + Pinia + ECharts | Management console |
| Driver | uni-app Vue3 + uview-plus | H5 mobile app |
| Courier | uni-app Vue3 + uview-plus | H5 mobile app |
| Customer | uni-app Vue3 + uview-plus | H5 mobile app |

### 6.4 Tech Highlights

#### 6.4.1 Landed

| Technology | Role in the system |
| --- | --- |
| **JDK 25 Virtual Threads** | Higher throughput on IO-heavy scenarios, lower thread overhead |
| **Drools 8.38 rule engine** | Configurable, hot-reloadable freight &amp; business rules |
| **XXL-Job 2.4.1 distributed scheduling** | Unified orchestration of dispatch &amp; periodic tasks (replaces Quartz) |
| **Sa-Token + JWT multi-tenant auth** | Unified login, stateless auth, multi-tenant &amp; captcha |
| **Spring Cloud Gateway** | Routing, login-state validation &amp; anti-direct-access |
| **Netty** | High-concurrency TCP channel for tracking reporting &amp; device access |
| **OR-Tools 9.12 (`xfwl-optimizer`)** | Load-plan solving; falls back to the rule engine when OR-Tools is unavailable |
| **Baidu Map** | Address resolution, distance calculation, track display |
| **Druid connection pool** | Connection management &amp; SQL monitoring |

#### 6.4.2 Why These Choices

| Choice | Why not the alternative? |
| --- | --- |
| **Sa-Token** over Shiro | Lighter, native multi-tenancy, complete Chinese docs, no Boot 4 compatibility baggage |
| **XXL-Job** over Quartz | Distributed scheduling with built-in admin console; Quartz means building your own scheduler |
| **Drools** over if-else | Rules are visible, hot-reloadable, no redeploy needed |
| **Single-db-by-domain** over 8 databases | Lower dev/small-scale ops cost; evolve to time-series DB at scale |
| **Resilience4j** over Hystrix | Hystrix is archived; R4 is the Spring Cloud official direction (planned) |

#### 6.4.3 Code Snippets

**Drools dispatch rule** (`xfwl-dispatch`) — rules are configuration, hot-reloaded without redeploy:

```java
rule "same-city order priority"
when
    $order : Order(distanceType == "LOCAL", priority >= 5)
    $driver : Driver(status == "IDLE", city == $order.pickupCity)
then
    $order.assignTo($driver);
end
```

**Netty tracking ingestion** (`xfwl-netty`) — self-built TCP channel, no third-party SDK:

```java
@ChannelHandler.Sharable
public class LocationServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
        // parse location protocol → store track → push to dispatch / monitoring
    }
}
```

#### 6.4.4 Evolution Roadmap (Planned)

Reviewed and approved, not yet landed, grouped by priority:

| Technology | Purpose | Priority |
| --- | --- | --- |
| **MinIO object storage** | POD &amp; signature photos (S3 protocol) | P1 near-term |
| **Zipkin tracing** | Micrometer Tracing + Zipkin call-chain monitoring | P1 near-term |
| **Resilience4j** | Circuit breaker, fallback &amp; rate limiting | P1 near-term |
| **MapStruct** | Compile-time type-safe mapping instead of reflection copy | P1 near-term |
| **Kafka** | Track pipeline (Netty → Kafka → time-series store) | P2 mid-term |
| **Debezium CDC** | binlog change capture, MySQL → Kafka → ES / Doris sync | P2 mid-term |
| **Elasticsearch** | Waybill multi-condition &amp; fuzzy search | P2 mid-term |
| **Google OR-Tools** | VRP optimization (CVRP / VRPTW); load-plan solving already runs in `xfwl-optimizer` | P2 mid-term |
| **Apache Doris OLAP** | Reports &amp; analytics, MySQL-protocol compatible | P2 mid-term |
| **Baidu Yingyan** | Track correction &amp; geofencing | P2 mid-term |
| **Spring AI 2.0** | LLM chat / RAG / agent orchestration for smart customer service | P3 long-term |
| **Kuaidi100** | Multi-carrier tracking, e-waybill printing, booking | P3 long-term |
| **Flowable workflow** | Approval flows: exception handling, re-dispatch, reconciliation | P3 long-term |
| **JimuReport** | Visual reports &amp; big-screen dashboards | P3 long-term |

### 6.5 Database Design

Business tables all live in **one MySQL database** (`xinfei_tms`), prefixed by business domain, and can be split per domain as volume grows:

| Domain | Main tables |
| --- | --- |
| Master data | cargo types, vehicles, fleets, lines, organizations, stations |
| Order (OMS) | orders, cargo info, order locations |
| Operations | pickup/delivery tasks, transport tasks, driver job sheets, waybills |
| Dispatch | order classification, route caches |
| Auth &amp; permission | tenants, users, roles, menus, resources |
| Tracking &amp; location | vehicle tracks &amp; locations (time-series DB planned at scale, see §6.4) |

---

<a id="sec-quickstart"></a>

## 7. Quick Start

### 7.1 Environment &amp; Stack

- **Backend**: JDK 25 + Spring Boot 4.1.1 + Spring Cloud 2025.1.2 (Spring Cloud Alibaba 2025.1.0.0), Nacos registry/config, OpenFeign, Spring Cloud Gateway auth, Sa-Token multi-tenant auth, XXL-Job, Drools.
- **Frontend**: Admin `xfwl-admin-ui-vue3` (Vue 3 + Vite + Element Plus + Pinia + ECharts); Mobile uni-app (Vue 3 / Vite + uview-plus) for 3 apps.
- **Storage &amp; middleware**: MySQL (single-db-by-domain), Redis cache, RabbitMQ, Druid; MinIO &amp; ES are planned (see §6.4).
- **Support**: unified auth center (Sa-Token multi-tenant + captcha), gateway anti-direct-access, Netty tracking ingestion.

### 7.2 Run in Five Minutes

```bash
git clone https://gitee.com/itxinfei/xfwl.git
cd xinfei-tms
docker-compose up -d        # start middleware: MySQL / Redis / RabbitMQ / Nacos
# ① Initialize the database: DDL & seed scripts are bundled in the pre-built VM image (see "10. Pre-built VM Image");
#    the sql/ directory is not distributed with this repo — contact the author if needed
# ② start xfwl-auth → xfwl-gateway → business microservices (mvn spring-boot:run or deploy scripts)
# ③ start admin (pnpm i && pnpm dev) and mobile (run uni-app in HBuilderX)
```

> Expected 5–10 minutes. Detailed deployment, config &amp; secondary-development docs: see 「9. Documents」; a pre-built VM image (middleware pre-installed) is available from the author — see 「10. Pre-built VM Image」.

### 7.3 Monolith One-Command Startup (no Nacos / multi-process)

```bash
# Prereq: reachable MySQL 8 (create xinfei_tms and import sql/ DDL & seed) and Redis
mvn -pl xfwl-monolith package -DskipTests      # build the monolith fat jar
java -jar xfwl-monolith/target/xfwl-monolith-1.0.0-SNAPSHOT.jar
# Tomcat 8080 (HTTP) + Netty 8192 (TCP); point the frontend proxy to http://localhost:8080
```

> The monolith defaults to the DB and Redis on `192.168.20.130`. Override for local debugging with env vars: `MYSQL_HOST`, `REDIS_HOST`, `REDIS_PASSWORD`, `SERVER_PORT`.

---

<a id="sec-screens"></a>

## 8. Screenshots

> Screenshots are from a real running environment (updated 2026-10-15). Admin core business pages and the 3 mobile apps are all captured.

### 8.1 Admin Console

<table>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-login.png" alt="Admin login"/>
  <br/><sub><b>Admin Login</b> · unified account + tenant + captcha</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-dashboard.png" alt="Admin dashboard"/>
  <br/><sub><b>Dashboard</b> · orders / alarms / todos &amp; charts</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-order.png" alt="Order management"/>
  <br/><sub><b>Order Management</b> · status filter / paginated list (60 records)</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-waybill.png" alt="Waybill management"/>
  <br/><sub><b>Waybill Management</b> · waybill status / line haul (45 records)</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-dispatch.png" alt="Transport tasks"/>
  <br/><sub><b>Transport Tasks</b> · dispatch-generated tasks (33 records)</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-pickup.png" alt="Pickup/delivery tasks"/>
  <br/><sub><b>Pickup/Delivery Tasks</b> · pickup &amp; delivery (44 records)</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-driver-job.png" alt="Driver jobs"/>
  <br/><sub><b>Driver Jobs</b> · driver pickup / unloading (13 records)</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-alarm.png" alt="Alarm center"/>
  <br/><sub><b>Alarm Center</b> · deviation / overspeed / offline alarms (22, 14 open)</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-truck.png" alt="Truck management"/>
  <br/><sub><b>Truck Management</b> · vehicle profiles &amp; status (22 records)</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-driver.png" alt="Driver management"/>
  <br/><sub><b>Driver Management</b> · driver profiles / Xinfei fleet (10 records)</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/admin-line.png" alt="Line management"/>
  <br/><sub><b>Line Management</b> · trunk / feeder lines (22 records)</sub>
</td>
<td align="center" width="50%"></td>
</tr>
</table>

### 8.2 Mobile Apps

<table>
<tr>
<td align="center" width="100%">
<b>Customer · Xinfei Express</b> (H5, :8084)<br/><br/>
<img src="docs/screenshots/mobile-customer-home.png" alt="Customer home" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-customer-order.png" alt="Book order" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-customer-list.png" alt="My waybills" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-customer-track.png" alt="Waybill detail" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<br/><sub>Home (in-progress orders) · Book a shipment · My waybills · Waybill detail / tracking</sub>
</td>
</tr>
<tr>
<td align="center" width="100%">
<b>Courier · Xinfei Courier</b> (H5, :8083)<br/><br/>
<img src="docs/screenshots/mobile-courier-list.png" alt="Task list" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-courier-detail.png" alt="Task detail" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<br/><sub>Pickup/delivery task list · Task detail (sign / inbound / handover / verify)</sub>
</td>
</tr>
<tr>
<td align="center" width="100%">
<b>Driver · Xinfei Driver</b> (H5, :8082)<br/><br/>
<img src="docs/screenshots/mobile-driver-home.png" alt="Driver home" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<img src="docs/screenshots/mobile-driver-location.png" alt="Location report" style="width:23%;margin:0 4px;border:1px solid #e5e7eb;border-radius:6px;"/>
<br/><sub>Home / transport tasks · Location reporting toggle (enabled)</sub>
</td>
</tr>
</table>

### 8.3 Ops &amp; Middleware

<table>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-deploy-status.png" alt="Deploy status overview"/>
  <br/><sub><b>Deploy Status</b> · all microservices health monitoring</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-nacos.png" alt="Nacos console"/>
  <br/><sub><b>Nacos</b> · registry / config center</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-rabbitmq.png" alt="RabbitMQ management"/>
  <br/><sub><b>RabbitMQ</b> · message queue monitoring</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-xxljob.png" alt="XXL-Job console"/>
  <br/><sub><b>XXL-Job</b> · scheduled tasks &amp; reports</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-docker.png" alt="Docker containers"/>
  <br/><sub><b>Docker</b> · microservice &amp; middleware container health</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-gitea-workflow.png" alt="Gitea CI/CD workflow"/>
  <br/><sub><b>Gitea CI/CD</b> · ordered backend deployment + per-service health checks</sub>
</td>
</tr>
<tr>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-mysql.png" alt="MySQL database"/>
  <br/><sub><b>MySQL 8.0</b> · direct query · xinfei_tms all 71 tables (InnoDB)</sub>
</td>
<td align="center" width="50%">
  <img src="docs/screenshots/ops-redis.png" alt="Redis keys"/>
  <br/><sub><b>Redis 7</b> · Sa-Token session cache &amp; TTL</sub>
</td>
</tr>
</table>

---

<a id="sec-docs"></a>

## 9. Documents

In-repo resources you can browse directly; internal design documents (requirements, API contracts, data dictionary) are **not distributed with the source repo** — contact the author if needed:

| Resource | Description |
| --- | --- |
| [docs/architecture/](docs/architecture/) | Architecture view sources (business flow / runtime topology / data ownership / order lifecycle .dot diagrams & scripts) |
| [deploy/](deploy/) | One-stop deployment assets: middleware & app Docker Compose, CI/CD workflows, deploy scripts, sanitized .env example |
| [docs/sql/区划数据/](docs/sql/区划数据/) | 4-level administrative division data (CSV) with coordinate / pinyin generation toolchain |
| [docs/brand/](docs/brand/) | Brand assets (full-size logos / favicon) |
| [SECURITY.md](SECURITY.md) · [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) · [CONTRIBUTING.md](CONTRIBUTING.md) | Community files: vulnerability disclosure / code of conduct / contribution guide |
| [LICENSE](LICENSE) | BUSL-1.1 license (free for personal study & research; commercial use requires authorization) |

**Design diagrams** (in `docs/`): `system-arch.png` (system), `microservice-arch.png` (microservices), `tech-arch.png` (technology), `business-flow.png` (business flow).

---

<a id="sec-vm"></a>

## 10. Pre-built VM Image

> Learning and deployment both depend on the pre-built VM image. Middleware, database data and the configuration each service needs are already inside it, so there is no separate environment to set up and no data to migrate.

### 10.1 What's inside the image

| Category | Component | State inside the image |
| --- | --- | --- |
| Database | MySQL 8.0 | Business DB `xinfei_tms` (all tables + demo data) and scheduler DB `xxl_job`, persisted on disk |
| Cache | Redis 7 | Sa-Token sessions / J2Cache L2 cache, `dump.rdb` persisted |
| Message queue | RabbitMQ | Exchanges / queues / accounts persisted |
| Registry / Config | Nacos | Namespace `xinfei-tms`; all microservice configs (per-service dataIds) are already published in the image |
| Job scheduling | XXL-Job | Scheduler center + executor config ready |
| Reverse proxy | Nginx | Frontend static assets + backend gateway proxy, pre-configured in the image |
| Source / CI | Gitea + Runner | Project source repo and CI/CD pipeline built-in |

### 10.2 Key points

- The data is in the image: MySQL / Redis / RabbitMQ business data, the Nacos configuration and each microservice's `.env` are all included, so nothing has to be loaded separately and nothing points at an external service.
- The VM's internal IP is `192.168.20.130` (VMware NAT segment); accessible right after import. To change the IP/segment, see `docs/05-部署文档.md`.
- Default accounts: `pdwl / 123456` (SSH), `MySQL root / 123456`; for the application admin login, see the `docs/` login notes or contact the author.

### 10.3 How to get it

The image is distributed via **Baidu Netdisk**; the link is **not public** — contact the author for the latest address (see Chapter 11).

---

<a id="sec-contact"></a>

## 11. Licensing, Services and Contact

<div align="center">

<table>
<tr>
<td align="center" width="50%">

### 11.1 Paid Technical Services

This project is licensed under **BUSL-1.1**: free for personal learning, research, teaching and graduation projects; commercial use (production, private deployment, providing services, etc.) requires a commercial license from the author. The author also offers commercial support:

- **Secondary development / customization** (platform integration, new features, UI adjustments)
- **Private deployment delivery** (server, domain, environment, incl. VM image)
- **Project walkthrough / thesis Q&amp;A** (architecture, source code, interview topics)
- **Full database init scripts + demo data**
- Daily technical Q&amp;A and long-term maintenance support

<b>Business contact QQ: <code>747011882</code></b>

</td>
<td align="center" width="50%">

### 11.2 Join the Community

<img src="docs/心飞为你飞.jpg" width="170" alt="WeChat QR code" style="border-radius:8px;border:1px solid #e5e7eb;">

<sub>Scan to follow WeChat official account 「心飞为你飞」</sub>

<br>

Repo: [gitee.com/itxinfei/xfwl](https://gitee.com/itxinfei/xfwl)<br/>
Author: [gitee.com/itxinfei](https://gitee.com/itxinfei)

</td>
</tr>
</table>

<br>

### 11.3 If this project helps you

A Star is enough.

</div>

### 11.4 Sponsorship

> This project is developed with AI assistance, and further iteration takes both time and AI tool costs. If Xinfei TMS has been useful to you, sponsoring the author keeps it moving.

> **Sponsorship contact**: add QQ `747011882` to discuss (sponsors will be listed in this README); WeChat / Alipay QR codes are still being prepared.

---

<a id="sec-milestones"></a>

## 12. Milestones

| Time | Progress |
| --- | --- |
| 2026-09 | ✅ Project kickoff: 4-end skeleton + 13 microservices + full business closed loop |
| 2026-10 | ✅ Stack modernization: JDK 25 + SB 4.1.1 + Vue3 + Sa-Token multi-tenant + XXL-Job |
| 2026-Q4 | 🔲 Tracking pipeline: Kafka + TDengine / Yingyan + ES waybill search |
| 2027-H1 | 🔲 OR-Tools route planning + MinIO POD + Doris reports |
| 2027-H2 | 🔲 Spring AI assistant + POD OCR + dynamic ETA |

---

<a id="sec-faq"></a>

## 13. FAQ

**Q: Can I use it commercially?**
A: Free for personal learning, research, teaching and graduation projects; commercial use by enterprises (production environment, private deployment, providing services, etc.) requires a commercial license from the author (BUSL-1.1).

**Q: Why does Gitee show "Unknown license"?**
A: BUSL-1.1 is a newer license not yet indexed by Gitee's badge catalog — a platform limitation. The authoritative text is the [LICENSE](LICENSE) file in this repo, which remains fully effective.

**Q: Why such a modern stack (JDK 25 + Spring Boot 4.1)?**
A: A new project in 2026 carries no legacy baggage; we adopt the latest LTS to avoid forced upgrades later.

**Q: Is there documentation?**
A: Yes. See "9. Documents": architecture view sources, deployment assets, brand assets and the full license text ship with the repo. Internal requirements and design documents are not distributed with the source; contact the author if you need them.

**Q: Does the mobile side support mini-programs?**
A: uni-app 3-end architecture (App / H5 / Mini-Program); currently H5-first. A real appid is required before publishing mini-programs.

---

<a id="sec-contrib"></a>

## 14. Contributing

<div align="center">

<img src="https://gitee.com/itxinfei/xfwl/badge/star.svg" alt="Gitee Stars"/>
<img src="https://gitee.com/itxinfei/xfwl/badge/fork.svg" alt="Gitee Forks"/>

</div>

Issues and Pull Requests are welcome! For environment setup, commit conventions, development rules and the PR workflow, see [CONTRIBUTING.md](CONTRIBUTING.md). Please discuss the direction in an Issue before submitting a PR. **Do NOT report security vulnerabilities via public Issues** — see [SECURITY.md](SECURITY.md) for the disclosure channel; community standards: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

---

<p align="center"><sub>Xinfei Logistics TMS · Open-source transportation management system · Spring Cloud · JDK 25 · Vue 3 · uni-app</sub></p>
