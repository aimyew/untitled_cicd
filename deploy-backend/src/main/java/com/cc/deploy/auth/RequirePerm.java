package com.cc.deploy.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解：方法上标注所需的权限码。
 * <p>value 支持 SpEL 风格，形如 "project:deploy:#projectId"，会从方法参数中取值。
 * <p>需要与 @Auth 同时使用（或所在 Controller 已标 @Auth）。
 * <p>超管自动绕过校验。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 权限码（支持 #参数名 占位符） */
    String value();
}
