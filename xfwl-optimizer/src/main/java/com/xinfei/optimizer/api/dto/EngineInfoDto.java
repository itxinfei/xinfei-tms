package com.xinfei.optimizer.api.dto;

import com.xinfei.optimizer.domain.EngineName;

import java.time.Instant;
import java.util.List;

/**
 * 引擎能力和原生库探测结果。
 */
public record EngineInfoDto(
        EngineName engine,
        String displayName,
        String version,
        String license,
        boolean enabled,
        boolean available,
        String status,
        List<String> capabilities,
        String smokeTestMessage,
        Instant checkedAt
) {
}
