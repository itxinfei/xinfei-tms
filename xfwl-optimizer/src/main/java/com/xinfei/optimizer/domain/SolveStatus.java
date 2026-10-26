package com.xinfei.optimizer.domain;

/**
 * 求解结果状态。业务数据无可行解不是 HTTP 异常，返回 HTTP 200 + INFEASIBLE 和不可行原因。
 */
public enum SolveStatus {
    FEASIBLE,
    INFEASIBLE,
    INVALID,
    UNSUPPORTED,
    FAILED
}
