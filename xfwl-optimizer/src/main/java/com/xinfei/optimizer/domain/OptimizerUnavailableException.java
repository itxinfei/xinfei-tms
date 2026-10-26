package com.xinfei.optimizer.domain;

/**
 * 显式要求的引擎不可用且调用方禁止降级，对应 HTTP 503。
 */
public class OptimizerUnavailableException extends RuntimeException {
    public OptimizerUnavailableException(String message) {
        super(message);
    }
}
