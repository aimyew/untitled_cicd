package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目部署权限：user_id + project_id 唯一
 */
@Data
@TableName("cc_deploy_permission")
public class DeployPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long projectId;

    private Long grantByUserId;

    private LocalDateTime grantTime;
}
