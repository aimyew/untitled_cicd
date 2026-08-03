package com.cc.deploy.common;

import lombok.Data;

/**
 * 统一返回结果
 * <p>HTTP 状态码始终为 200；业务语义由 {@link ResultCode} 表达。
 */
@Data
public class BaseResponse<T> {

    public static final int SUCCESS = ResultCode.SUCCESS.code;
    public static final int FAIL = ResultCode.FAIL.code;

    private int code;
    private String msg;
    private T data;

    public static <T> BaseResponse<T> ok() {
        return ok(null);
    }

    public static <T> BaseResponse<T> ok(T data) {
        BaseResponse<T> r = new BaseResponse<>();
        r.code = ResultCode.SUCCESS.code;
        r.msg = ResultCode.SUCCESS.defaultMsg;
        r.data = data;
        return r;
    }

    /** 通用失败（保持原有签名，默认 ResultCode.FAIL） */
    public static <T> BaseResponse<T> fail(String msg) {
        return fail(ResultCode.FAIL, msg);
    }

    /** 按业务状态码返回失败，msg 为 null 时使用默认文案 */
    public static <T> BaseResponse<T> fail(ResultCode rc, String msg) {
        BaseResponse<T> r = new BaseResponse<>();
        r.code = rc.code;
        r.msg = (msg == null || msg.isBlank()) ? rc.defaultMsg : msg;
        return r;
    }

    public static <T> BaseResponse<T> fail(ResultCode rc) {
        return fail(rc, null);
    }
}
