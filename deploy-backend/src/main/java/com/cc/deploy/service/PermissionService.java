package com.cc.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc.deploy.entity.DeployPermission;
import com.cc.deploy.entity.FuncPermDef;
import com.cc.deploy.entity.Menu;
import com.cc.deploy.entity.Permission;
import com.cc.deploy.mapper.DeployPermissionMapper;
import com.cc.deploy.mapper.FuncPermDefMapper;
import com.cc.deploy.mapper.MenuMapper;
import com.cc.deploy.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限服务：查询/授予/回收功能权限 + 项目部署权限
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final DeployPermissionMapper deployPermissionMapper;
    private final FuncPermDefMapper funcPermDefMapper;
    private final MenuMapper menuMapper;

    /**
     * 列出所有功能权限定义（前端弹框用）
     */
    public List<FuncPermDef> listFuncPermDefs() {
        return funcPermDefMapper.selectList(
                new LambdaQueryWrapper<FuncPermDef>().orderByAsc(FuncPermDef::getSortOrder));
    }

    /** 列出全部菜单（按 sort_order 排序） */
    public List<Menu> listMenus() {
        return menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSortOrder));
    }

    /** 是否拥有某个权限码（超管逻辑在调用方或切面判断） */
    public boolean hasPerm(Long userId, String code) {
        return permissionMapper.selectCount(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getUserId, userId)
                .eq(Permission::getPermCode, code)) > 0;
    }

    public Set<String> listPermCodes(Long userId) {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getUserId, userId)).stream()
                .map(Permission::getPermCode)
                .collect(Collectors.toSet());
    }

    public List<Permission> listByUserId(Long userId) {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getUserId, userId));
    }

    /**
     * 全量替换某用户的功能权限（前端传什么就是什么）
     */
    @Transactional
    public void replacePerms(Long userId, Collection<String> codes, Long grantByUserId) {
        permissionMapper.delete(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getUserId, userId));
        if (codes != null) {
            for (String code : codes.stream().distinct().collect(Collectors.toList())) {
                Permission p = new Permission();
                p.setUserId(userId);
                p.setPermCode(code);
                p.setGrantByUserId(grantByUserId);
                p.setGrantTime(LocalDateTime.now());
                permissionMapper.insert(p);
            }
        }
    }

    public List<DeployPermission> listDeployPermsByUserId(Long userId) {
        return deployPermissionMapper.selectList(new LambdaQueryWrapper<DeployPermission>()
                .eq(DeployPermission::getUserId, userId));
    }

    /**
     * 列出某用户有权限的所有项目 ID
     */
    public List<Long> listProjectIdsByUserId(Long userId) {
        return deployPermissionMapper.selectList(new LambdaQueryWrapper<DeployPermission>()
                .eq(DeployPermission::getUserId, userId))
                .stream().map(DeployPermission::getProjectId).toList();
    }

    public boolean canDeployProject(Long userId, Long projectId) {
        return deployPermissionMapper.selectCount(new LambdaQueryWrapper<DeployPermission>()
                .eq(DeployPermission::getUserId, userId)
                .eq(DeployPermission::getProjectId, projectId)) > 0;
    }

    /**
     * 给某用户授权某个项目（已存在则跳过）。
     * 用于：新建项目时自动把创建者加入该项目的白名单
     */
    @Transactional
    public void grantProjectPerm(Long userId, Long projectId, Long grantByUserId) {
        Long cnt = deployPermissionMapper.selectCount(new LambdaQueryWrapper<DeployPermission>()
                .eq(DeployPermission::getUserId, userId)
                .eq(DeployPermission::getProjectId, projectId));
        if (cnt != null && cnt > 0) return;
        DeployPermission dp = new DeployPermission();
        dp.setUserId(userId);
        dp.setProjectId(projectId);
        dp.setGrantByUserId(grantByUserId);
        dp.setGrantTime(LocalDateTime.now());
        deployPermissionMapper.insert(dp);
    }

    /**
     * 全量替换某用户的项目部署权限
     */
    @Transactional
    public void replaceDeployPerms(Long userId, Collection<Long> projectIds, Long grantByUserId) {
        deployPermissionMapper.delete(new LambdaQueryWrapper<DeployPermission>()
                .eq(DeployPermission::getUserId, userId));
        if (projectIds != null) {
            for (Long pid : projectIds.stream().distinct().collect(Collectors.toList())) {
                DeployPermission dp = new DeployPermission();
                dp.setUserId(userId);
                dp.setProjectId(pid);
                dp.setGrantByUserId(grantByUserId);
                dp.setGrantTime(LocalDateTime.now());
                deployPermissionMapper.insert(dp);
            }
        }
    }
}
