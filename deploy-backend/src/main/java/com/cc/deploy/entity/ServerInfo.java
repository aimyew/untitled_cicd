package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部署目标服务器
 */
@Data
@TableName("cc_server")
public class ServerInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String host;

    private Integer port;

    private String username;

    /** AES 加密存储 */
    private String password;

    private String remark;

    @TableField(fill = FieldFill.DEFAULT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.DEFAULT)
    private LocalDateTime updateTime;
}
