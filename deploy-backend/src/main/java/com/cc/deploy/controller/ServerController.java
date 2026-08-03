package com.cc.deploy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc.deploy.auth.Auth;
import com.cc.deploy.auth.PermissionCode;
import com.cc.deploy.auth.RequirePerm;
import com.cc.deploy.auth.UserContext;
import com.cc.deploy.common.ForbiddenException;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.common.PageResult;
import com.cc.deploy.config.DeployProperties;
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.entity.ServerInfo;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.ServerInfoMapper;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.PermissionService;
import com.cc.deploy.util.AesUtil;
import com.cc.deploy.util.SshUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务器管理
 */
@RestController
@RequestMapping("/api/servers")
@Auth
@RequiredArgsConstructor
public class ServerController {

    private final ServerInfoMapper serverInfoMapper;
    private final DeployProperties props;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @GetMapping
    @RequirePerm(PermissionCode.SERVER_QUERY)
    public BaseResponse<PageResult<ServerInfo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<ServerInfo> wrapper = new LambdaQueryWrapper<ServerInfo>()
                .and(keyword != null && !keyword.isBlank(), w ->
                        w.like(ServerInfo::getName, keyword).or().like(ServerInfo::getHost, keyword))
                .orderByDesc(ServerInfo::getId);
        Page<ServerInfo> pageResult = serverInfoMapper.selectPage(new Page<>(page, pageSize), wrapper);
        // 密码不下发到前端
        pageResult.getRecords().forEach(s -> s.setPassword(null));
        return BaseResponse.ok(PageResult.of(pageResult.getTotal(), page, pageSize, pageResult.getRecords()));
    }

    /**
     * 新增/编辑。编辑时密码留空表示不修改。
     * 新增需 server:add，编辑需 server:edit（通过代码判断 server.id 区分）
     */
    @PostMapping
    public BaseResponse<Void> save(@RequestBody ServerInfo server) {
        Assert.hasText(server.getName(), "服务器名称不能为空");
        Assert.hasText(server.getHost(), "host 不能为空");
        Assert.hasText(server.getUsername(), "用户名不能为空");
        boolean isNew = server.getId() == null;
        checkEditPerm(isNew ? PermissionCode.SERVER_ADD : PermissionCode.SERVER_EDIT);

        if (server.getPort() == null) {
            server.setPort(22);
        }
        if (isNew) {
            Assert.hasText(server.getPassword(), "密码不能为空");
            server.setPassword(AesUtil.encrypt(server.getPassword(), props.getAesKey()));
            serverInfoMapper.insert(server);
            auditService.log("SERVER_ADD", AuditLog.TARGET_SERVER, server.getId(),
                    "name=" + server.getName() + ", host=" + server.getHost());
        } else {
            if (StringUtils.hasText(server.getPassword())) {
                server.setPassword(AesUtil.encrypt(server.getPassword(), props.getAesKey()));
            } else {
                server.setPassword(null); // null 字段不更新，保留原密码
            }
            serverInfoMapper.updateById(server);
            auditService.log("SERVER_EDIT", AuditLog.TARGET_SERVER, server.getId(),
                    "name=" + server.getName());
        }
        return BaseResponse.ok();
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        // 删除是高危操作，只允许超管
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");
        if (!user.isSuperAdmin()) throw new ForbiddenException("仅超管可删除服务器");
        serverInfoMapper.deleteById(id);
        auditService.log("SERVER_DELETE", AuditLog.TARGET_SERVER, id, "id=" + id);
        return BaseResponse.ok();
    }

    /**
     * 测试 SSH 连接
     */
    @PostMapping("/{id}/test")
    @RequirePerm(PermissionCode.SERVER_QUERY)
    public BaseResponse<String> testConnect(@PathVariable Long id) throws Exception {
        ServerInfo server = serverInfoMapper.selectById(id);
        Assert.notNull(server, "服务器不存在");
        String password = AesUtil.decrypt(server.getPassword(), props.getAesKey());
        SshUtil.testConnect(server.getHost(), server.getPort(), server.getUsername(), password);
        return BaseResponse.ok("连接成功");
    }

    private void checkEditPerm(String permCode) {
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");
        if (user.isSuperAdmin()) return;
        if (!permissionService.hasPerm(user.getId(), permCode)) {
            throw new ForbiddenException("无权限: " + permCode);
        }
    }
}
