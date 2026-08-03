package com.cc.deploy.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（业务异常统一转成 BaseResponse.fail 响应，HTTP 始终 200）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public BaseResponse<Void> handleUnauthorized(UnauthorizedException e) {
        return BaseResponse.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public BaseResponse<Void> handleForbidden(ForbiddenException e) {
        return BaseResponse.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return BaseResponse.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return BaseResponse.fail(ResultCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return BaseResponse.fail(ResultCode.INTERNAL_ERROR,
                e.getMessage() == null ? "系统异常" : e.getMessage());
    }
}
