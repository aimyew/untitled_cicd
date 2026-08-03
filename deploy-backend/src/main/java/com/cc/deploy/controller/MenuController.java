package com.cc.deploy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc.deploy.auth.Auth;
import com.cc.deploy.auth.PermissionCode;
import com.cc.deploy.auth.RequirePerm;
import com.cc.deploy.auth.UserContext;
import com.cc.deploy.common.BaseResponse;
import com.cc.deploy.entity.AuditLog;
import com.cc.deploy.entity.Menu;
import com.cc.deploy.entity.User;
import com.cc.deploy.mapper.MenuMapper;
import com.cc.deploy.service.AuditService;
import com.cc.deploy.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理（仅超管可调用）
 */
@RestController
@RequestMapping("/api/menus")
@Auth
@RequiredArgsConstructor
public class MenuController {

    private final PermissionService permissionService;
    private final MenuMapper menuMapper;
    private final AuditService auditService;

    /** 列出全部菜单（扁平结构，前端按 parentId 组装树） */
    @GetMapping
    @RequirePerm(PermissionCode.MENU_MANAGE)
    public BaseResponse<List<Menu>> list() {
        return BaseResponse.ok(permissionService.listMenus());
    }

    /**
     * 当前登录用户可见菜单（按 user.perms 过滤）：
     * - 超管：返回全部菜单
     * - 普通用户：返回 permCode 在 user.perms 里的菜单；permCode 为空的菜单视为"所有人可见"
     */
    @GetMapping("/current-visible")
    public BaseResponse<List<Menu>> currentVisible() {
        List<Menu> all = permissionService.listMenus();
        User user = UserContext.current();
        if (user == null) return BaseResponse.ok(java.util.Collections.emptyList());
        if (user.isSuperAdmin()) return BaseResponse.ok(all);
        java.util.Set<String> perms = permissionService.listPermCodes(user.getId());
        List<Menu> filtered = all.stream()
                .filter(m -> {
                    String pc = m.getPermCode();
                    if (pc == null || pc.isBlank()) return true; // permCode 为空 = 所有人可见
                    return perms != null && perms.contains(pc);
                })
                .toList();
        return BaseResponse.ok(filtered);
    }

    /** 新增/编辑 */
    @PostMapping
    @RequirePerm(PermissionCode.MENU_MANAGE)
    public BaseResponse<Void> save(@RequestBody Menu menu) {
        Assert.hasText(menu.getPath(), "路由路径不能为空");
        Assert.hasText(menu.getTitle(), "菜单标题不能为空");
        if (menu.getParentId() == null) menu.setParentId(0L);
        if (menu.getSortOrder() == null) menu.setSortOrder(0);

        boolean isNew = menu.getId() == null;
        if (isNew) {
            menuMapper.insert(menu);
            auditService.log("MENU_ADD", AuditLog.TARGET_SERVER, menu.getId(),
                    "path=" + menu.getPath());
        } else {
            menuMapper.updateById(menu);
            auditService.log("MENU_EDIT", AuditLog.TARGET_SERVER, menu.getId(),
                    "path=" + menu.getPath());
        }
        return BaseResponse.ok();
    }

    /** 删除（连带删除子菜单） */
    @DeleteMapping("/{id}")
    @RequirePerm(PermissionCode.MENU_MANAGE)
    public BaseResponse<Void> delete(@PathVariable Long id) {
        menuMapper.delete(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, id));
        menuMapper.deleteById(id);
        auditService.log("MENU_DELETE", AuditLog.TARGET_SERVER, id, "id=" + id);
        return BaseResponse.ok();
    }
}
