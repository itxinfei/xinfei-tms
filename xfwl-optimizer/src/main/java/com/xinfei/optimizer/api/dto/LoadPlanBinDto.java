package com.xinfei.optimizer.api.dto;

import com.xinfei.optimizer.domain.LoadingStatus;

import java.util.List;

/**
 * 单个车辆/车次的候选配载结果。
 */
public record LoadPlanBinDto(
        Integer binNo,
        String vehicleId,
        String tripsId,
        String driverId,
        String vehicleTypeId,
        List<String> orderIds,
        Integer pieceCount,
        Double totalWeightKg,
        Double totalVolumeM3,
        Double allowableLoadKg,
        Double allowableVolumeM3,
        Double effectiveWeightCapacityKg,
        Double effectiveVolumeCapacityM3,
        Double weightUtilization,
        Double volumeUtilization,
        LoadingStatus loadingStatus,
        Double fixedCost,
        List<String> explanations
) {
}
