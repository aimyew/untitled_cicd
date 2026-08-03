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
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.entity.DeployRecord;
import com.cc.deploy.entity.Project;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.DeployRecordMapper;
import com.cc.deploy.mapper.ProjectMapper;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.DeployService;
import com.cc.deploy.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部署触发与部署历史
 */
@RestController
@RequestMapping("/api/deploy")
@Auth
@RequiredArgsConstructor
public class DeployController {

    private final DeployService deployService;
    private final DeployRecordMapper recordMapper;
    private final ProjectMapper projectMapper;
    private final AuditService auditService;
    private final PermissionService permissionService;

    /**
     * 触发部署：需要 project:deploy（全局开关）且在 cc_deploy_permission 表里有该项目的白名单
     */
    @PostMapping("/{projectId}")
    @RequirePerm(PermissionCode.PROJECT_DEPLOY)
    public BaseResponse<Long> deploy(@PathVariable Long projectId) {
        checkProjectDeployPerm(projectId);
        Long recordId = deployService.deploy(projectId);
        Project p = projectMapper.selectById(projectId);
        auditService.log("DEPLOY", AuditLog.TARGET_DEPLOY, recordId,
                "projectId=" + projectId + ", project=" + (p == null ? "" : p.getName()));
        return BaseResponse.ok(recordId);
    }

    /**
     * 部署历史列表（不含日志大字段），分页 + 可选 projectName / status 过滤
     */
    @GetMapping("/records")
    @RequirePerm(PermissionCode.HISTORY_QUERY)
    public BaseResponse<PageResult<DeployRecord>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DeployRecord> wrapper = new LambdaQueryWrapper<DeployRecord>()
                .select(DeployRecord::getId, DeployRecord::getProjectId, DeployRecord::getProjectName,
                        DeployRecord::getStatus, DeployRecord::getCurrentStep,
                        DeployRecord::getStartTime, DeployRecord::getEndTime)
                .eq(projectId != null, DeployRecord::getProjectId, projectId)
                .like(projectName != null && !projectName.isBlank(), DeployRecord::getProjectName, projectName)
                .eq(status != null && !status.isBlank(), DeployRecord::getStatus, status)
                .orderByDesc(DeployRecord::getId);
        Page<DeployRecord> pageResult = recordMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return BaseResponse.ok(PageResult.of(pageResult.getTotal(), page, pageSize, pageResult.getRecords()));
    }

    /**
     * 单条部署记录详情（含完整日志）
     */
    @GetMapping("/records/{id}")
    @RequirePerm(PermissionCode.HISTORY_QUERY)
    public BaseResponse<DeployRecord> recordDetail(@PathVariable Long id) {
        return BaseResponse.ok(recordMapper.selectById(id));
    }

    /**
     * 根据项目 id 集合返回每个项目的最近一次部署记录（用于项目管理列表展示）
     */
    @GetMapping("/last")
    @RequirePerm(PermissionCode.PROJECT_QUERY)
    public BaseResponse<java.util.Map<Long, DeployRecord>> lastByProjects(@RequestParam java.util.List<Long> projectIds) {
        return BaseResponse.ok(deployService.lastRecordByProjectIds(java.util.Set.copyOf(projectIds)));
    }

    private void checkProjectDeployPerm(Long projectId) {
        User user = UserContext.current();
        if (user == null) throw new ForbiddenException("未登录");
        if (user.isSuperAdmin()) return;
        if (!permissionService.canDeployProject(user.getId(), projectId)) {
            throw new ForbiddenException("无该项目的部署权限");
        }
    }
}
