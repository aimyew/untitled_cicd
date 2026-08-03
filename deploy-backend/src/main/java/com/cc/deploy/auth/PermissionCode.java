package com.cc.deploy.auth;

/**
 * 权限码常量：所有前后端约定的权限码集中在这里维护
 */
public final class PermissionCode {

    private PermissionCode() {}

    // --- 项目 ---
    public static final String PROJECT_QUERY  = "project:query";
    public static final String PROJECT_ADD    = "project:add";
    public static final String PROJECT_EDIT   = "project:edit";
    public static final String PROJECT_DELETE = "project:delete";
    /** 部署按钮的全局开关（项目维度白名单在 cc_deploy_permission 表） */
    public static final String PROJECT_DEPLOY = "project:deploy";

    // --- 服务器 ---
    public static final String SERVER_QUERY  = "server:query";
    public static final String SERVER_ADD    = "server:add";
    public static final String SERVER_EDIT   = "server:edit";
    public static final String SERVER_DELETE = "server:delete";

    // --- 部署历史 ---
    public static final String HISTORY_QUERY = "history:query";

    // --- 用户管理 ---
    public static final String USER_MANAGE = "user:manage";
    public static final String MENU_MANAGE = "menu:manage";

    // --- 审计日志 ---
    public static final String AUDIT_VIEW = "audit:view";
}
