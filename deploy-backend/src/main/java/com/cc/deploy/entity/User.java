package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户：IP 是登录名，首次访问自动创建为 DISABLED
 */
@Data
@TableName("cc_user")
public class User {

    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_USER = "USER";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ip;

    private String nickname;

    /** AES 加密存储 */
    private String password;

    /** SUPER_ADMIN / USER */
    private String role;

    /** ENABLED / DISABLED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public boolean isSuperAdmin() {
        return ROLE_SUPER_ADMIN.equals(role);
    }

    public boolean isEnabled() {
        return STATUS_ENABLED.equals(status);
    }
}
