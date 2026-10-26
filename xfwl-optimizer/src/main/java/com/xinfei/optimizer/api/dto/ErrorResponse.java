package com.xinfei.optimizer.api.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String requestId,
        List<FieldErrorDto> fieldErrors
) {
    public record FieldErrorDto(String field, String message) {
    }
}
