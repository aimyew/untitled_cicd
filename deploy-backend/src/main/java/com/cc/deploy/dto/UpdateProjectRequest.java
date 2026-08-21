package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 编辑项目请求
 * <p>注意：敏感字段（localPath, buildCmd, buildProfile, artifactPath, serverId, uploadDir, deployCmd）
 * 仅超管可修改，非超管传入这些字段会被后端忽略
 */
@Data
public class UpdateProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "项目类型不能为空")
    private String type;

    @NotBlank(message = "Git 地址不能为空")
    private String gitUrl;

    private String branch;

    /** 本地目录（仅超管可改） */
    private String localPath;

    /** 构建命令（仅超管可改） */
    @NotBlank(message = "构建命令不能为空")
    private String buildCmd;

    /** Maven 打包 profile（仅超管可改） */
    private String buildProfile;

    /** 产物路径（仅超管可改） */
    @NotBlank(message = "产物路径不能为空")
    private String artifactPath;

    /** 目标服务器（仅超管可改） */
    @NotNull(message = "目标服务器不能为空")
    private Long serverId;

    /** 上传目录（仅超管可改） */
    @NotBlank(message = "上传目录不能为空")
    private String uploadDir;

    /** 部署命令（仅超管可改） */
    private String deployCmd;

    /** 目录下脚本名称 */
    private String scriptName;

    /** 目录下脚本内容 */
    private String scriptContent;
}
