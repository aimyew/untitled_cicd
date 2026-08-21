package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增项目请求
 */
@Data
public class CreateProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "项目类型不能为空")
    private String type;

    @NotBlank(message = "Git 地址不能为空")
    private String gitUrl;

    /** 分支，默认 dev */
    private String branch;

    /** 本地目录，留空则用全局工作区 + 项目名 */
    private String localPath;

    @NotBlank(message = "构建命令不能为空")
    private String buildCmd;

    /** Maven 打包 profile */
    private String buildProfile;

    @NotBlank(message = "产物路径不能为空")
    private String artifactPath;

    @NotNull(message = "目标服务器不能为空")
    private Long serverId;

    @NotBlank(message = "上传目录不能为空")
    private String uploadDir;

    /** 部署命令，可空 */
    private String deployCmd;

    /** 目录下脚本名称 */
    private String scriptName;

    /** 目录下脚本内容 */
    private String scriptContent;
}
