package com.xinfei.optimizer.engine.rule;

import com.xinfei.optimizer.OptimizerTestFixtures;
import com.xinfei.optimizer.api.dto.LoadOrderInput;
import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.LoadVehicleInput;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.engine.LoadPlanPreparator;
import com.xinfei.optimizer.engine.LoadPlanResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleLoadPlanEngineTest {

    private OptimizerProperties properties;
    private RuleLoadPlanEngine engine;

    @BeforeEach
    void setUp() {
        properties = OptimizerTestFixtures.properties();
        LoadPlanPreparator preparator = new LoadPlanPreparator(properties);
        engine = new RuleLoadPlanEngine(properties, new LoadPlanResponseAssembler());
    }

    @Test
    void shouldPackAllOrdersIntoOneVehicleWhenCapacityAllows() {
        List<LoadOrderInput> orders = List.of(
                OptimizerTestFixtures.order("O1", 10, 100d, 1d),
                OptimizerTestFixtures.order("O2", 20, 200d, 2d)
        );
        List<LoadVehicleInput> vehicles = List.of(OptimizerTestFixtures.vehicle("V1", 500d, 5d));
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(orders, vehicles, EngineName.RULE, true);

        LoadPlanSolveResponse response = engine.solve(
                request, new LoadPlanPreparator(properties).prepare(request), System.nanoTime());

        assertThat(response.feasible()).isTrue();
        assertThat(response.usedVehicleCount()).isEqualTo(1);
        assertThat(response.bins().getFirst().orderIds()).containsExactly("O2", "O1");
        assertThat(response.totalPieceCount()).isEqualTo(30);
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void shouldSplitAcrossMultipleVehiclesWhenOneVehicleCannotHoldAll() {
        List<LoadOrderInput> orders = List.of(
                OptimizerTestFixtures.order("O1", 10, 300d, 3d),
                OptimizerTestFixtures.order("O2", 10, 290d, 2.9d)
        );
        List<LoadVehicleInput> vehicles = List.of(
                OptimizerTestFixtures.vehicle("V1", 310d, 4d),
                OptimizerTestFixtures.vehicle("V2", 310d, 4d)
        );
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(orders, vehicles, EngineName.RULE, true);

        LoadPlanSolveResponse response = engine.solve(
                request, new LoadPlanPreparator(properties).prepare(request), System.nanoTime());

        assertThat(response.feasible()).isTrue();
        assertThat(response.usedVehicleCount()).isEqualTo(2);
        assertThat(response.bins()).extracting(bin -> bin.orderIds().size()).containsExactly(1, 1);
    }

    @Test
    void shouldReturnInfeasibleWhenSingleOrderExceedsAllVehicles() {
        List<LoadOrderInput> orders = List.of(OptimizerTestFixtures.order("O1", 10, 400d, 1d));
        List<LoadVehicleInput> vehicles = List.of(OptimizerTestFixtures.vehicle("V1", 300d, 3d));
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(orders, vehicles, EngineName.RULE, true);

        LoadPlanSolveResponse response = engine.solve(
                request, new LoadPlanPreparator(properties).prepare(request), System.nanoTime());

        assertThat(response.feasible()).isFalse();
        assertThat(response.violations()).isNotEmpty();
        assertThat(response.violations().getFirst().code()).isEqualTo("ORDER_EXCEEDS_ALL_VEHICLE_WEIGHT");
    }

    @Test
    void shouldReturnInfeasibleWhenTotalWeightCapacityIsInsufficient() {
        List<LoadOrderInput> orders = List.of(
                OptimizerTestFixtures.order("O1", 10, 300d, 1d),
                OptimizerTestFixtures.order("O2", 10, 300d, 1d)
        );
        List<LoadVehicleInput> vehicles = List.of(
                OptimizerTestFixtures.vehicle("V1", 300d, 3d),
                OptimizerTestFixtures.vehicle("V2", 300d, 3d)
        );
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(orders, vehicles, EngineName.RULE, true);

        LoadPlanSolveResponse response = engine.solve(
                request, new LoadPlanPreparator(properties).prepare(request), System.nanoTime());

        assertThat(response.feasible()).isFalse();
        assertThat(response.violations()).anyMatch(violation ->
                violation.code().equals("INSUFFICIENT_TOTAL_WEIGHT_CAPACITY")
                        || violation.code().equals("NO_VEHICLE_CAN_FIT_ORDER"));
    }

    @Test
    void shouldExcludeUnavailableGpsOfflineAndLicenseInvalidVehicles() {
        List<LoadOrderInput> orders = List.of(OptimizerTestFixtures.order("O1", 10, 100d, 1d));
        List<LoadVehicleInput> vehicles = List.of(
                OptimizerTestFixtures.unavailableVehicle("V1", "AVAILABLE"),
                OptimizerTestFixtures.unavailableVehicle("V2", "GPS"),
                OptimizerTestFixtures.unavailableVehicle("V3", "LICENSE")
        );
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(orders, vehicles, EngineName.RULE, true);

        LoadPlanSolveResponse response = engine.solve(
                request, new LoadPlanPreparator(properties).prepare(request), System.nanoTime());

        assertThat(response.feasible()).isFalse();
        assertThat(response.violations()).anyMatch(violation -> violation.code().equals("NO_ELIGIBLE_VEHICLE"));
    }
}
