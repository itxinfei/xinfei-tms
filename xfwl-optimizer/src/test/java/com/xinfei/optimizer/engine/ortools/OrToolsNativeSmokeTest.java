package com.xinfei.optimizer.engine.ortools;

import com.xinfei.optimizer.OptimizerTestFixtures;
import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.engine.LoadPlanPreparator;
import com.xinfei.optimizer.engine.LoadPlanResponseAssembler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 原生库 smoke test 默认跳过；本地/镜像验证时显式加 -Dxfwl.test.ortools.native=true。
 */
class OrToolsNativeSmokeTest {

    @Test
    void shouldSolveWithOrToolsNativeBackendWhenExplicitlyEnabled() {
        Assumptions.assumeTrue(Boolean.getBoolean("xfwl.test.ortools.native"),
                "OR-Tools 原生库 smoke test 默认跳过");

        OptimizerProperties properties = new OptimizerProperties(
                EngineName.RULE,
                1_000,
                5_000,
                500,
                80,
                0.98d,
                0.85d,
                90,
                false,
                new OptimizerProperties.Engines(
                        new OptimizerProperties.OrTools(true, "9.12.4544", true),
                        new OptimizerProperties.Timefold(false, "not-bundled")
                )
        );
        OrToolsRuntimeProbe probe = new OrToolsRuntimeProbe(properties);
        OrToolsLoadPlanEngine engine = new OrToolsLoadPlanEngine(
                properties, probe, new LoadPlanResponseAssembler());

        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(
                        OptimizerTestFixtures.order("O1", 10, 100d, 1d),
                        OptimizerTestFixtures.order("O2", 10, 200d, 2d)
                ),
                List.of(OptimizerTestFixtures.vehicle("V1", 500d, 5d)),
                EngineName.OR_TOOLS,
                false
        );

        LoadPlanSolveResponse response = engine.solve(
                request, new LoadPlanPreparator(properties).prepare(request), System.nanoTime());

        assertThat(probe.probe().available()).isTrue();
        assertThat(response.feasible()).isTrue();
        assertThat(response.engineUsed()).isEqualTo(EngineName.OR_TOOLS);
        assertThat(response.usedVehicleCount()).isEqualTo(1);
        assertThat(response.bins().getFirst().orderIds()).containsExactlyInAnyOrder("O1", "O2");
    }
}
