package com.cc.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc.deploy.auth.TokenService;
import com.cc.deploy.common.ForbiddenException;
import com.cc.deploy.common.UnauthorizedException;
import com.cc.deploy.config.DeployProperties;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.UserMapper;
import com.cc.deploy.util.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户服务：CRUD、登录、启用/禁用、改密、自动开户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final PermissionService permissionService;
    private final DeployProperties props;

    /** 列表（不返回密码） */
    public List<User> listAll() {
        List<User> list = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByDesc(User::getId));
        list.forEach(u -> u.setPassword(null));
        return list;
    }

    public User getById(Long id) {
        User u = userMapper.selectById(id);
        if (u != null) u.setPassword(null);
        return u;
    }

    /** 根据 IP 查找用户（不返回密码） */
    public User findByIp(String ip) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getIp, ip));
        if (u != null) u.setPassword(null);
        return u;
    }

    /**
     * 登录：返回 token + 用户信息（含权限码）。
     *   - 用户不存在 → 自动开户为 DISABLED 并拒绝登录
     *   - 用户 DISABLED → 拒绝
     *   - 密码错误 → 拒绝
     */
    public LoginResult login(String ip, String password) {
        Assert.hasText(ip, "IP 不能为空");
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getIp, ip));
        if (user == null) {
            // 自动开户为 DISABLED
            user = autoRegister(ip);
            throw new ForbiddenException("新用户 [" + ip + "] 已自动创建，请联系超管启用后再登录");
        }
        if (!user.isEnabled()) {
            throw new ForbiddenException("账号 [" + ip + "] 已被禁用，请联系超管");
        }
        String stored = user.getPassword();
        try {
            String plain = AesUtil.decrypt(stored, props.getAesKey());
            if (!plain.equals(password)) {
                throw new UnauthorizedException("密码错误");
            }
        } catch (IllegalStateException e) {
            throw new UnauthorizedException("密码校验失败，请联系超管重置");
        }
        String token = tokenService.issue(user.getId());
        LoginResult r = new LoginResult();
        r.token = token;
        r.user = new UserSummary();
        r.user.id = user.getId();
        r.user.ip = user.getIp();
        r.user.nickname = user.getNickname();
        r.user.role = user.getRole();
        r.user.isSuperAdmin = user.isSuperAdmin();
        r.user.perms = permissionService.listPermCodes(user.getId());
        r.user.deployProjectIds = permissionService.listDeployPermsByUserId(user.getId()).stream()
                .map(dp -> dp.getProjectId()).toList();
        return r;
    }

    /** 取当前用户信息 + 权限码（前端初始化用） */
    public UserSummary me(User user) {
        if (user == null) return null;
        UserSummary s = new UserSummary();
        s.id = user.getId();
        s.ip = user.getIp();
        s.nickname = user.getNickname();
        s.role = user.getRole();
        s.isSuperAdmin = user.isSuperAdmin();
        s.perms = permissionService.listPermCodes(user.getId());
        s.deployProjectIds = permissionService.listDeployPermsByUserId(user.getId()).stream()
                .map(dp -> dp.getProjectId()).toList();
        return s;
    }

    /** 启用/禁用用户。禁用时强制清掉该用户的所有 token */
    public void updateStatus(Long userId, String status) {
        User user = userMapper.selectById(userId);
        Assert.notNull(user, "用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
        if (!User.STATUS_ENABLED.equals(status)) {
            tokenService.revokeAll(userId);
        }
    }

    /** 修改昵称 */
    public void updateNickname(Long userId, String nickname) {
        User user = userMapper.selectById(userId);
        Assert.notNull(user, "用户不存在");
        user.setNickname(nickname);
        userMapper.updateById(user);
    }

    /** 重置密码（明文传入） */
    public void resetPassword(Long userId, String newPassword) {
        Assert.hasText(newPassword, "新密码不能为空");
        User user = userMapper.selectById(userId);
        Assert.notNull(user, "用户不存在");
        user.setPassword(AesUtil.encrypt(newPassword, props.getAesKey()));
        userMapper.updateById(user);
        tokenService.revokeAll(userId);
    }

    /** 修改自己密码 */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        Assert.hasText(newPassword, "新密码不能为空");
        User user = userMapper.selectById(userId);
        Assert.notNull(user, "用户不存在");
        try {
            String plain = AesUtil.decrypt(user.getPassword(), props.getAesKey());
            if (!plain.equals(oldPassword)) {
                throw new UnauthorizedException("旧密码错误");
            }
        } catch (IllegalStateException e) {
            throw new UnauthorizedException("密码校验失败");
        }
        user.setPassword(AesUtil.encrypt(newPassword, props.getAesKey()));
        userMapper.updateById(user);
    }

    /** 启动时调用：确保超管账号存在，不存在则自动生成随机密码 */
    public String ensureSuperAdmin() {
        String ip = props.getSuperAdminIp();
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getIp, ip));
        if (admin != null) return null;
        String randomPwd = randomPassword(12);
        admin = new User();
        admin.setIp(ip);
        admin.setNickname("超级管理员");
        admin.setRole(User.ROLE_SUPER_ADMIN);
        admin.setStatus(User.STATUS_ENABLED);
        admin.setPassword(AesUtil.encrypt(randomPwd, props.getAesKey()));
        userMapper.insert(admin);
        return randomPwd;
    }

    private User autoRegister(String ip) {
        User u = new User();
        u.setIp(ip);
        u.setRole(User.ROLE_USER);
        u.setStatus(User.STATUS_DISABLED);
        u.setNickname("新用户-" + ip);
        // 给一个随机密码（超管后续可重置）
        u.setPassword(AesUtil.encrypt(randomPassword(10), props.getAesKey()));
        userMapper.insert(u);
        return u;
    }

    private String randomPassword(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(len);
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    // -------- DTO --------

    public static class LoginResult {
        public String token;
        public UserSummary user;
    }

    public static class UserSummary {
        public Long id;
        public String ip;
        public String nickname;
        public String role;
        public boolean isSuperAdmin;
        public java.util.Set<String> perms;
        public java.util.List<Long> deployProjectIds;
    }
}
