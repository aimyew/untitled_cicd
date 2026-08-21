package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 服务器预配置命令
 */
@Data
@TableName("cc_server_command")
public class ServerCommand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serverId;

    private String name;

    private String command;

    private Integer sortOrder;
}
