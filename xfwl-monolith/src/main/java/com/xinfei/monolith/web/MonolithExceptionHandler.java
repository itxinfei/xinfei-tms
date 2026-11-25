package com.xinfei.monolith.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SameTokenInvalidException;
import com.xinfei.common.exception.XfException;
import com.xinfei.common.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 单体统一异常处理。
 *
 * <p>背景：微服务各进程只加载本模块 classpath 的 advice，互不干扰；单体把所有模块合并到一个
 * Spring 容器，多个 {@code @RestControllerAdvice} 共存。其中 xfwl-netty 的
 * {@code SaTokenExceptionHandler} 以 HIGHEST_PRECEDENCE 全局兜底，会跨模块抢先把参数校验等
 * 异常错误收口为 500，已在 {@code MonolithApplication} 不加载。本类在单体装配层统一复刻
 * 正确的 HTTP 语义，不修改任何微服务文件。</p>
 *
 * <p>口径：鉴权 401/403、参数与校验 400、方法不支持 405、媒体类型 415、资源不存在 404、
 * 业务异常 {@link XfException} 走 200 + 业务 code（前端 Result 约定）、其余兜底 500。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MonolithExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MonolithExceptionHandler.class);

    /** 未登录或登录已过期 */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handleNotLogin(NotLoginException e) {
        return Result.error(HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期，请重新登录");
    }

    /** Same-Token 无效：绕过网关直连 */
    @ExceptionHandler(SameTokenInvalidException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handleSameTokenInvalid(SameTokenInvalidException e) {
        return Result.error(HttpStatus.UNAUTHORIZED.value(), "非法来源请求，请通过网关访问");
    }

    /** 角色不符 */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result handleNotRole(NotRoleException e) {
        log.warn("[鉴权] 角色不符: {}", e.getMessage());
        return Result.error(HttpStatus.FORBIDDEN.value(), "无权访问该功能");
    }

    /** body 校验 / 表单绑定失败：400 + 首条字段错误 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleValidation(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return Result.error(HttpStatus.BAD_REQUEST.value(), msg);
    }

    /** 缺参数 / 类型不匹配 / 请求体不可读：400 */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleBadRequest(Exception e) {
        log.warn("[参数错误] 400: {}", e.getMessage());
        return Result.error(HttpStatus.BAD_REQUEST.value(), "请求参数错误");
    }

    /** 方法不支持：405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方法不支持");
    }

    /** 媒体类型不支持：415 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result handleMediaType(HttpMediaTypeNotSupportedException e) {
        return Result.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "不支持的媒体类型");
    }

    /** 资源不存在：404 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleNoResource(NoResourceFoundException e) {
        return Result.error(HttpStatus.NOT_FOUND.value(), "请求的资源不存在");
    }

    /** 业务异常：HTTP 200，透传业务 code 与消息（前端按 code 判定） */
    @ExceptionHandler(XfException.class)
    public Result handleBusiness(XfException e) {
        log.warn("[业务异常] code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 兜底：未分类异常 500，不外露堆栈细节 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception e) {
        log.error("[系统异常] 500", e);
        return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "系统繁忙，请稍后再试");
    }
}
