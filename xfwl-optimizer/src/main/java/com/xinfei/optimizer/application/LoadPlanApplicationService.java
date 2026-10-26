package com.xinfei.optimizer.application;

import com.xinfei.optimizer.api.dto.EngineInfoDto;
import com.xinfei.optimizer.api.dto.EnginesResponse;
import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.config.OptimizerProperties;
import com.xinfei.optimizer.domain.EngineName;
import com.xinfei.optimizer.domain.OptimizerUnavailableException;
import com.xinfei.optimizer.domain.OptimizerUnsupportedException;
import com.xinfei.optimizer.engine.LoadPlanPreparator;
import com.xinfei.optimizer.engine.PreparedLoadProblem;
import com.xinfei.optimizer.engine.ortools.OrToolsLoadPlanEngine;
import com.xinfei.optimizer.engine.ortools.OrToolsRuntimeProbe;
import com.xinfei.optimizer.engine.rule.RuleLoadPlanEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 求解应用服务：选择引擎、执行容量配载、处理降级和规则对照。
 */
@Service
public class LoadPlanApplicationService {

    private final OptimizerProperties properties;
    private final LoadPlanPreparator preparator;
    private final RuleLoadPlanEngine ruleEngine;
    private final OrToolsLoadPlanEngine orToolsEngine;
    private final OrToolsRuntimeProbe orToolsProbe;

    public LoadPlanApplicationService(OptimizerProperties properties,
                                      LoadPlanPreparator preparator,
                                      RuleLoadPlanEngine ruleEngine,
                                      OrToolsLoadPlanEngine orToolsEngine,
                                      OrToolsRuntimeProbe orToolsProbe) {
        this.properties = properties;
        this.preparator = preparator;
        this.ruleEngine = ruleEngine;
        this.orToolsEngine = orToolsEngine;
        this.orToolsProbe = orToolsProbe;
    }

    public LoadPlanSolveResponse solveLoadPlan(SolveLoadPlanRequest request) {
        long startNanos = System.nanoTime();
        EngineName requested = request.engine() == null ? properties.defaultEngine() : request.engine();
        PreparedLoadProblem problem = preparator.prepare(request);

        if (requested == EngineName.TIMEFOLD) {
            throw new OptimizerUnsupportedException("Timefold 排班/波次能力属于 P2，当前服务尚未启用");
        }
        if (requested == EngineName.OR_TOOLS) {
            return solveWithOrTools(request, problem, startNanos, requested);
        }
        return ruleEngine.solve(request, problem, startNanos);
    }

    private LoadPlanSolveResponse solveWithOrTools(SolveLoadPlanRequest request,
                                                   PreparedLoadProblem problem,
                                                   long startNanos,
                                                   EngineName requested) {
        OrToolsRuntimeProbe.ProbeResult probe = orToolsProbe.probe();
        boolean fallbackAllowed = !Boolean.FALSE.equals(request.fallbackAllowed());
        if (!probe.available()) {
            if (fallbackAllowed) {
                LoadPlanSolveResponse response = ruleEngine.solve(request, problem, startNanos);
                return withFallbackWarning(response, "OR_TOOLS_UNAVAILABLE_FALLBACK_RULE",
                        "OR-Tools 不可用，已降级规则引擎: " + probe.status() + " - " + probe.message());
            }
            throw new OptimizerUnavailableException("OR-Tools 不可用且请求禁止降级: " + probe.message());
        }

        try {
            LoadPlanSolveResponse response = orToolsEngine.solve(request, problem, startNanos);
            if (Boolean.TRUE.equals(request.compareRule()) || Boolean.TRUE.equals(properties.compareRule())) {
                LoadPlanSolveResponse ruleResponse = ruleEngine.solve(request, problem, startNanos);
                List<String> warnings = new ArrayList<>(response.warnings());
                if (response.feasible() && ruleResponse.feasible()
                        && ruleResponse.usedVehicleCount() < response.usedVehicleCount()) {
                    warnings.add("RULE_COMPARE_USED_VEHICLES_LESS: rule="
                            + ruleResponse.usedVehicleCount() + ", ortools=" + response.usedVehicleCount());
                } else {
                    warnings.add("RULE_COMPARE_DONE: ruleUsedVehicles="
                            + ruleResponse.usedVehicleCount() + ", ortoolsUsedVehicles="
                            + response.usedVehicleCount());
                }
                return withWarnings(response, warnings);
            }
            return response;
        } catch (RuntimeException e) {
            if (!fallbackAllowed) {
                throw new OptimizerUnavailableException("OR-Tools 求解失败且请求禁止降级: " + e.getMessage());
            }
            LoadPlanSolveResponse response = ruleEngine.solve(request, problem, startNanos);
            return withFallbackWarning(response, "ORTOOLS_SOLVE_FAILED_FALLBACK_RULE",
                    "OR-Tools 求解失败，已降级规则引擎: " + e.getClass().getSimpleName());
        }
    }

    public EnginesResponse engines() {
        return new EnginesResponse(properties.defaultEngine(), List.of(
                new EngineInfoDto(
                        EngineName.RULE,
                        "确定性规则引擎",
                        "built-in",
                        "proprietary",
                        true,
                        true,
                        "AVAILABLE",
                        List.of("LOAD_PLAN", "BEST_FIT_DECREASING", "HARD_CAPACITY", "DETERMINISTIC_FALLBACK"),
                        "规则引擎内置可用",
                        null),
                orToolsInfo(),
                new EngineInfoDto(
                        EngineName.TIMEFOLD,
                        "Timefold Community 排班/波次引擎",
                        properties.engines().timefold().version(),
                        "Apache-2.0",
                        Boolean.TRUE.equals(properties.engines().timefold().enabled()),
                        false,
                        "NOT_BUNDLED",
                        List.of("SCHEDULE", "PLANNED_P2"),
                        "P2 阶段再评估并引入社区版，当前服务不打包 Timefold",
                        null)
        ));
    }

    private EngineInfoDto orToolsInfo() {
        OrToolsRuntimeProbe.ProbeResult probe = orToolsProbe.probe();
        return new EngineInfoDto(
                EngineName.OR_TOOLS,
                "Google OR-Tools MIP/SCIP",
                probe.version(),
                "Apache-2.0",
                orToolsProbe.isEnabled(),
                probe.available(),
                probe.status().name(),
                List.of("LOAD_PLAN", "BIN_PACKING", "MULTI_VEHICLE_SPLIT", "CVRP_PLANNED"),
                probe.message(),
                probe.checkedAt()
        );
    }

    private LoadPlanSolveResponse withFallbackWarning(LoadPlanSolveResponse response, String code, String message) {
        List<String> warnings = new ArrayList<>(response.warnings());
        warnings.add(code + ": " + message);
        return withWarnings(response, warnings);
    }

    private LoadPlanSolveResponse withWarnings(LoadPlanSolveResponse response, List<String> warnings) {
        return new LoadPlanSolveResponse(
                response.requestId(),
                response.requestHash(),
                response.tenantId(),
                response.sceneCode(),
                response.requestedEngine(),
                response.engineUsed(),
                response.status(),
                response.feasible(),
                response.resultCode(),
                response.message(),
                response.orderCount(),
                response.vehicleCount(),
                response.usedVehicleCount(),
                response.totalPieceCount(),
                response.totalWeightKg(),
                response.totalVolumeM3(),
                response.overallWeightUtilization(),
                response.overallVolumeUtilization(),
                response.solveDurationMs(),
                response.bins(),
                response.violations(),
                List.copyOf(warnings),
                response.score()
        );
    }
}
