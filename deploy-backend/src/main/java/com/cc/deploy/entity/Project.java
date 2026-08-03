package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部署项目
 */
@Data
@TableName("cc_project")
public class Project {

    public static final String TYPE_JAVA = "JAVA";
    public static final String TYPE_VUE = "VUE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** JAVA / VUE */
    private String type;

    private String gitUrl;

    private String branch;

    /** 本地目录，留空则用全局工作区 + 项目名 */
    private String localPath;

    /** 构建命令，如 mvn clean install -DskipTests / npm run build */
    private String buildCmd;

    /** Maven 打包 profile，如 test/prod，留空用项目默认（activeByDefault） */
    private String buildProfile;

    /** 产物相对路径，支持 * 通配，如 target/*.jar、dist */
    private String artifactPath;

    private Long serverId;

    /** 服务器上传目录 */
    private String uploadDir;

    /** 上传后远程执行的部署命令，可空 */
    private String deployCmd;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
