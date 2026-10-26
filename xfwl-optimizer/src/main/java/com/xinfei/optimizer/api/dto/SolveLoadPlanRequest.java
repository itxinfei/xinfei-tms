package com.xinfei.optimizer.api.dto;

import com.xinfei.optimizer.domain.EngineName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 容量配载求解请求。只包含脱敏后的资源、货量和约束字段。
 */
public record SolveLoadPlanRequest(
        @NotBlank String tenantId,
        String sceneCode,
        String requestHash,
        @NotNull @Size(min = 1, max = 500) List<@Valid LoadOrderInput> orders,
        @NotNull @Size(min = 1, max = 80) List<@Valid LoadVehicleInput> vehicles,
        EngineName engine,
        Integer timeLimitMs,
        Boolean fallbackAllowed,
        Boolean compareRule
) {
}
