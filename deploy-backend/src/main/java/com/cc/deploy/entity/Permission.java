package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 功能权限：user_id + perm_code 唯一
 */
@Data
@TableName("cc_permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 权限码，如 project:query / deploy:execute */
    private String permCode;

    private Long grantByUserId;

    private LocalDateTime grantTime;
}
