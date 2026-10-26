package com.xinfei.optimizer.application;

import com.xinfei.optimizer.OptimizerTestFixtures;
import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.domain.OptimizerUnavailableException;
import com.xinfei.optimizer.domain.OptimizerUnsupportedException;
import com.xinfei.optimizer.engine.LoadPlanPreparator;
import com.xinfei.optimizer.engine.LoadPlanResponseAssembler;
import com.xinfei.optimizer.engine.ortools.OrToolsLoadPlanEngine;
import com.xinfei.optimizer.engine.ortools.OrToolsRuntimeProbe;
import com.xinfei.optimizer.engine.rule.RuleLoadPlanEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadPlanApplicationServiceTest {

    private OptimizerProperties properties;
    private LoadPlanApplicationService service;

    @BeforeEach
    void setUp() {
        properties = OptimizerTestFixtures.properties();
        LoadPlanResponseAssembler assembler = new LoadPlanResponseAssembler();
        LoadPlanPreparator preparator = new LoadPlanPreparator(properties);
        RuleLoadPlanEngine ruleEngine = new RuleLoadPlanEngine(properties, assembler);
        OrToolsRuntimeProbe probe = new OrToolsRuntimeProbe(properties);
        OrToolsLoadPlanEngine orToolsEngine = new OrToolsLoadPlanEngine(properties, probe, assembler);
        service = new LoadPlanApplicationService(properties, preparator, ruleEngine, orToolsEngine, probe);
    }

    @Test
    void shouldFallbackToRuleWhenOrToolsDisabledAndFallbackAllowed() {
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(OptimizerTestFixtures.order("O1", 1, 10d, 1d)),
                List.of(OptimizerTestFixtures.vehicle("V1", 100d, 10d)),
                EngineName.OR_TOOLS,
                true
        );

        LoadPlanSolveResponse response = service.solveLoadPlan(request);

        assertThat(response.feasible()).isTrue();
        assertThat(response.requestedEngine()).isEqualTo(EngineName.OR_TOOLS);
        assertThat(response.engineUsed()).isEqualTo(EngineName.RULE);
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("OR_TOOLS_UNAVAILABLE_FALLBACK_RULE"));
    }

    @Test
    void shouldReturn503WhenOrToolsDisabledAndFallbackForbidden() {
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(OptimizerTestFixtures.order("O1", 1, 10d, 1d)),
                List.of(OptimizerTestFixtures.vehicle("V1", 100d, 10d)),
                EngineName.OR_TOOLS,
                false
        );

        assertThatThrownBy(() -> service.solveLoadPlan(request))
                .isInstanceOf(OptimizerUnavailableException.class)
                .hasMessageContaining("禁止降级");
    }

    @Test
    void shouldRejectTimefoldUntilP2() {
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(OptimizerTestFixtures.order("O1", 1, 10d, 1d)),
                List.of(OptimizerTestFixtures.vehicle("V1", 100d, 10d)),
                EngineName.TIMEFOLD,
                true
        );

        assertThatThrownBy(() -> service.solveLoadPlan(request))
                .isInstanceOf(OptimizerUnsupportedException.class)
                .hasMessageContaining("P2");
    }

    @Test
    void shouldExposeEngineStatus() {
        var engines = service.engines();

        assertThat(engines.defaultEngine()).isEqualTo(EngineName.RULE);
        assertThat(engines.engines()).hasSize(3);
        assertThat(engines.engines()).anyMatch(engine ->
                engine.engine() == EngineName.OR_TOOLS && !engine.enabled() && !engine.available());
    }
}
