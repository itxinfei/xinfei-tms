package com.xinfei.optimizer.api;

import com.xinfei.optimizer.api.dto.ErrorResponse;
import com.xinfei.optimizer.domain.OptimizerRequestException;
import com.xinfei.optimizer.domain.OptimizerUnavailableException;
import com.xinfei.optimizer.domain.OptimizerUnsupportedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class OptimizerExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldErrorDto> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return new ErrorResponse(Instant.now(), 400, "INVALID_REQUEST",
                "请求参数不合法", requestId(), fieldErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, OptimizerRequestException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(Exception e) {
        return error(400, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(OptimizerUnsupportedException.class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public ErrorResponse handleUnsupported(OptimizerUnsupportedException e) {
        return error(501, "NOT_IMPLEMENTED", e.getMessage());
    }

    @ExceptionHandler(OptimizerUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleUnavailable(OptimizerUnavailableException e) {
        return error(503, "ENGINE_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception e) {
        return error(500, "INTERNAL_ERROR", "求解服务内部异常，请查看服务日志并降级规则引擎");
    }

    private ErrorResponse.FieldErrorDto toFieldError(FieldError error) {
        return new ErrorResponse.FieldErrorDto(error.getField(), error.getDefaultMessage());
    }

    private ErrorResponse error(int status, String code, String message) {
        return new ErrorResponse(Instant.now(), status, code, message, requestId(), List.of());
    }

    private String requestId() {
        return UUID.randomUUID().toString();
    }
}
