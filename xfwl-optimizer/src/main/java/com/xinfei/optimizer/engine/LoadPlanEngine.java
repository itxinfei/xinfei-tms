package com.xinfei.optimizer.engine;

import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.domain.EngineName;

/**
 * 配载求解引擎。实现必须无状态、不写业务库、不访问公网。
 */
public interface LoadPlanEngine {

    EngineName engine();

    boolean enabled();

    boolean available();

    LoadPlanSolveResponse solve(SolveLoadPlanRequest request, PreparedLoadProblem problem, long startNanos);
}
