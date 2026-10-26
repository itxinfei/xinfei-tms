package com.xinfei.optimizer.api.dto;

import java.util.List;

/**
 * 候选方案评分明细。分数越低越好，便于多候选方案横向比较。
 */
public record ScoreDetailDto(
        Double score,
        Integer usedVehicleCount,
        Double fixedCost,
        Double averageWeightUtilization,
        Double averageVolumeUtilization,
        List<String> explanations
) {
}
