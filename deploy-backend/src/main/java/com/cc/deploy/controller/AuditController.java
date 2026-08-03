package com.cc.deploy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc.deploy.auth.Auth;
import com.cc.deploy.auth.PermissionCode;
import com.cc.deploy.auth.RequirePerm;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询（仅超管）
 */
@RestController
@RequestMapping("/api/audit-logs")
@Auth
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogMapper auditLogMapper;

    @GetMapping
    @RequirePerm(PermissionCode.AUDIT_VIEW)
    public BaseResponse<Page<AuditLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userIp,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(userIp != null && !userIp.isBlank(), AuditLog::getUserIp, userIp)
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .eq(targetType != null && !targetType.isBlank(), AuditLog::getTargetType, targetType)
                .orderByDesc(AuditLog::getId);
        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return BaseResponse.ok(result);
    }
}
