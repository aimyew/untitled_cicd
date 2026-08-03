package com.cc.deploy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc.deploy.auth.*;
import com.cc.deploy.common.ForbiddenException;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.common.PageResult;
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.entity.Project;
import com.cc.deploy.entity.ServerInfo;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.ProjectMapper;
import com.cc.deploy.mapper.ServerInfoMapper;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
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
 * 项目管理
 */
@RestController
@RequestMapping("/api/projects")
@Auth
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;
    private final ServerInfoMapper serverInfoMapper;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @GetMapping
    @RequirePerm(PermissionCode.PROJECT_QUERY)
    public BaseResponse<PageResult<ProjectVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        // 1. 从 DB 查项目（按名称模糊查询）
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .like(keyword != null && !keyword.isBlank(), Project::getName, keyword)
                .orderByDesc(Project::getId);
        List<Project> projects = projectMapper.selectList(wrapper);

        // 2. 按项目白名单过滤（超管看全部）
        User user = UserContext.current();
        if (user == null) return BaseResponse.ok(PageResult.of(0, page, pageSize, java.util.Collections.emptyList()));
        if (!user.isSuperAdmin()) {
            java.util.Set<Long> allowed = new java.util.HashSet<>(
                    permissionService.listProjectIdsByUserId(user.getId()));
            projects = projects.stream()
                    .filter(p -> allowed.contains(p.getId()))
                    .toList();
        }

        // 3. 批量查服务器名称
        List<ServerInfo> servers = serverInfoMapper.selectList(null);
        java.util.Map<Long, String> serverNameMap = new java.util.HashMap<>();
        for (ServerInfo s : servers) serverNameMap.put(s.getId(), s.getName());

        // 4. 组装 VO
        List<ProjectVO> voList = new java.util.ArrayList<>(projects.size());
        for (Project p : projects) {
            ProjectVO vo = new ProjectVO();
            vo.id = p.getId();
            vo.name = p.getName();
            vo.type = p.getType();
            vo.gitUrl = p.getGitUrl();
            vo.branch = p.getBranch();
            vo.localPath = p.getLocalPath();
            vo.buildCmd = p.getBuildCmd();
            vo.buildProfile = p.getBuildProfile();
            vo.artifactPath = p.getArtifactPath();
            vo.serverId = p.getServerId();
            vo.serverName = serverNameMap.getOrDefault(p.getServerId(), "");
            vo.uploadDir = p.getUploadDir();
            vo.deployCmd = p.getDeployCmd();
            voList.add(vo);
        }

        // 5. Java 侧分页
        long total = voList.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(voList.size(), from + pageSize);
        List<ProjectVO> pageRecords = from >= voList.size()
                ? java.util.Collections.emptyList()
                : voList.subList(from, to);
        return BaseResponse.ok(PageResult.of(total, page, pageSize, pageRecords));
    }

    @PostMapping
    public BaseResponse<Void> save(@RequestBody Project project) {
        Assert.hasText(project.getName(), "项目名称不能为空");
        Assert.hasText(project.getType(), "项目类型不能为空");
        Assert.hasText(project.getGitUrl(), "Git 地址不能为空");
        Assert.hasText(project.getBuildCmd(), "构建命令不能为空");
        Assert.hasText(project.getArtifactPath(), "产物路径不能为空");
        Assert.notNull(project.getServerId(), "目标服务器不能为空");
        Assert.hasText(project.getUploadDir(), "上传目录不能为空");
        if (!org.springframework.util.StringUtils.hasText(project.getBranch())) {
            project.setBranch("dev");
        }
        boolean isNew = project.getId() == null;
        checkEditPerm(isNew ? PermissionCode.PROJECT_ADD : PermissionCode.PROJECT_EDIT);
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");

        // 编辑场景：无 server:edit 权限的非超管，禁止改动敏感字段
        // 涵盖：本地目录、构建命令、打包Profile、产物路径、目标服务器、上传目录、部署命令
        if (!isNew && !user.isSuperAdmin() && !permissionService.hasPerm(user.getId(), PermissionCode.SERVER_EDIT)) {
            Project old = projectMapper.selectById(project.getId());
            if (old != null) {
                project.setLocalPath(old.getLocalPath());
                project.setBuildCmd(old.getBuildCmd());
                project.setBuildProfile(old.getBuildProfile());
                project.setArtifactPath(old.getArtifactPath());
                project.setServerId(old.getServerId());
                project.setUploadDir(old.getUploadDir());
                project.setDeployCmd(old.getDeployCmd());
            }
        }

        if (isNew) {
            projectMapper.insert(project);
            auditService.log("PROJECT_ADD", AuditLog.TARGET_PROJECT, project.getId(),
                    "name=" + project.getName());
            // 新建项目后自动把创建者加入该项目的白名单
            permissionService.grantProjectPerm(user.getId(), project.getId(), user.getId());
        } else {
            projectMapper.updateById(project);
            auditService.log("PROJECT_EDIT", AuditLog.TARGET_PROJECT, project.getId(),
                    "name=" + project.getName());
        }
        return BaseResponse.ok();
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        // 删除是高危操作，只允许超管
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");
        if (!user.isSuperAdmin()) throw new ForbiddenException("仅超管可删除项目");
        projectMapper.deleteById(id);
        auditService.log("PROJECT_DELETE", AuditLog.TARGET_PROJECT, id, "id=" + id);
        return BaseResponse.ok();
    }

    private void checkEditPerm(String permCode) {
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");
        if (user.isSuperAdmin()) return;
        if (!permissionService.hasPerm(user.getId(), permCode)) {
            throw new ForbiddenException("无权限: " + permCode);
        }
    }

    /**
     * 项目列表 VO：在 Project 基础上拼接 serverName，避免前端再查一次 /api/servers
     */
    public static class ProjectVO {
        public Long id;
        public String name;
        public String type;
        public String gitUrl;
        public String branch;
        public String localPath;
        public String buildCmd;
        public String buildProfile;
        public String artifactPath;
        public Long serverId;
        public String serverName;
        public String uploadDir;
        public String deployCmd;
    }
}
