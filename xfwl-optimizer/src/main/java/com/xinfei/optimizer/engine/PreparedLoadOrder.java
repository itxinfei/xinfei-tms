package com.xinfei.optimizer.engine;

import com.xinfei.optimizer.api.dto.LoadOrderInput;

/**
 * 归一化后的订单。单票订单默认原子分配，不允许在没有拆票授权时拆到多车。
 */
public record PreparedLoadOrder(
        LoadOrderInput source,
        int priority
) {
    public String orderId() {
        return source.orderId();
    }

    public int pieceCount() {
        return source.pieceCount();
    }

    public double weightKg() {
        return source.weightKg();
    }

    public double volumeM3() {
        return source.volumeM3();
    }
}
