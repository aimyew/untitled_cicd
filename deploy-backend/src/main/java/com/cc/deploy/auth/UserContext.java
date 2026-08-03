package com.cc.deploy.auth;

import com.cc.deploy.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前登录用户上下文工具（基于 request attribute）
 */
public final class UserContext {

    private UserContext() {}

    public static User current() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        return (User) request.getAttribute(AuthInterceptor.ATTR_USER);
    }

    public static Long currentUserId() {
        User u = current();
        return u == null ? null : u.getId();
    }

    public static String currentIp() {
        User u = current();
        if (u != null) return u.getIp();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? "" : IpUtil.getClientIp(attrs.getRequest());
    }
}
