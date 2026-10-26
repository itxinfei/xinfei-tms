# xfwl-optimizer 无状态智能求解服务

心飞物流 TMS 的独立求解服务，只做**无状态计算**并返回候选方案；不连接业务 MySQL、Redis、RabbitMQ，不直接写业务库。方案落库、容量复核、人工确认、扫码封签和发运门禁仍由 `xfwl-dispatch` / `xfwl-work` 负责。

## 1. 当前范围

| 能力 | 状态 | 说明 |
|---|---|---|
| 规则引擎容量配载 | 已实现 | Best Fit Decreasing，支持重量/体积双约束、安全系数、车辆筛选、多车拆分和不可行原因 |
| OR-Tools MIP 配载 | 已接入 | 依赖 `com.google.ortools:ortools-java`，使用 SCIP 求解 bin packing；默认关闭 |
| Timefold Solver | P2 评估 | 当前不打包；仅允许 Timefold Community（Apache-2.0），禁止 Enterprise 坐标 |
| CVRP 路径规划 | 未实现 | `/routes/solve` 当前返回 501 |
| 司机/月台/波次排班 | 未实现 | `/schedules/solve` 当前返回 501 |
| 方案回放 | 未实现 | `/replay` 当前返回 501 |

## 2. 端口和注册

- 应用名：`xfwl-optimizer`
- 端口：`8193`
- Nacos namespace：`1cb93ce4-dc0e-4730-b759-d35fd7ed93c3`
- Nacos group：`xinfei-tms`
- 仅内网注册和调用，不通过网关对公网暴露。

## 3. HTTP 接口

### 健康检查

```http
GET /actuator/health
```

OR-Tools 开启后，健康检查会包含 `ortools` 组件；默认关闭时不加载原生库。

### 引擎清单

```http
GET /api/v1/optimizer/engines
```

### 容量配载求解

```http
POST /api/v1/optimizer/load-plans/solve
Content-Type: application/json
```

请求示例：

```json
{
  "tenantId": "tenant-test",
  "sceneCode": "MANUAL_PREVIEW",
  "orders": [
    {"orderId": "O1", "pieceCount": 10, "weightKg": 100.0, "volumeM3": 1.0},
    {"orderId": "O2", "pieceCount": 10, "weightKg": 200.0, "volumeM3": 2.0}
  ],
  "vehicles": [
    {
      "vehicleId": "V1",
      "available": true,
      "gpsOnline": true,
      "licenseValid": true,
      "allowableLoadKg": 500.0,
      "allowableVolumeM3": 5.0,
      "fixedCost": 100.0,
      "sortNo": 1
    }
  ],
  "engine": "RULE",
  "fallbackAllowed": true
}
```

说明：

- `engine` 不传时使用配置的默认引擎，当前默认 `RULE`。
- `fallbackAllowed=true` 时，OR-Tools 不可用或求解异常会自动降级规则引擎。
- `fallbackAllowed=false` 时，指定引擎不可用返回 HTTP 503。
- 订单默认作为原子分配单元；没有拆票授权时，一票货不允许拆到多辆车。
- 车辆显式 `available=false`、`gpsOnline=false`、`licenseValid=false` 时剔除。

## 4. 关键配置

```yaml
xfwl:
  optimizer:
    default-engine: RULE
    max-order-count: 500
    max-vehicle-count: 80
    weight-safety-factor: 0.98
    volume-fill-factor: 0.85
    full-threshold-percent: 90
    compare-rule: true
    engines:
      ortools:
        enabled: false
        smoke-on-startup: false
      timefold:
        enabled: false
```

Compose 中开启 OR-Tools：

```bash
XFWL_OPTIMIZER_ORTOOLS_ENABLED=true \
XFWL_OPTIMIZER_ORTOOLS_SMOKE_ON_STARTUP=true \
docker compose -f deploy/apps/docker-compose.app.yml up -d xfwl-optimizer
```

生产开启前必须先在 Linux 镜像内验证原生库加载和 SCIP solver 创建；Windows 本机已通过原生库 smoke test，不代表 Linux 镜像自动通过。

## 5. 构建和测试

```bash
# 只打包当前服务
mvn -pl xfwl-optimizer -am clean package

# 常规测试：OR-Tools 原生 smoke test 默认跳过
mvn -pl xfwl-optimizer test

# 显式执行本机 OR-Tools 原生库 smoke test
mvn -pl xfwl-optimizer -Dtest=OrToolsNativeSmokeTest \
  -DargLine=-Dxfwl.test.ortools.native=true test
```

本地禁用 Nacos 启动 fat jar：

```bash
java -jar xfwl-optimizer/target/xfwl-optimizer-1.0.0-SNAPSHOT.jar \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false
```

## 6. 合规和边界

- 不引入 GPL/AGPL/SSPL 依赖。
- Timefold 只允许 `ai.timefold.solver:*` Community 坐标，禁止 `ai.timefold.solver.enterprise:*`。
- 服务运行期不访问公网，不向外部 SaaS 发送客户姓名、手机号、详细门牌地址。
- 输入快照、候选方案、采用/放弃/人工调整记录由业务侧持久化，要求可审计、可回放、可降级。
- 求解器返回的是候选方案，不代表最终发运指令；必须经过业务侧容量复核和人工确认。
