package com.cc.deploy.service;

import com.cc.deploy.auth.UserContext;
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志服务：所有"写操作"都应通过该服务记录一条日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public void log(String action, String targetType, Long targetId, String detail) {
        log(action, targetType, targetId, detail, AuditLog.RESULT_SUCCESS);
    }

    public void log(String action, String targetType, Long targetId, String detail, String result) {
        try {
            AuditLog audit = new AuditLog();
            audit.setUserId(UserContext.currentUserId());
            audit.setUserIp(UserContext.currentIp());
            audit.setAction(action);
            audit.setTargetType(targetType);
            audit.setTargetId(targetId);
            audit.setDetail(truncate(detail, 2000));
            audit.setResult(result);
            audit.setTime(LocalDateTime.now());
            auditLogMapper.insert(audit);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
