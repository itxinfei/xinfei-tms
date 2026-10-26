package com.xinfei.optimizer;

import com.xinfei.optimizer.api.dto.LoadOrderInput;
import com.xinfei.optimizer.api.dto.LoadVehicleInput;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;

import java.util.List;

public final class OptimizerTestFixtures {

    private OptimizerTestFixtures() {
    }

    public static OptimizerProperties properties() {
        return new OptimizerProperties(
                EngineName.RULE,
                5_000,
                30_000,
                500,
                80,
                0.98d,
                0.85d,
                90,
                true,
                null
        );
    }

    public static LoadOrderInput order(String id, int pieces, double weightKg, double volumeM3) {
        return new LoadOrderInput(id, id + "-transport", "LINE-001", "AGENCY-END",
                pieces, weightKg, volumeM3, 0, true);
    }

    public static LoadVehicleInput vehicle(String id, double weightKg, double volumeM3) {
        return new LoadVehicleInput(id, "TRIPS-001", "DRIVER-001", "TRUCK-4M2",
                true, true, true, weightKg, volumeM3, 100d, 1);
    }

    public static LoadVehicleInput unavailableVehicle(String id, String reason) {
        LoadVehicleInput base = vehicle(id, 1_000d, 10d);
        return switch (reason) {
            case "AVAILABLE" -> new LoadVehicleInput(base.vehicleId(), base.tripsId(), base.driverId(),
                    base.vehicleTypeId(), false, base.gpsOnline(), base.licenseValid(),
                    base.allowableLoadKg(), base.allowableVolumeM3(), base.fixedCost(), base.sortNo());
            case "GPS" -> new LoadVehicleInput(base.vehicleId(), base.tripsId(), base.driverId(),
                    base.vehicleTypeId(), base.available(), false, base.licenseValid(),
                    base.allowableLoadKg(), base.allowableVolumeM3(), base.fixedCost(), base.sortNo());
            default -> new LoadVehicleInput(base.vehicleId(), base.tripsId(), base.driverId(),
                    base.vehicleTypeId(), base.available(), base.gpsOnline(), false,
                    base.allowableLoadKg(), base.allowableVolumeM3(), base.fixedCost(), base.sortNo());
        };
    }

    public static SolveLoadPlanRequest request(List<LoadOrderInput> orders,
                                               List<LoadVehicleInput> vehicles,
                                               EngineName engine,
                                               Boolean fallbackAllowed) {
        return new SolveLoadPlanRequest(
                "tenant-001",
                "LOAD_PLAN_PREVIEW",
                "hash-" + (orders.size() + "-" + vehicles.size()),
                orders,
                vehicles,
                engine,
                null,
                fallbackAllowed,
                false
        );
    }
}
