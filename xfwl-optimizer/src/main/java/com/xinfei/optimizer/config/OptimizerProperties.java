package com.xinfei.optimizer.config;

import com.xinfei.optimizer.domain.EngineName;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * xfwl-optimizer 本地配置。服务只做无状态计算，不连接业务库，也不持有任何客户 PII。
 */
@ConfigurationProperties(prefix = "xfwl.optimizer")
public record OptimizerProperties(
        EngineName defaultEngine,
        Integer previewTimeLimitMs,
        Integer asyncTimeLimitMs,
        Integer maxOrderCount,
        Integer maxVehicleCount,
        Double weightSafetyFactor,
        Double volumeFillFactor,
        Integer fullThresholdPercent,
        Boolean compareRule,
        Engines engines
) {

    public static final int DEFAULT_PREVIEW_TIME_LIMIT_MS = 5_000;
    public static final int DEFAULT_ASYNC_TIME_LIMIT_MS = 30_000;
    public static final int DEFAULT_MAX_ORDER_COUNT = 500;
    public static final int DEFAULT_MAX_VEHICLE_COUNT = 80;
    public static final double DEFAULT_WEIGHT_SAFETY_FACTOR = 0.98d;
    public static final double DEFAULT_VOLUME_FILL_FACTOR = 0.85d;
    public static final int DEFAULT_FULL_THRESHOLD_PERCENT = 90;

    public OptimizerProperties {
        if (defaultEngine == null) {
            defaultEngine = EngineName.RULE;
        }
        if (previewTimeLimitMs == null || previewTimeLimitMs <= 0) {
            previewTimeLimitMs = DEFAULT_PREVIEW_TIME_LIMIT_MS;
        }
        if (asyncTimeLimitMs == null || asyncTimeLimitMs <= 0) {
            asyncTimeLimitMs = DEFAULT_ASYNC_TIME_LIMIT_MS;
        }
        if (maxOrderCount == null || maxOrderCount <= 0) {
            maxOrderCount = DEFAULT_MAX_ORDER_COUNT;
        }
        if (maxVehicleCount == null || maxVehicleCount <= 0) {
            maxVehicleCount = DEFAULT_MAX_VEHICLE_COUNT;
        }
        if (weightSafetyFactor == null || weightSafetyFactor <= 0 || weightSafetyFactor > 1) {
            weightSafetyFactor = DEFAULT_WEIGHT_SAFETY_FACTOR;
        }
        if (volumeFillFactor == null || volumeFillFactor <= 0 || volumeFillFactor > 1) {
            volumeFillFactor = DEFAULT_VOLUME_FILL_FACTOR;
        }
        if (fullThresholdPercent == null || fullThresholdPercent <= 0 || fullThresholdPercent > 100) {
            fullThresholdPercent = DEFAULT_FULL_THRESHOLD_PERCENT;
        }
        if (compareRule == null) {
            compareRule = Boolean.TRUE;
        }
        if (engines == null) {
            engines = new Engines(new OrTools(false, "9.12.4544", false),
                    new Timefold(false, "not-bundled"));
        }
    }

    public boolean ortoolsEnabled() {
        return engines != null && engines.ortools() != null && Boolean.TRUE.equals(engines.ortools().enabled());
    }

    public record Engines(OrTools ortools, Timefold timefold) {
        public Engines {
            if (ortools == null) {
                ortools = new OrTools(false, "9.12.4544", false);
            }
            if (timefold == null) {
                timefold = new Timefold(false, "not-bundled");
            }
        }
    }

    public record OrTools(Boolean enabled, String version, Boolean smokeOnStartup) {
        public OrTools {
            if (enabled == null) {
                enabled = Boolean.FALSE;
            }
            if (version == null || version.isBlank()) {
                version = "9.12.4544";
            }
            if (smokeOnStartup == null) {
                smokeOnStartup = Boolean.FALSE;
            }
        }
    }

    public record Timefold(Boolean enabled, String version) {
        public Timefold {
            if (enabled == null) {
                enabled = Boolean.FALSE;
            }
            if (version == null || version.isBlank()) {
                version = "not-bundled";
            }
        }
    }
}
