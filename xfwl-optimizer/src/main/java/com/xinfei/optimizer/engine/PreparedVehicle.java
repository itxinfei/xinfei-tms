package com.xinfei.optimizer.engine;

import com.xinfei.optimizer.api.dto.LoadVehicleInput;

/**
 * 归一化后的候选车辆。effective* 是应用安全系数/填充系数后的硬约束容量。
 */
public record PreparedVehicle(
        LoadVehicleInput source,
        boolean eligible,
        String ineligibleReason,
        double effectiveWeightKg,
        double effectiveVolumeM3
) {
    public String vehicleId() {
        return source.vehicleId();
    }

    public double allowableWeightKg() {
        return source.allowableLoadKg();
    }

    public double allowableVolumeM3() {
        return source.allowableVolumeM3();
    }

    public double fixedCost() {
        return source.fixedCost() == null ? 0d : source.fixedCost();
    }
}
