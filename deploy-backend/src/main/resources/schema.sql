DROP TABLE IF EXISTS `cc_audit_log`,`cc_deploy_permission`,`cc_deploy_record`,`cc_func_perm_def`,`cc_menu`,`cc_permission`,`cc_project`,`cc_server`,`cc_user`;

CREATE TABLE IF NOT EXISTS cc_audit_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT COMMENT '用户id, 未登录时为空',
    user_ip      VARCHAR(64) NOT NULL COMMENT '请求IP',
    action       VARCHAR(64) NOT NULL COMMENT '动作',
    target_type  VARCHAR(32) COMMENT '目标类型: SERVER/PROJECT/USER/DEPLOY',
    target_id    BIGINT COMMENT '目标id',
    detail       VARCHAR(2000) COMMENT '操作细节(JSON)',
    result       VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果: SUCCESS/FAIL',
    time         DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审计日志';

CREATE TABLE IF NOT EXISTS cc_deploy_permission (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL COMMENT '用户id',
    project_id         BIGINT NOT NULL COMMENT '项目id',
    grant_by_user_id   BIGINT COMMENT '授权人id',
    grant_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_project (user_id, project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='项目部署权限';

CREATE TABLE IF NOT EXISTS cc_deploy_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT      NOT NULL COMMENT '项目id',
    project_name VARCHAR(64) COMMENT '项目名称(冗余)',
    status       VARCHAR(16) NOT NULL COMMENT '状态: RUNNING/SUCCESS/FAILED',
    current_step VARCHAR(64) COMMENT '当前步骤',
    log          LONGTEXT COMMENT '完整日志',
    start_time   DATETIME COMMENT '开始时间',
    end_time     DATETIME COMMENT '结束时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='部署记录';

CREATE TABLE IF NOT EXISTS cc_func_perm_def (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_code   VARCHAR(64)  NOT NULL UNIQUE COMMENT '权限码',
    title       VARCHAR(64)  NOT NULL COMMENT '前端展示名',
    description VARCHAR(255) COMMENT '描述',
    perm_type   VARCHAR(16)  NOT NULL DEFAULT 'DEPLOY' COMMENT '权限类型: DEPLOY(部署权限)/SYSTEM(系统权限)',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='功能权限定义';

-- 初始化功能权限定义（INSERT IGNORE 保证幂等）
INSERT IGNORE INTO cc_func_perm_def (perm_code, title, description, perm_type, sort_order) VALUES
('project:query',      '项目-查询',     '可打开项目管理页面',                       'DEPLOY', 10),
('project:add',        '项目-新增',     '可新增项目',                               'DEPLOY', 20),
('project:edit',       '项目-编辑',     '可编辑项目',                               'DEPLOY', 30),
('project:delete',     '项目-删除',     '可删除项目（仅超管可删除，此权限不生效）', 'DEPLOY', 40),
('project:deploy',     '项目-部署',     '可对授权项目触发部署按钮',                 'DEPLOY', 50),
('server:query',       '服务器-查询',   '可打开服务器管理页面',                     'DEPLOY', 60),
('server:add',         '服务器-新增',   '可新增服务器',                             'DEPLOY', 70),
('server:edit',        '服务器-编辑',   '可编辑服务器',                             'DEPLOY', 80),
('server:delete',      '服务器-删除',   '可删除服务器（仅超管可删除，此权限不生效）', 'DEPLOY', 90),
('history:query',      '部署历史-查询', '可查看部署历史及日志',                     'DEPLOY', 100),
('user:manage',        '用户管理',      '可管理用户（增删改查、分配权限）',         'SYSTEM', 110),
('menu:manage',        '菜单管理',      '可管理菜单（增删改查）',                   'SYSTEM', 120),
('audit:view',         '审计日志',      '可查看审计日志',                           'SYSTEM', 130);

CREATE TABLE IF NOT EXISTS cc_menu (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单id，0表示一级菜单',
    path        VARCHAR(128) NOT NULL COMMENT '前端路由路径',
    title       VARCHAR(64)  NOT NULL COMMENT '菜单标题',
    icon        VARCHAR(64)  COMMENT '图标名（element-plus 图标组件名）',
    perm_code   VARCHAR(64)  COMMENT '所需权限码（空=超管专属或所有人可见）',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    UNIQUE KEY uk_path (path)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜单配置';

-- 初始化菜单（INSERT IGNORE 保证幂等）
INSERT IGNORE INTO cc_menu (parent_id, path, title, icon, perm_code, sort_order) VALUES
(0, '/projects', '项目管理',   'Folder',   'project:query',      10),
(0, '/servers',  '服务器管理', 'Monitor',  'server:query',       20),
(0, '/history',  '部署历史',   'Clock',    'history:query',      30),
(0, '/users',    '用户管理',   'User',     'user:manage',   40),
(0, '/menus',    '菜单管理',   'Setting',  'menu:manage',   50),
(0, '/audit',    '审计日志',   'DataLine', 'audit:view',         60);

CREATE TABLE IF NOT EXISTS cc_permission (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL COMMENT '用户id',
    perm_code          VARCHAR(64) NOT NULL COMMENT '权限码, 如 project:query',
    grant_by_user_id   BIGINT COMMENT '授权人id',
    grant_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_perm (user_id, perm_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='功能权限';

CREATE TABLE IF NOT EXISTS cc_project (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL COMMENT '项目名称',
    type          VARCHAR(16)  NOT NULL COMMENT '类型: JAVA/VUE',
    git_url       VARCHAR(255) NOT NULL COMMENT 'Git仓库地址',
    branch        VARCHAR(64)  NOT NULL DEFAULT 'dev' COMMENT '部署分支',
    local_path    VARCHAR(255) COMMENT '本地目录，留空则用全局工作区+项目名',
    build_cmd     VARCHAR(255) NOT NULL COMMENT '构建命令',
    build_profile VARCHAR(32) COMMENT 'Maven打包profile，如 test/prod，留空用默认',
    artifact_path VARCHAR(255) NOT NULL COMMENT '产物相对路径，支持*通配，如 target/*.jar、dist',
    server_id     BIGINT       NOT NULL COMMENT '目标服务器id',
    upload_dir    VARCHAR(255) NOT NULL COMMENT '服务器上传目录',
    deploy_cmd    VARCHAR(512) COMMENT '上传后远程执行的部署命令，可空',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='部署项目';

CREATE TABLE IF NOT EXISTS cc_server (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL COMMENT '服务器名称',
    host        VARCHAR(128) NOT NULL COMMENT 'IP或域名',
    port        INT          NOT NULL DEFAULT 22 COMMENT 'SSH端口',
    username    VARCHAR(64)  NOT NULL COMMENT 'SSH用户名',
    password    VARCHAR(512) NOT NULL COMMENT 'SSH密码(AES加密)',
    remark      VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_host_port (host, port)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='部署目标服务器';

CREATE TABLE IF NOT EXISTS cc_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip            VARCHAR(64)  NOT NULL UNIQUE COMMENT '用户IP(登录名)',
    nickname      VARCHAR(64) COMMENT '昵称',
    password      VARCHAR(512) NOT NULL COMMENT '密码(AES加密)',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色: SUPER_ADMIN/USER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'DISABLED' COMMENT '状态: ENABLED/DISABLED',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ip (ip)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户';
