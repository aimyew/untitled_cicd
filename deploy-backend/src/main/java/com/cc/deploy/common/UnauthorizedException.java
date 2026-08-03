package com.cc.deploy.common;

/**
 * 未认证异常（1001）。
 * <p>HTTP 返回 200，业务状态码由 body.code=1001 表达。
 */
public class UnauthorizedException extends RuntimeException {

    private final ResultCode resultCode;

    public UnauthorizedException(String message) {
        super(message);
        this.resultCode = ResultCode.UNAUTHORIZED;
    }

    public UnauthorizedException() {
        super(ResultCode.UNAUTHORIZED.defaultMsg);
        this.resultCode = ResultCode.UNAUTHORIZED;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
