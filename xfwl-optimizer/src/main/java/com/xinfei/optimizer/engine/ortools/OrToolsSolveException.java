package com.xinfei.optimizer.engine.ortools;

/**
 * OR-Tools 原生求解异常，由应用层决定是否降级规则引擎。
 */
public class OrToolsSolveException extends RuntimeException {
    public OrToolsSolveException(String message, Throwable cause) {
        super(message, cause);
    }

    public OrToolsSolveException(String message) {
        super(message);
    }
}
