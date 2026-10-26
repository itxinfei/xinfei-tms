package com.xinfei.optimizer.engine;

import com.xinfei.optimizer.api.dto.ConstraintViolationDto;

import java.util.List;

/**
 * 已完成基础归一化的配载问题。业务不可行原因放在 violations，字段格式错误直接抛 400。
 */
public record PreparedLoadProblem(
        List<PreparedLoadOrder> orders,
        List<PreparedVehicle> vehicles,
        List<ConstraintViolationDto> violations,
        double totalWeightKg,
        double totalVolumeM3,
        int totalPieceCount
) {
}
