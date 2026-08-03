package com.cc.deploy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 部署系统全局配置
 */
@Data
@ConfigurationProperties(prefix = "deploy")
public class DeployProperties {

    /** 项目未指定本地目录时，统一 clone 到该工作区 */
    private String workspace;

    /** 服务器密码 AES 加密密钥（16位） */
    private String aesKey;

    /** 登录 token 有效期（秒），默认 8 小时 */
    private long tokenExpireSeconds = 28800;

    /** 超级管理员 IP，启动时自动创建用户 */
    private String superAdminIp = "10.10.12.5";
}
