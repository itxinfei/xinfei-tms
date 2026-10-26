package com.xinfei.optimizer.engine.rule;

import com.xinfei.optimizer.api.dto.ConstraintViolationDto;
import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.engine.BinAssignment;
import com.xinfei.optimizer.engine.LoadPlanEngine;
import com.xinfei.optimizer.engine.LoadPlanResponseAssembler;
import com.xinfei.optimizer.engine.PreparedLoadOrder;
import com.xinfei.optimizer.engine.PreparedLoadProblem;
import com.xinfei.optimizer.engine.PreparedVehicle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 确定性 Best Fit Decreasing 规则引擎。
 * 只做透明、可解释、可单测的硬约束配载，不依赖任何原生库。
 */
@Component
public class RuleLoadPlanEngine implements LoadPlanEngine {

    private final OptimizerProperties properties;
    private final LoadPlanResponseAssembler assembler;

    public RuleLoadPlanEngine(OptimizerProperties properties, LoadPlanResponseAssembler assembler) {
        this.properties = properties;
        this.assembler = assembler;
    }

    @Override
    public EngineName engine() {
        return EngineName.RULE;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public LoadPlanSolveResponse solve(SolveLoadPlanRequest request, PreparedLoadProblem problem, long startNanos) {
        List<ConstraintViolationDto> violations = new ArrayList<>(problem.violations());
        List<BinAssignment> bins = new ArrayList<>();
        List<PreparedLoadOrder> sortedOrders = problem.orders().stream()
                .sorted(Comparator
                        .comparingInt(PreparedLoadOrder::priority).reversed()
                        .thenComparing(Comparator.comparingDouble(PreparedLoadOrder::weightKg).reversed())
                        .thenComparing(Comparator.comparingDouble(PreparedLoadOrder::volumeM3).reversed())
                        .thenComparing(PreparedLoadOrder::orderId))
                .toList();

        for (PreparedLoadOrder order : sortedOrders) {
            BinAssignment best = null;
            double bestRemaining = Double.MAX_VALUE;
            for (BinAssignment bin : bins) {
                if (bin.canFit(order)) {
                    double remainingScore = bin.remainingWeightKg() + bin.remainingVolumeM3() * 1000d;
                    if (remainingScore < bestRemaining) {
                        bestRemaining = remainingScore;
                        best = bin;
                    }
                }
            }

            if (best == null) {
                PreparedVehicle vehicle = chooseNewVehicle(problem.vehicles(), bins, order);
                if (vehicle == null) {
                    violations.add(ConstraintViolationDto.hard(
                            "NO_VEHICLE_CAN_FIT_ORDER",
                            "没有可启用且能容纳该订单的车辆，可能受车辆数量、载重或体积限制",
                            order.orderId(),
                            null,
                            "weight=" + order.weightKg() + "kg,volume=" + order.volumeM3() + "m3",
                            null));
                    continue;
                }
                best = new BinAssignment(vehicle);
                bins.add(best);
                best.add(order);
            } else {
                best.add(order);
            }
        }

        List<PreparedLoadOrder> assignedOrders = bins.stream()
                .flatMap(bin -> bin.orders().stream())
                .toList();
        if (assignedOrders.size() != problem.orders().size()) {
            return assembler.infeasible(request,
                    request.engine() == null ? EngineName.RULE : request.engine(),
                    EngineName.RULE,
                    bins,
                    violations,
                    List.of("规则引擎已按确定性容量硬约束尝试配载，未分配订单必须转人工处理"),
                    startNanos,
                    properties);
        }

        if (!violations.isEmpty()) {
            return assembler.infeasible(request,
                    request.engine() == null ? EngineName.RULE : request.engine(),
                    EngineName.RULE,
                    bins,
                    violations,
                    List.of("规则引擎检测到容量硬约束风险"),
                    startNanos,
                    properties);
        }

        return assembler.feasible(request,
                request.engine() == null ? EngineName.RULE : request.engine(),
                EngineName.RULE,
                bins,
                violations,
                List.of("规则引擎结果可作为 OR-Tools 故障时的兜底方案"),
                startNanos,
                properties);
    }

    private PreparedVehicle chooseNewVehicle(List<PreparedVehicle> vehicles,
                                             List<BinAssignment> usedBins,
                                             PreparedLoadOrder order) {
        List<String> usedVehicleIds = usedBins.stream()
                .map(bin -> bin.vehicle().vehicleId())
                .toList();
        return vehicles.stream()
                .filter(PreparedVehicle::eligible)
                .filter(vehicle -> !usedVehicleIds.contains(vehicle.vehicleId()))
                .filter(vehicle -> vehicle.effectiveWeightKg() >= order.weightKg())
                .filter(vehicle -> vehicle.effectiveVolumeM3() >= order.volumeM3())
                .min(Comparator
                        .comparingDouble(PreparedVehicle::effectiveWeightKg)
                        .thenComparingDouble(PreparedVehicle::effectiveVolumeM3)
                        .thenComparingDouble(PreparedVehicle::fixedCost)
                        .thenComparing(vehicle -> vehicle.source().sortNo() == null
                                ? Integer.MAX_VALUE
                                : vehicle.source().sortNo())
                        .thenComparing(PreparedVehicle::vehicleId))
                .orElse(null);
    }
}
