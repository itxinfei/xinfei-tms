package com.xinfei.optimizer.domain;

/**
 * 当前阶段未开放的求解场景，对应 HTTP 501。
 */
public class OptimizerUnsupportedException extends RuntimeException {
    public OptimizerUnsupportedException(String message) {
        super(message);
    }
}
