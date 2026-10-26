package com.xinfei.optimizer.engine.ortools;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OR-Tools MIP/SCIP 容量配载引擎。
 * 模型：x[o,v] 表示订单是否入车，y[v] 表示车辆是否启用，最小化启用车辆数和固定成本。
 */
@Component
public class OrToolsLoadPlanEngine implements LoadPlanEngine {

    private static final int WEIGHT_SCALE = 1_000;       // kg -> g
    private static final int VOLUME_SCALE = 1_000_000;  // m3 -> cm3

    private final OptimizerProperties properties;
    private final OrToolsRuntimeProbe runtimeProbe;
    private final LoadPlanResponseAssembler assembler;

    public OrToolsLoadPlanEngine(OptimizerProperties properties,
                                 OrToolsRuntimeProbe runtimeProbe,
                                 LoadPlanResponseAssembler assembler) {
        this.properties = properties;
        this.runtimeProbe = runtimeProbe;
        this.assembler = assembler;
    }

    @Override
    public EngineName engine() {
        return EngineName.OR_TOOLS;
    }

    @Override
    public boolean enabled() {
        return runtimeProbe.isEnabled();
    }

    @Override
    public boolean available() {
        return runtimeProbe.isEnabled() && runtimeProbe.probe().available();
    }

    @Override
    public LoadPlanSolveResponse solve(SolveLoadPlanRequest request, PreparedLoadProblem problem, long startNanos) {
        OrToolsRuntimeProbe.ProbeResult probe = runtimeProbe.probe();
        if (!probe.available()) {
            throw new OrToolsSolveException("OR-Tools 不可用: " + probe.status() + " - " + probe.message());
        }

        List<PreparedVehicle> eligibleVehicles = problem.vehicles().stream()
                .filter(PreparedVehicle::eligible)
                .toList();
        if (eligibleVehicles.isEmpty()) {
            return assembler.infeasible(request, EngineName.OR_TOOLS, EngineName.OR_TOOLS,
                    List.of(), problem.violations(), List.of(), startNanos, properties);
        }

        MPSolver solver = null;
        try {
            Loader.loadNativeLibraries();
            solver = MPSolver.createSolver("SCIP");
            if (solver == null) {
                throw new OrToolsSolveException("OR-Tools SCIP backend 创建失败");
            }
            int timeLimitMs = request.timeLimitMs() != null
                    ? request.timeLimitMs()
                    : properties.previewTimeLimitMs();
            solver.setTimeLimit(timeLimitMs);

            int orderCount = problem.orders().size();
            int vehicleCount = eligibleVehicles.size();
            MPVariable[][] assignment = new MPVariable[orderCount][vehicleCount];
            MPVariable[] vehicleUsed = new MPVariable[vehicleCount];

            for (int vehicleIndex = 0; vehicleIndex < vehicleCount; vehicleIndex++) {
                vehicleUsed[vehicleIndex] = solver.makeIntVar(0, 1, "vehicle_used_" + vehicleIndex);
            }
            for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
                for (int vehicleIndex = 0; vehicleIndex < vehicleCount; vehicleIndex++) {
                    assignment[orderIndex][vehicleIndex] = solver.makeIntVar(0, 1,
                            "order_" + orderIndex + "_vehicle_" + vehicleIndex);
                }
            }

            for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
                MPConstraint exactlyOneVehicle = solver.makeConstraint(1, 1, "one_vehicle_order_" + orderIndex);
                for (int vehicleIndex = 0; vehicleIndex < vehicleCount; vehicleIndex++) {
                    exactlyOneVehicle.setCoefficient(assignment[orderIndex][vehicleIndex], 1);
                }
            }

