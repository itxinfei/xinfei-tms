package com.xinfei.optimizer.api.dto;

import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.domain.SolveStatus;

import java.util.List;

/**
 * 容量配载求解响应。求解器只返回候选方案，不写业务库。
 */
public record LoadPlanSolveResponse(
        String requestId,
        String requestHash,
        String tenantId,
        String sceneCode,
        EngineName requestedEngine,
        EngineName engineUsed,
        SolveStatus status,
        boolean feasible,
        String resultCode,
        String message,
        Integer orderCount,
        Integer vehicleCount,
        Integer usedVehicleCount,
        Integer totalPieceCount,
        Double totalWeightKg,
        Double totalVolumeM3,
        Double overallWeightUtilization,
        Double overallVolumeUtilization,
        Long solveDurationMs,
        List<LoadPlanBinDto> bins,
        List<ConstraintViolationDto> violations,
        List<String> warnings,
        ScoreDetailDto score
) {
}
