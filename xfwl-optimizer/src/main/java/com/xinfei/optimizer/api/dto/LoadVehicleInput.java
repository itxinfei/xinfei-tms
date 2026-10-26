package com.xinfei.optimizer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 候选车辆最小输入。调用方应先完成业务筛选；求解器仍会对 available/GPS/证照布尔位做硬约束。
 */
public record LoadVehicleInput(
        @NotBlank String vehicleId,
        String tripsId,
        String driverId,
        String vehicleTypeId,
        Boolean available,
        Boolean gpsOnline,
        Boolean licenseValid,
        @NotNull @Positive Double allowableLoadKg,
        @NotNull @Positive Double allowableVolumeM3,
        @PositiveOrZero Double fixedCost,
        Integer sortNo
) {
}
