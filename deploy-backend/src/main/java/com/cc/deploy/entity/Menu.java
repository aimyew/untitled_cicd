package com.cc.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 菜单配置（前端左侧菜单，后台可维护）
 */
@Data
@TableName("cc_menu")
public class Menu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单id，0表示一级菜单 */
    private Long parentId;

    /** 前端路由路径 */
    private String path;

    /** 菜单标题 */
    private String title;

    /** 图标名（element-plus 图标组件名） */
    private String icon;

    /** 所需权限码（空=超管专属或所有人可见） */
    private String permCode;

    /** 排序 */
    private Integer sortOrder;
}
