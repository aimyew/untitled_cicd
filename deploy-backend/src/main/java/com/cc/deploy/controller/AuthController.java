package com.cc.deploy.controller;

import com.cc.deploy.auth.IpUtil;
import com.cc.deploy.auth.TokenService;
import com.cc.deploy.auth.UserContext;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.entity.User;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录 / 登出 / 获取当前用户 / 改密
 * <p>大部分接口在 WebMvcConfig 中排除在拦截器之外
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final AuditService auditService;

    /**
     * 返回请求者本机 IP（无需登录，供登录页"自动填充 IP"使用）
     */
    @GetMapping("/client-ip")
    public BaseResponse<String> clientIp(HttpServletRequest request) {
        return BaseResponse.ok(IpUtil.getClientIp(request));
    }

    /**
     * 登录：返回 { token, user }
     * <p>body 可省略 ip 字段，后端会自动从请求中提取；前端也可以显式传 ip 覆盖。
     */
    @PostMapping("/login")
    public BaseResponse<UserService.LoginResult> login(@RequestBody(required = false) Map<String, String> body,
                                            HttpServletRequest request) {
        String ip = null;
        if (body != null) ip = IpUtil.formatIp(body.get("ip"));
        if (ip == null || ip.isBlank()) ip = IpUtil.getClientIp(request);
        String password = body == null ? null : body.get("password");
        Assert.hasText(password, "密码不能为空");
        UserService.LoginResult result = userService.login(ip, password);
        auditService.log("LOGIN", "USER", result.user.id, "ip=" + result.user.ip);
        return BaseResponse.ok(result);
    }

    /**
     * 取当前登录用户 + 权限码列表。
     *   未登录时返回 null（前端据此跳登录页）。
     */
    @GetMapping("/me")
    public BaseResponse<UserService.UserSummary> me() {
        User user = UserContext.current();
        return BaseResponse.ok(userService.me(user));
    }

    /**
     * 登出（需要 token）
     */
    @PostMapping("/logout")
    public BaseResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenService.revoke(authHeader.substring(7).trim());
        }
        return BaseResponse.ok();
    }

    /**
     * 修改自己的密码（需要登录）
     */
    @PostMapping("/change-password")
    public BaseResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = UserContext.currentUserId();
        Assert.notNull(userId, "未登录");
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        Assert.hasText(oldPwd, "旧密码不能为空");
        Assert.hasText(newPwd, "新密码不能为空");
        userService.changePassword(userId, oldPwd, newPwd);
        auditService.log("CHANGE_PASSWORD", "USER", userId, "用户修改自己的密码");
        return BaseResponse.ok();
    }
}
