package com.xinfei.optimizer.api.dto;

/**
 * 约束不可行或资源不满足的解释。
 */
public record ConstraintViolationDto(
        String code,
        String severity,
        String message,
        String orderId,
        String vehicleId,
        String actualValue,
        String limitValue
) {
    public static ConstraintViolationDto hard(String code, String message) {
        return new ConstraintViolationDto(code, "HARD", message, null, null, null, null);
    }

    public static ConstraintViolationDto hard(String code, String message, String orderId, String vehicleId,
                                              String actualValue, String limitValue) {
        return new ConstraintViolationDto(code, "HARD", message, orderId, vehicleId, actualValue, limitValue);
    }
}
