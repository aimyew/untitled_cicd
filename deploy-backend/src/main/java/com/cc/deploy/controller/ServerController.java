package com.cc.deploy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc.deploy.auth.Auth;
import com.cc.deploy.auth.PermissionCode;
import com.cc.deploy.auth.RequirePerm;
import com.cc.deploy.auth.UserContext;
import com.cc.deploy.common.ForbiddenException;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.common.PageResult;
import com.cc.deploy.config.DeployProperties;
import com.cc.deploy.dto.CreateServerRequest;
import com.cc.deploy.dto.UpdateServerRequest;
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.entity.ServerCommand;
import com.cc.deploy.entity.ServerInfo;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.ServerCommandMapper;
import com.cc.deploy.mapper.ServerInfoMapper;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.PermissionService;
import com.cc.deploy.util.AesUtil;
import com.cc.deploy.util.SshUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 服务器管理
 */
@RestController
@RequestMapping("/api/servers")
@Auth
@RequiredArgsConstructor
public class ServerController {

    private final ServerInfoMapper serverInfoMapper;
    private final ServerCommandMapper serverCommandMapper;
    private final DeployProperties props;
    private final AuditService auditService;
    private final PermissionService permissionService;

    /** 命令黑名单：包含这些关键词的命令禁止执行 */
    private static final List<String> COMMAND_BLACKLIST = List.of(
            "rm -rf /", "rm -rf /*", "mkfs", "dd if=", ":(){:|:&};:",
            "chmod -R 777 /", "> /dev/sda", ":(){ :|:& };:"
    );

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

    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody CreateServerRequest req) {
        checkEditPerm(PermissionCode.SERVER_ADD);

        int port = req.getPort() == null ? 22 : req.getPort();

        ServerInfo server = new ServerInfo();
        server.setName(req.getName());
        server.setHost(req.getHost());
        server.setPort(port);
        server.setUsername(req.getUsername());
        server.setPassword(AesUtil.encrypt(req.getPassword(), props.getAesKey()));
        server.setRemark(req.getRemark());

        serverInfoMapper.insert(server);
        auditService.log("SERVER_ADD", AuditLog.TARGET_SERVER, server.getId(),
                "name=" + req.getName() + ", host=" + req.getHost());
        return BaseResponse.ok();
    }

    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateServerRequest req) {
        checkEditPerm(PermissionCode.SERVER_EDIT);

        int port = req.getPort() == null ? 22 : req.getPort();

        LambdaUpdateWrapper<ServerInfo> wrapper = new LambdaUpdateWrapper<ServerInfo>()
                .eq(ServerInfo::getId, id)
                .set(ServerInfo::getName, req.getName())
                .set(ServerInfo::getHost, req.getHost())
                .set(ServerInfo::getPort, port)
                .set(ServerInfo::getUsername, req.getUsername())
                .set(ServerInfo::getRemark, req.getRemark());

        // 密码留空表示不修改
        if (StringUtils.hasText(req.getPassword())) {
            wrapper.set(ServerInfo::getPassword, AesUtil.encrypt(req.getPassword(), props.getAesKey()));
        }

        serverInfoMapper.update(null, wrapper);
        auditService.log("SERVER_EDIT", AuditLog.TARGET_SERVER, id, "name=" + req.getName());
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

    /**
     * 查询服务器已配置的命令列表（仅超管）
     */
    @GetMapping("/{id}/commands")
    public BaseResponse<List<ServerCommand>> listCommands(@PathVariable Long id) {
        requireSuperAdmin();
        List<ServerCommand> commands = serverCommandMapper.selectList(
                new LambdaQueryWrapper<ServerCommand>()
                        .eq(ServerCommand::getServerId, id)
                        .orderByAsc(ServerCommand::getSortOrder));
        return BaseResponse.ok(commands);
    }

    /**
     * 执行已配置的命令（仅超管）
     */
    @PostMapping("/{id}/execute/{commandId}")
    public BaseResponse<Map<String, Object>> executeCommand(@PathVariable Long id, @PathVariable Long commandId) {
        requireSuperAdmin();
        ServerInfo server = serverInfoMapper.selectById(id);
        Assert.notNull(server, "服务器不存在");
        ServerCommand cmd = serverCommandMapper.selectById(commandId);
        Assert.notNull(cmd, "命令不存在");
        Assert.isTrue(cmd.getServerId().equals(id), "命令不属于该服务器");

        checkBlacklist(cmd.getCommand());

        String password = AesUtil.decrypt(server.getPassword(), props.getAesKey());
        List<String> outputLines = new ArrayList<>();
        int exitCode = SshUtil.exec(server.getHost(), server.getPort(), server.getUsername(), password,
                cmd.getCommand(), outputLines::add);

        auditService.log("SERVER_EXEC", AuditLog.TARGET_SERVER, id,
                "command=" + cmd.getCommand() + ", exitCode=" + exitCode);

        return BaseResponse.ok(Map.of(
                "exitCode", exitCode,
                "output", String.join("\n", outputLines)
        ));
    }

    /**
     * 生成 INSERT SQL（不入库，仅返回 SQL 字符串）
     */
    @PostMapping("/{id}/generate-sql")
    public BaseResponse<String> generateSql(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireSuperAdmin();
        String name = body.get("name");
        String command = body.get("command");
        Assert.hasText(name, "命令名称不能为空");
        Assert.hasText(command, "命令内容不能为空");

        String sql = "INSERT INTO cc_server_command (server_id, name, command, sort_order) VALUES ("
                + id + ", '" + name.replace("'", "\\'") + "', '"
                + command.replace("'", "\\'") + "', 0);";
        return BaseResponse.ok(sql);
    }

    private void requireSuperAdmin() {
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");
        if (!user.isSuperAdmin()) throw new ForbiddenException("仅超管可操作");
    }

    private void checkBlacklist(String command) {
        String lower = command.toLowerCase().replaceAll("\\s+", " ").trim();
        for (String blocked : COMMAND_BLACKLIST) {
            if (lower.contains(blocked.toLowerCase())) {
                throw new ForbiddenException("命令包含黑名单内容，禁止执行: " + blocked);
            }
        }
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
