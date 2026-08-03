package com.cc.deploy.controller;

import com.cc.deploy.auth.Auth;
import com.cc.deploy.auth.PermissionCode;
import com.cc.deploy.auth.RequirePerm;
import com.cc.deploy.auth.TokenService;
import com.cc.deploy.auth.UserContext;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.common.PageResult;
import com.cc.deploy.entity.FuncPermDef;
import com.cc.deploy.entity.User;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.PermissionService;
import com.cc.deploy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户管理（仅超管可调用）
 */
@RestController
@RequestMapping("/api/users")
@Auth
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final TokenService tokenService;

    @GetMapping("/func-perm-defs")
    @RequirePerm(PermissionCode.USER_MANAGE)
    public BaseResponse<List<FuncPermDef>> funcPermDefs() {
        return BaseResponse.ok(permissionService.listFuncPermDefs());
    }

    @GetMapping
    @RequirePerm(PermissionCode.USER_MANAGE)
    public BaseResponse<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        List<User> users = userService.listAll();
        // 按 keyword（ip 或 nickname 模糊）过滤
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getIp() != null && u.getIp().toLowerCase().contains(kw))
                            || (u.getNickname() != null && u.getNickname().toLowerCase().contains(kw)))
                    .toList();
        }
        List<UserVO> voList = new ArrayList<>(users.size());
        for (User u : users) {
            UserVO vo = new UserVO();
            vo.id = u.getId();
            vo.ip = u.getIp();
            vo.nickname = u.getNickname();
            vo.role = u.getRole();
            vo.status = u.getStatus();
            vo.createTime = u.getCreateTime();
            vo.perms = new ArrayList<>(permissionService.listPermCodes(u.getId()));
            vo.deployProjectIds = permissionService.listDeployPermsByUserId(u.getId())
                    .stream().map(dp -> dp.getProjectId()).toList();
            voList.add(vo);
        }
        // Java 侧分页
        long total = voList.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(voList.size(), from + pageSize);
        List<UserVO> pageRecords = from >= voList.size()
                ? java.util.Collections.emptyList()
                : voList.subList(from, to);
        return BaseResponse.ok(PageResult.of(total, page, pageSize, pageRecords));
    }

    @PostMapping("/{id}/status")
    @RequirePerm(PermissionCode.USER_MANAGE)
    public BaseResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        Assert.hasText(status, "状态不能为空");
        userService.updateStatus(id, status);
        auditService.log("USER_STATUS", "USER", id, "status=" + status);
        return BaseResponse.ok();
    }

    @PostMapping("/{id}/nickname")
    @RequirePerm(PermissionCode.USER_MANAGE)
    public BaseResponse<Void> updateNickname(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");
        userService.updateNickname(id, nickname);
        auditService.log("USER_NICKNAME", "USER", id, "nickname=" + nickname);
        return BaseResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    @RequirePerm(PermissionCode.USER_MANAGE)
    public BaseResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        Assert.hasText(newPassword, "新密码不能为空");
        userService.resetPassword(id, newPassword);
        auditService.log("USER_RESET_PASSWORD", "USER", id, "超管重置密码");
        return BaseResponse.ok();
    }

    @PostMapping("/{id}/permissions")
    @RequirePerm(PermissionCode.USER_MANAGE)
    public BaseResponse<Void> updatePermissions(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        List<String> codes = body.get("codes");
        Assert.notNull(codes, "权限码列表不能为空");
        Long grantBy = UserContext.currentUserId();
        permissionService.replacePerms(id, codes, grantBy);
        // 权限变更：强制该用户重新登录，避免浏览器缓存旧权限
        tokenService.revokeAll(id);
        auditService.log("USER_PERMISSIONS", "USER", id, "codes=" + codes);
        return BaseResponse.ok();
    }

    @PostMapping("/{id}/deploy-permissions")
    @RequirePerm(PermissionCode.USER_MANAGE)
    public BaseResponse<Void> updateDeployPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> projectIds = body.get("projectIds");
        Assert.notNull(projectIds, "项目 id 列表不能为空");
        Long grantBy = UserContext.currentUserId();
        permissionService.replaceDeployPerms(id, projectIds, grantBy);
        // 项目白名单变更：强制该用户重新登录，避免浏览器缓存旧白名单
        tokenService.revokeAll(id);
        auditService.log("USER_DEPLOY_PERMISSIONS", "USER", id, "projectIds=" + projectIds);
        return BaseResponse.ok();
    }

    public static class UserVO {
        public Long id;
        public String ip;
        public String nickname;
        public String role;
        public String status;
        public java.time.LocalDateTime createTime;
        public List<String> perms;
        public List<Long> deployProjectIds;
    }
}
