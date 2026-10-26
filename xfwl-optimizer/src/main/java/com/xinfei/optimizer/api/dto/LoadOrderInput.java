package com.xinfei.optimizer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 配载订单最小输入。禁止出现客户姓名、手机号、详细门牌地址等 PII。
 */
public record LoadOrderInput(
        @NotBlank String orderId,
        String transportOrderId,
        String lineId,
        String destinationAgencyId,
        @NotNull @Positive Integer pieceCount,
        @NotNull @Positive Double weightKg,
        @NotNull @Positive Double volumeM3,
        Integer priority,
        Boolean stackable
) {
}
