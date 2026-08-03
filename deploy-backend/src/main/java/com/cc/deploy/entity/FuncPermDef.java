package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 功能权限定义（前端弹框里的"功能权限"选项列表）
 */
@Data
@TableName("cc_func_perm_def")
public class FuncPermDef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String permCode;

    private String title;

    private String description;

    private String permType;

    private Integer sortOrder;
}
