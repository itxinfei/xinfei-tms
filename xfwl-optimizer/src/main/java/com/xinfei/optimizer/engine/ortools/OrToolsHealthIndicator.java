package com.xinfei.optimizer.engine.ortools;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * OR-Tools 开关开启时，健康检查必须真实加载原生库并创建 SCIP solver；关闭时仅显示 disabled。
 */
@Component("ortools")
public class OrToolsHealthIndicator implements HealthIndicator {

    private final OrToolsRuntimeProbe probe;

    public OrToolsHealthIndicator(OrToolsRuntimeProbe probe) {
        this.probe = probe;
    }

    @Override
    public Health health() {
        OrToolsRuntimeProbe.ProbeResult result = probe.probe();
        return switch (result.status()) {
            case DISABLED -> Health.up()
                    .withDetail("enabled", false)
                    .withDetail("version", result.version())
                    .withDetail("message", result.message())
                    .build();
            case AVAILABLE -> Health.up()
                    .withDetail("enabled", true)
                    .withDetail("version", result.version())
                    .withDetail("message", result.message())
                    .build();
            case UNAVAILABLE -> Health.down()
                    .withDetail("enabled", true)
                    .withDetail("version", result.version())
                    .withDetail("message", result.message())
                    .build();
        };
    }
}
