package com.cc.deploy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cc.deploy.auth.*;
import com.cc.deploy.common.ForbiddenException;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.common.PageResult;
import com.cc.deploy.dto.CreateProjectRequest;
import com.cc.deploy.dto.UpdateProjectRequest;
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.entity.Project;
import com.cc.deploy.entity.ServerInfo;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.ProjectMapper;
import com.cc.deploy.mapper.ServerInfoMapper;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.PermissionService;
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
            vo.scriptName = p.getScriptName();
            vo.scriptContent = p.getScriptContent();
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
    public BaseResponse<Void> create(@Valid @RequestBody CreateProjectRequest req) {
        checkEditPerm(PermissionCode.PROJECT_ADD);
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");

        String branch = StringUtils.hasText(req.getBranch()) ? req.getBranch() : "dev";

        // 脚本校验
        String scriptName = null;
        String scriptContent = null;
        if (StringUtils.hasText(req.getScriptName())) {
            Assert.hasText(req.getScriptContent(), "填写了目录下脚本名称时，目录下脚本内容不能为空");
            scriptName = req.getScriptName().trim();
            scriptContent = req.getScriptContent();
        }

        Project project = new Project();
        project.setName(req.getName());
        project.setType(req.getType());
        project.setGitUrl(req.getGitUrl());
        project.setBranch(branch);
        project.setLocalPath(req.getLocalPath());
        project.setBuildCmd(req.getBuildCmd());
        project.setBuildProfile(req.getBuildProfile());
        project.setArtifactPath(req.getArtifactPath());
        project.setServerId(req.getServerId());
        project.setUploadDir(req.getUploadDir());
        project.setDeployCmd(req.getDeployCmd());
        project.setScriptName(scriptName);
        project.setScriptContent(scriptContent);

        projectMapper.insert(project);
        auditService.log("PROJECT_ADD", AuditLog.TARGET_PROJECT, project.getId(), "name=" + req.getName());
        // 新建项目后自动把创建者加入该项目的白名单
        permissionService.grantProjectPerm(user.getId(), project.getId(), user.getId());
        return BaseResponse.ok();
    }

    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest req) {
        checkEditPerm(PermissionCode.PROJECT_EDIT);
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");

        String branch = StringUtils.hasText(req.getBranch()) ? req.getBranch() : "dev";

        // 脚本校验
        String scriptName = null;
        String scriptContent = null;
        if (StringUtils.hasText(req.getScriptName())) {
            Assert.hasText(req.getScriptContent(), "填写了目录下脚本名称时，目录下脚本内容不能为空");
            scriptName = req.getScriptName().trim();
            scriptContent = req.getScriptContent();
        }

        LambdaUpdateWrapper<Project> wrapper = new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, id)
                .set(Project::getName, req.getName())
                .set(Project::getType, req.getType())
                .set(Project::getGitUrl, req.getGitUrl())
                .set(Project::getBranch, branch)
                .set(Project::getScriptName, scriptName)
                .set(Project::getScriptContent, scriptContent);

        // 敏感字段：仅超管可改
        if (user.isSuperAdmin()) {
            wrapper.set(Project::getLocalPath, req.getLocalPath())
                   .set(Project::getBuildCmd, req.getBuildCmd())
                   .set(Project::getBuildProfile, req.getBuildProfile())
                   .set(Project::getArtifactPath, req.getArtifactPath())
                   .set(Project::getServerId, req.getServerId())
                   .set(Project::getUploadDir, req.getUploadDir())
                   .set(Project::getDeployCmd, req.getDeployCmd());
        }

        projectMapper.update(null, wrapper);
        auditService.log("PROJECT_EDIT", AuditLog.TARGET_PROJECT, id, "name=" + req.getName());
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
        public String scriptName;
        public String scriptContent;
    }
}
