package com.cc.deploy.common;

/**
 * 系统业务状态码（HTTP 全部返回 200，业务语义由本枚举表达）
 * <p>编码规则：从 1001 起逐个排下去。
 */
public enum ResultCode {

    SUCCESS(0,    "success"),
    FAIL(1,       "fail"),

    /** 未登录 / 会话失效 / 密码错误 */
    UNAUTHORIZED(1001, "未登录"),

    /** 无权限（未授权 / 账号被禁用 / 新用户未启用 / 仅超管可操作 / 项目无权限 等） */
    FORBIDDEN(1003, "无权限"),

    /** 参数校验失败 */
    BAD_REQUEST(1004, "参数错误"),

    /** 服务器内部错误（兜底异常） */
    INTERNAL_ERROR(1005, "服务器内部错误");

    public final int code;
    public final String defaultMsg;

    ResultCode(int code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }
}
