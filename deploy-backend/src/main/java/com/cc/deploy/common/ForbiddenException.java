package com.cc.deploy.common;

/**
 * 无权限异常（1003）。
 * <p>HTTP 返回 200，业务状态码由 body.code=1003 表达。
 */
public class ForbiddenException extends RuntimeException {

    private final ResultCode resultCode;

    public ForbiddenException(String message) {
        super(message);
        this.resultCode = ResultCode.FORBIDDEN;
    }

    public ForbiddenException() {
        super(ResultCode.FORBIDDEN.defaultMsg);
        this.resultCode = ResultCode.FORBIDDEN;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
