package com.xinfei.optimizer.engine;

import com.xinfei.optimizer.api.dto.ConstraintViolationDto;
import com.xinfei.optimizer.api.dto.LoadOrderInput;
import com.xinfei.optimizer.api.dto.LoadVehicleInput;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.OptimizerRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 配载请求归一化与基础硬约束诊断。
 */
@Component
public class LoadPlanPreparator {

    private final OptimizerProperties properties;

    public LoadPlanPreparator(OptimizerProperties properties) {
        this.properties = properties;
    }

    public PreparedLoadProblem prepare(SolveLoadPlanRequest request) {
        if (request.orders().size() > properties.maxOrderCount()) {
            throw new OptimizerRequestException("订单数量超过求解上限: " + properties.maxOrderCount());
        }
        if (request.vehicles().size() > properties.maxVehicleCount()) {
            throw new OptimizerRequestException("车辆数量超过求解上限: " + properties.maxVehicleCount());
        }
        if (request.timeLimitMs() != null && request.timeLimitMs() <= 0) {
            throw new OptimizerRequestException("timeLimitMs 必须大于 0");
        }

        List<PreparedLoadOrder> orders = prepareOrders(request.orders());
        List<PreparedVehicle> vehicles = prepareVehicles(request.vehicles());
        List<ConstraintViolationDto> violations = new ArrayList<>();

        double totalWeight = orders.stream().mapToDouble(PreparedLoadOrder::weightKg).sum();
        double totalVolume = orders.stream().mapToDouble(PreparedLoadOrder::volumeM3).sum();
        int totalPieces = orders.stream().mapToInt(PreparedLoadOrder::pieceCount).sum();

        List<PreparedVehicle> eligible = vehicles.stream().filter(PreparedVehicle::eligible).toList();
        if (eligible.isEmpty()) {
            violations.add(ConstraintViolationDto.hard(
                    "NO_ELIGIBLE_VEHICLE",
                    "没有可用车辆：车辆必须 available=true、gpsOnline=true、licenseValid=true"));
        } else {
            double maxWeight = eligible.stream().mapToDouble(PreparedVehicle::effectiveWeightKg).max().orElse(0d);
            double maxVolume = eligible.stream().mapToDouble(PreparedVehicle::effectiveVolumeM3).max().orElse(0d);
            for (PreparedLoadOrder order : orders) {
                if (order.weightKg() > maxWeight + 1e-9d) {
                    violations.add(ConstraintViolationDto.hard(
                            "ORDER_EXCEEDS_ALL_VEHICLE_WEIGHT",
                            "单票重量超过所有候选车的安全载重",
                            order.orderId(),
                            null,
                            format(order.weightKg()) + "kg",
                            format(maxWeight) + "kg"));
                }
                if (order.volumeM3() > maxVolume + 1e-9d) {
                    violations.add(ConstraintViolationDto.hard(
                            "ORDER_EXCEEDS_ALL_VEHICLE_VOLUME",
                            "单票体积超过所有候选车的可用体积",
                            order.orderId(),
                            null,
                            format(order.volumeM3()) + "m3",
                            format(maxVolume) + "m3"));
                }
            }

            double eligibleWeight = eligible.stream().mapToDouble(PreparedVehicle::effectiveWeightKg).sum();
            double eligibleVolume = eligible.stream().mapToDouble(PreparedVehicle::effectiveVolumeM3).sum();
            if (totalWeight > eligibleWeight + 1e-9d) {
                violations.add(ConstraintViolationDto.hard(
                        "INSUFFICIENT_TOTAL_WEIGHT_CAPACITY",
                        "候选车总安全载重不足",
                        null,
                        null,
                        format(totalWeight) + "kg",
                        format(eligibleWeight) + "kg"));
            }
            if (totalVolume > eligibleVolume + 1e-9d) {
                violations.add(ConstraintViolationDto.hard(
                        "INSUFFICIENT_TOTAL_VOLUME_CAPACITY",
                        "候选车总可用体积不足",
                        null,
                        null,
                        format(totalVolume) + "m3",
                        format(eligibleVolume) + "m3"));
            }
        }

        return new PreparedLoadProblem(orders, vehicles, violations, totalWeight, totalVolume, totalPieces);
    }

    private List<PreparedLoadOrder> prepareOrders(List<LoadOrderInput> source) {
        Set<String> ids = new HashSet<>();
        List<PreparedLoadOrder> result = new ArrayList<>();
        for (LoadOrderInput order : source) {
            if (!ids.add(order.orderId())) {
                throw new OptimizerRequestException("订单 ID 重复: " + order.orderId());
            }
            int priority = order.priority() == null ? 0 : order.priority();
            result.add(new PreparedLoadOrder(order, priority));
        }
        return result;
    }

    private List<PreparedVehicle> prepareVehicles(List<LoadVehicleInput> source) {
        Set<String> ids = new HashSet<>();
        List<PreparedVehicle> result = new ArrayList<>();
        for (LoadVehicleInput vehicle : source) {
            if (!ids.add(vehicle.vehicleId())) {
                throw new OptimizerRequestException("车辆 ID 重复: " + vehicle.vehicleId());
            }
            boolean available = !Boolean.FALSE.equals(vehicle.available());
            boolean gpsOnline = !Boolean.FALSE.equals(vehicle.gpsOnline());
            boolean licenseValid = !Boolean.FALSE.equals(vehicle.licenseValid());
            boolean eligible = available && gpsOnline && licenseValid;
            String reason = null;
            if (!eligible) {
                if (!available) {
                    reason = "VEHICLE_NOT_AVAILABLE";
                } else if (!gpsOnline) {
                    reason = "VEHICLE_GPS_OFFLINE";
                } else {
                    reason = "VEHICLE_LICENSE_INVALID";
                }
            }
            double effectiveWeight = round(vehicle.allowableLoadKg() * properties.weightSafetyFactor());
            double effectiveVolume = round(vehicle.allowableVolumeM3() * properties.volumeFillFactor());
            result.add(new PreparedVehicle(vehicle, eligible, reason, effectiveWeight, effectiveVolume));
        }
        return result;
    }

    public static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    public static double format(double value) {
        return round(value);
    }
}
