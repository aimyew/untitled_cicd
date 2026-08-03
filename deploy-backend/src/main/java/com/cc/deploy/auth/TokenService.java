package com.cc.deploy.auth;

import com.cc.deploy.config.DeployProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的内存 Token 服务：
 *   - 登录时生成 UUID token 放入内存 Map，关联 userId 和过期时间
 *   - 每个请求校验 token 是否存在、是否过期
 *   - 服务重启后所有 token 失效，需要重新登录（内部工具，可接受）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final DeployProperties props;

    /** token -> TokenInfo */
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + props.getTokenExpireSeconds() * 1000L;
        tokens.put(token, new TokenInfo(userId, expiresAt));
        log.info("签发 token: userId={}, token={}..., 有效期={}秒", userId, token.substring(0, 8), props.getTokenExpireSeconds());
        return token;
    }

    /** 校验 token，返回 userId；无效/过期返回 null */
    public Long validate(String token) {
        if (token == null) return null;
        TokenInfo info = tokens.get(token);
        if (info == null) return null;
        if (System.currentTimeMillis() > info.expiresAt) {
            tokens.remove(token);
            return null;
        }
        return info.userId;
    }

    /** 退出登录 */
    public void revoke(String token) {
        if (token != null) tokens.remove(token);
    }

    /** 强制失效某用户的所有 token（如禁用用户时） */
    public void revokeAll(Long userId) {
        tokens.entrySet().removeIf(e -> userId.equals(e.getValue().userId));
    }

    private static class TokenInfo {
        final Long userId;
        final long expiresAt;
        TokenInfo(Long userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
