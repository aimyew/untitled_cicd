package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 编辑菜单请求（只允许修改：标题、图标、权限码、排序）
 * 注意：type、path、parentId 不允许编辑
 */
@Data
public class UpdateMenuRequest {

    /** 菜单标题 */
    @NotBlank(message = "菜单标题不能为空")
    private String title;

    /** 图标名（可清空为 null） */
    private String icon;

    /** 权限码（可清空为 null） */
    private String permCode;

    /** 排序 */
    private Integer sortOrder;
}
