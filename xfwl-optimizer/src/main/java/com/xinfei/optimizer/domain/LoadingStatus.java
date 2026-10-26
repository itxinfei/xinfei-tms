package com.xinfei.optimizer.domain;

/**
 * 单车装载状态，口径与《77》保持一致：EMPTY 空载、PARTIAL 半载、FULL 达到满载阈值。
 */
public enum LoadingStatus {
    EMPTY,
    PARTIAL,
    FULL
}
