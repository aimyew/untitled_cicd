package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部署记录
 */
@Data
@TableName("cc_deploy_record")
public class DeployRecord {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String projectName;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    private String currentStep;

    /** 完整日志 */
    private String log;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
