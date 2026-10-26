package com.xinfei.optimizer.domain;

/**
 * 请求格式或字段不合法，对应 HTTP 400。
 */
public class OptimizerRequestException extends RuntimeException {
    public OptimizerRequestException(String message) {
        super(message);
    }
}
