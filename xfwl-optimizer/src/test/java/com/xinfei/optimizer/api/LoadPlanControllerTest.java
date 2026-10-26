package com.xinfei.optimizer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinfei.optimizer.OptimizerTestFixtures;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.application.LoadPlanApplicationService;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.engine.LoadPlanPreparator;
import com.xinfei.optimizer.engine.LoadPlanResponseAssembler;
import com.xinfei.optimizer.engine.ortools.OrToolsLoadPlanEngine;
import com.xinfei.optimizer.engine.ortools.OrToolsRuntimeProbe;
import com.xinfei.optimizer.engine.rule.RuleLoadPlanEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoadPlanControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        OptimizerProperties properties = OptimizerTestFixtures.properties();
        LoadPlanResponseAssembler assembler = new LoadPlanResponseAssembler();
        LoadPlanPreparator preparator = new LoadPlanPreparator(properties);
        RuleLoadPlanEngine ruleEngine = new RuleLoadPlanEngine(properties, assembler);
        OrToolsRuntimeProbe probe = new OrToolsRuntimeProbe(properties);
        OrToolsLoadPlanEngine orToolsEngine = new OrToolsLoadPlanEngine(properties, probe, assembler);
        LoadPlanApplicationService service =
                new LoadPlanApplicationService(properties, preparator, ruleEngine, orToolsEngine, probe);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EngineController(service),
                        new LoadPlanController(service))
                .setControllerAdvice(new OptimizerExceptionHandler())
                .build();
    }

    @Test
    void shouldSolveLoadPlanViaHttp() throws Exception {
        SolveLoadPlanRequest request = OptimizerTestFixtures.request(
                List.of(OptimizerTestFixtures.order("O1", 10, 100d, 1d)),
                List.of(OptimizerTestFixtures.vehicle("V1", 500d, 5d)),
                EngineName.RULE,
                true
        );

        mockMvc.perform(post("/api/v1/optimizer/load-plans/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.engineUsed").value("RULE"))
                .andExpect(jsonPath("$.usedVehicleCount").value(1))
                .andExpect(jsonPath("$.bins[0].orderIds[0]").value("O1"));
    }

    @Test
    void shouldReturn400WhenOrdersEmpty() throws Exception {
        String invalidRequest = """
                {
                  "tenantId": "tenant-001",
                  "orders": [],
                  "vehicles": []
                }
                """;

        mockMvc.perform(post("/api/v1/optimizer/load-plans/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldListEnginesViaHttp() throws Exception {
        mockMvc.perform(get("/api/v1/optimizer/engines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultEngine").value("RULE"))
                .andExpect(jsonPath("$.engines.length()").value(3));
    }

    @Test
    void shouldReturn501ForRouteSolve() throws Exception {
        mockMvc.perform(post("/api/v1/optimizer/routes/solve"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }
}
