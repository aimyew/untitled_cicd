package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增菜单请求
 */
@Data
public class CreateMenuRequest {

    /** 菜单标题 */
    @NotBlank(message = "菜单标题不能为空")
    private String title;

    /** 菜单类型：GROUP / LINK */
    @NotBlank(message = "菜单类型不能为空")
    private String type;

    /** 父菜单id，0表示一级菜单 */
    private Long parentId;

    /** 前端路由路径（LINK 必填）或分组占位路径（GROUP 可空） */
    private String path;

    /** 图标名 */
    private String icon;

    /** 权限码（空=所有人可见） */
    private String permCode;

    /** 排序 */
    private Integer sortOrder;
}
