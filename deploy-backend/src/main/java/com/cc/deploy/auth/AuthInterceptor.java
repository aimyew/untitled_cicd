package com.cc.deploy.auth;

import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.common.ResultCode;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：
 *   1. 尝试解析 Authorization Bearer token，找到 user 写入 request attribute
 *   2. 如果方法/Controller 标注了 @Auth，未登录时返回 401
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = "cc_current_user";

    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        // 1. 尝试解析 token，有则写入 attribute
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            Long userId = tokenService.validate(token);
            if (userId != null) {
                User user = userMapper.selectById(userId);
                if (user != null) {
                    request.setAttribute(ATTR_USER, user);
                }
            }
        }

        // 2. 判断是否需要强制登录
        boolean needsAuth = hm.hasMethodAnnotation(Auth.class)
                || hm.getBeanType().isAnnotationPresent(Auth.class);
        if (!needsAuth) {
            return true;
        }
        if (request.getAttribute(ATTR_USER) == null) {
            writeJson(response, BaseResponse.fail(ResultCode.UNAUTHORIZED, "未登录"));
            return false;
        }
        return true;
    }

    private void writeJson(HttpServletResponse response, BaseResponse<?> r) throws Exception {
        // HTTP 始终 200，业务状态码由 body.code 表达
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(r));
    }
}
