package com.xinfei.optimizer.engine;

import com.xinfei.optimizer.api.dto.ConstraintViolationDto;
import com.xinfei.optimizer.api.dto.LoadPlanBinDto;
import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.ScoreDetailDto;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.domain.LoadingStatus;
import com.xinfei.optimizer.domain.SolveStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 把不同引擎的内部装载结果统一转换成心飞标准 DTO。
 */
@Component
public class LoadPlanResponseAssembler {

    public LoadPlanSolveResponse feasible(
            SolveLoadPlanRequest request,
            EngineName requestedEngine,
            EngineName usedEngine,
            List<BinAssignment> usedBins,
            List<ConstraintViolationDto> violations,
            List<String> warnings,
            long startNanos,
            OptimizerProperties properties
    ) {
        return build(request, requestedEngine, usedEngine, SolveStatus.FEASIBLE, true,
                "LOAD_PLAN_FEASIBLE", "已生成候选配载方案", usedBins, violations, warnings,
                startNanos, properties);
    }

    public LoadPlanSolveResponse infeasible(
            SolveLoadPlanRequest request,
            EngineName requestedEngine,
            EngineName usedEngine,
            List<BinAssignment> usedBins,
            List<ConstraintViolationDto> violations,
            List<String> warnings,
            long startNanos,
            OptimizerProperties properties
    ) {
        return build(request, requestedEngine, usedEngine, SolveStatus.INFEASIBLE, false,
                "LOAD_PLAN_INFEASIBLE", "没有满足全部硬约束的配载方案，需要人工调整车辆、车次或订单",
                usedBins, violations, warnings, startNanos, properties);
    }

    private LoadPlanSolveResponse build(
            SolveLoadPlanRequest request,
            EngineName requestedEngine,
            EngineName usedEngine,
            SolveStatus status,
            boolean feasible,
            String resultCode,
            String message,
            List<BinAssignment> sourceBins,
            List<ConstraintViolationDto> violations,
            List<String> warnings,
            long startNanos,
            OptimizerProperties properties
    ) {
        List<BinAssignment> sortedBins = sourceBins.stream()
                .filter(bin -> !bin.orders().isEmpty())
                .toList();
        List<LoadPlanBinDto> bins = new ArrayList<>();
        double totalWeight = 0d;
        double totalVolume = 0d;
        int totalPieces = 0;
        double totalAllowableWeight = 0d;
        double totalAllowableVolume = 0d;
        double fixedCost = 0d;
        double weightUtilizationSum = 0d;
        double volumeUtilizationSum = 0d;

        for (int i = 0; i < sortedBins.size(); i++) {
            BinAssignment assignment = sortedBins.get(i);
            PreparedVehicle vehicle = assignment.vehicle();
            double weightUtilization = ratio(assignment.totalWeightKg(), vehicle.allowableWeightKg());
            double volumeUtilization = ratio(assignment.totalVolumeM3(), vehicle.allowableVolumeM3());
            double maxUtilization = Math.max(weightUtilization, volumeUtilization);
            LoadingStatus loadingStatus = maxUtilization >= properties.fullThresholdPercent() / 100d
                    ? LoadingStatus.FULL
                    : LoadingStatus.PARTIAL;
            List<String> explanations = List.of(
                    "重量利用率 " + percent(weightUtilization) + "%，体积利用率 " + percent(volumeUtilization) + "%",
                    "硬约束容量按重量安全系数 " + properties.weightSafetyFactor()
                            + "、体积填充系数 " + properties.volumeFillFactor() + " 复核"
            );
            bins.add(new LoadPlanBinDto(
                    i + 1,
                    vehicle.vehicleId(),
                    vehicle.source().tripsId(),
                    vehicle.source().driverId(),
                    vehicle.source().vehicleTypeId(),
                    assignment.orders().stream().map(PreparedLoadOrder::orderId).toList(),
                    assignment.totalPieceCount(),
                    round(assignment.totalWeightKg()),
                    round(assignment.totalVolumeM3()),
                    round(vehicle.allowableWeightKg()),
                    round(vehicle.allowableVolumeM3()),
                    round(vehicle.effectiveWeightKg()),
                    round(vehicle.effectiveVolumeM3()),
                    round(weightUtilization),
                    round(volumeUtilization),
                    loadingStatus,
                    round(vehicle.fixedCost()),
                    explanations
            ));
            totalWeight += assignment.totalWeightKg();
            totalVolume += assignment.totalVolumeM3();
            totalPieces += assignment.totalPieceCount();
            totalAllowableWeight += vehicle.allowableWeightKg();
            totalAllowableVolume += vehicle.allowableVolumeM3();
            fixedCost += vehicle.fixedCost();
            weightUtilizationSum += weightUtilization;
            volumeUtilizationSum += volumeUtilization;
        }

        double overallWeight = ratio(totalWeight, totalAllowableWeight);
        double overallVolume = ratio(totalVolume, totalAllowableVolume);
        double avgWeight = sortedBins.isEmpty() ? 0d : weightUtilizationSum / sortedBins.size();
        double avgVolume = sortedBins.isEmpty() ? 0d : volumeUtilizationSum / sortedBins.size();
        double score = sortedBins.size() * 100_000d
                + fixedCost * 100d
                + (1d - avgWeight) * 1_000d
                + (1d - avgVolume) * 1_000d;
        ScoreDetailDto scoreDetail = new ScoreDetailDto(
                round(score),
                sortedBins.size(),
                round(fixedCost),
                round(avgWeight),
                round(avgVolume),
                List.of("分数越低越好；优先最小化用车数，其次降低固定成本和利用率缺口")
        );

        long durationMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        return new LoadPlanSolveResponse(
                UUID.randomUUID().toString(),
                request.requestHash(),
                request.tenantId(),
                request.sceneCode(),
                requestedEngine,
                usedEngine,
                status,
                feasible,
                resultCode,
                message,
                request.orders().size(),
                request.vehicles().size(),
                sortedBins.size(),
                totalPieces,
                round(totalWeight),
                round(totalVolume),
                round(overallWeight),
                round(overallVolume),
                durationMs,
                List.copyOf(bins),
                List.copyOf(violations),
                List.copyOf(warnings),
                scoreDetail
        );
    }

    private static double ratio(double numerator, double denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return numerator / denominator;
    }

    private static double round(double value) {
        return LoadPlanPreparator.round(value);
    }

    private static String percent(double ratio) {
        return String.format("%.2f", ratio * 100d);
    }
}
