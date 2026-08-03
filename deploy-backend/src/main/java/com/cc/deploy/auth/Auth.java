package com.cc.deploy.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要登录才能访问的方法。
 * <p>未传 token 时返回 401。
 * <p>加在 Controller 方法上；如果 Controller 级别使用，则对所有方法生效。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {
}
