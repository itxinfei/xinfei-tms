package com.xinfei.optimizer.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 求解过程中的单车装载箱。
 */
public class BinAssignment {

    private final PreparedVehicle vehicle;
    private final List<PreparedLoadOrder> orders = new ArrayList<>();
    private double totalWeightKg;
    private double totalVolumeM3;
    private int totalPieceCount;

    public BinAssignment(PreparedVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public PreparedVehicle vehicle() {
        return vehicle;
    }

    public List<PreparedLoadOrder> orders() {
        return List.copyOf(orders);
    }

    public double totalWeightKg() {
        return totalWeightKg;
    }

    public double totalVolumeM3() {
        return totalVolumeM3;
    }

    public int totalPieceCount() {
        return totalPieceCount;
    }

    public boolean canFit(PreparedLoadOrder order) {
        return vehicle.eligible()
                && totalWeightKg + order.weightKg() <= vehicle.effectiveWeightKg() + 1e-9d
                && totalVolumeM3 + order.volumeM3() <= vehicle.effectiveVolumeM3() + 1e-9d;
    }

    public void add(PreparedLoadOrder order) {
        if (!canFit(order)) {
            throw new IllegalArgumentException("订单超过车辆剩余硬约束容量: " + order.orderId());
        }
        orders.add(order);
        totalWeightKg += order.weightKg();
        totalVolumeM3 += order.volumeM3();
        totalPieceCount += order.pieceCount();
    }

    public double remainingWeightKg() {
        return Math.max(0d, vehicle.effectiveWeightKg() - totalWeightKg);
    }

    public double remainingVolumeM3() {
        return Math.max(0d, vehicle.effectiveVolumeM3() - totalVolumeM3);
    }
}
