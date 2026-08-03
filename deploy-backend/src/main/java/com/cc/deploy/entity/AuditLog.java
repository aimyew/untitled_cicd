package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志
 */
@Data
@TableName("cc_audit_log")
public class AuditLog {

    public static final String TARGET_SERVER = "SERVER";
    public static final String TARGET_PROJECT = "PROJECT";
    public static final String TARGET_USER = "USER";
    public static final String TARGET_DEPLOY = "DEPLOY";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userIp;

    private String action;

    /** SERVER / PROJECT / USER / DEPLOY */
    private String targetType;

    private Long targetId;

    /** 细节 JSON */
    private String detail;

    /** SUCCESS / FAIL */
    private String result;

    private LocalDateTime time;
}
