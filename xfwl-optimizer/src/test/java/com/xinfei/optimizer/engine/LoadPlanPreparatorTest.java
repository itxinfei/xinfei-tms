package com.xinfei.optimizer.engine;

import com.xinfei.optimizer.OptimizerTestFixtures;
import com.xinfei.optimizer.api.dto.LoadOrderInput;
import com.xinfei.optimizer.api.dto.LoadVehicleInput;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.domain.OptimizerRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadPlanPreparatorTest {

    private final OptimizerProperties properties = OptimizerTestFixtures.properties();
    private final LoadPlanPreparator preparator = new LoadPlanPreparator(properties);

    @Test
    void shouldApplyWeightSafetyFactorAndVolumeFillFactor() {
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(OptimizerTestFixtures.order("O1", 1, 1d, 1d)),
                List.of(OptimizerTestFixtures.vehicle("V1", 100d, 10d)),
                EngineName.RULE,
                true
        );

        PreparedLoadProblem problem = preparator.prepare(request);

        PreparedVehicle vehicle = problem.vehicles().getFirst();
        assertThat(vehicle.effectiveWeightKg()).isEqualTo(98d);
        assertThat(vehicle.effectiveVolumeM3()).isEqualTo(8.5d);
    }

    @Test
    void shouldRejectDuplicateOrderIds() {
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(
                        OptimizerTestFixtures.order("O1", 1, 1d, 1d),
                        OptimizerTestFixtures.order("O1", 1, 1d, 1d)
                ),
                List.of(OptimizerTestFixtures.vehicle("V1", 100d, 10d)),
                EngineName.RULE,
                true
        );

        assertThatThrownBy(() -> preparator.prepare(request))
                .isInstanceOf(OptimizerRequestException.class)
                .hasMessageContaining("订单 ID 重复");
    }

    @Test
    void shouldRejectDuplicateVehicleIds() {
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(OptimizerTestFixtures.order("O1", 1, 1d, 1d)),
                List.of(
                        OptimizerTestFixtures.vehicle("V1", 100d, 10d),
                        OptimizerTestFixtures.vehicle("V1", 100d, 10d)
                ),
                EngineName.RULE,
                true
        );

        assertThatThrownBy(() -> preparator.prepare(request))
                .isInstanceOf(OptimizerRequestException.class)
                .hasMessageContaining("车辆 ID 重复");
    }

    @Test
    void shouldRejectOrdersBeyondConfiguredLimit() {
        OptimizerProperties smallLimitProperties = new OptimizerProperties(
                EngineName.RULE, 1_000, 1_000, 1, 10,
                0.98d, 0.85d, 90, true, null
        );
        LoadPlanPreparator smallLimitPreparator = new LoadPlanPreparator(smallLimitProperties);
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(
                        OptimizerTestFixtures.order("O1", 1, 1d, 1d),
                        OptimizerTestFixtures.order("O2", 1, 1d, 1d)
                ),
                List.of(OptimizerTestFixtures.vehicle("V1", 100d, 10d)),
                EngineName.RULE,
                true
        );

        assertThatThrownBy(() -> smallLimitPreparator.prepare(request))
                .isInstanceOf(OptimizerRequestException.class)
                .hasMessageContaining("订单数量超过求解上限");
    }
}