            for (int vehicleIndex = 0; vehicleIndex < vehicleCount; vehicleIndex++) {
                PreparedVehicle vehicle = eligibleVehicles.get(vehicleIndex);
                MPConstraint weightCapacity = solver.makeConstraint(0, Double.POSITIVE_INFINITY,
                        "weight_capacity_" + vehicleIndex);
                MPConstraint volumeCapacity = solver.makeConstraint(0, Double.POSITIVE_INFINITY,
                        "volume_capacity_" + vehicleIndex);
                weightCapacity.setCoefficient(vehicleUsed[vehicleIndex],
                        vehicle.effectiveWeightKg() * WEIGHT_SCALE);
                volumeCapacity.setCoefficient(vehicleUsed[vehicleIndex],
                        vehicle.effectiveVolumeM3() * VOLUME_SCALE);
                for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
                    PreparedLoadOrder order = problem.orders().get(orderIndex);
                    weightCapacity.setCoefficient(assignment[orderIndex][vehicleIndex],
                            -order.weightKg() * WEIGHT_SCALE);
                    volumeCapacity.setCoefficient(assignment[orderIndex][vehicleIndex],
                            -order.volumeM3() * VOLUME_SCALE);
                }
            }

            MPObjective objective = solver.objective();
            for (int vehicleIndex = 0; vehicleIndex < vehicleCount; vehicleIndex++) {
                PreparedVehicle vehicle = eligibleVehicles.get(vehicleIndex);
                double coefficient = 100_000d + vehicle.fixedCost() * 100d;
                objective.setCoefficient(vehicleUsed[vehicleIndex], coefficient);
            }
            objective.setMinimization();

            MPSolver.ResultStatus resultStatus = solver.solve();
            if (resultStatus != MPSolver.ResultStatus.OPTIMAL
                    && resultStatus != MPSolver.ResultStatus.FEASIBLE) {
                List<ConstraintViolationDto> violations = new ArrayList<>(problem.violations());
                violations.add(ConstraintViolationDto.hard(
                        "ORTOOLS_NO_FEASIBLE_SOLUTION",
                        "OR-Tools 未找到满足硬约束的方案，status=" + resultStatus));
                return assembler.infeasible(request, EngineName.OR_TOOLS, EngineName.OR_TOOLS,
                        List.of(), violations, List.of(), startNanos, properties);
            }

            Map<String, BinAssignment> assignmentByVehicleId = new HashMap<>();
            List<BinAssignment> bins = new ArrayList<>();
            for (int vehicleIndex = 0; vehicleIndex < vehicleCount; vehicleIndex++) {
                if (vehicleUsed[vehicleIndex].solutionValue() >= 0.5d) {
                    BinAssignment bin = new BinAssignment(eligibleVehicles.get(vehicleIndex));
                    bins.add(bin);
                    assignmentByVehicleId.put(eligibleVehicles.get(vehicleIndex).vehicleId(), bin);
                }
            }

            for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
                PreparedLoadOrder order = problem.orders().get(orderIndex);
                boolean assigned = false;
                for (int vehicleIndex = 0; vehicleIndex < vehicleCount; vehicleIndex++) {
                    if (assignment[orderIndex][vehicleIndex].solutionValue() >= 0.5d) {
                        BinAssignment bin = assignmentByVehicleId.get(eligibleVehicles.get(vehicleIndex).vehicleId());
                        if (bin == null) {
                            throw new OrToolsSolveException("OR-Tools 返回订单分配到了未启用车辆");
                        }
                        bin.add(order);
                        assigned = true;
                    }
                }
                if (!assigned) {
                    throw new OrToolsSolveException("OR-Tools 返回结果中订单未分配车辆: " + order.orderId());
                }
            }

            bins.sort((left, right) -> Integer.compare(
                    eligibleVehicles.indexOf(left.vehicle()),
                    eligibleVehicles.indexOf(right.vehicle())));
            return assembler.feasible(request, EngineName.OR_TOOLS, EngineName.OR_TOOLS,
                    bins, List.of(),
                    List.of("OR-Tools status=" + resultStatus + ", wallTime=" + solver.wallTime() + "ms"),
                    startNanos, properties);
        } catch (OrToolsSolveException e) {
            throw e;
        } catch (Throwable t) {
            throw new OrToolsSolveException("OR-Tools 求解失败: " + t.getClass().getSimpleName(), t);
        } finally {
            if (solver != null) {
                solver.delete();
            }
        }
    }
}
