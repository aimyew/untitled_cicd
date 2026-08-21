# PROJECT KNOWLEDGE

> 本文件用于 AI 快速了解项目当前结构。不记录改造历史，只维护最新状态；每次变更后直接更新对应章节。

## 1. 项目概览

- **根路径**: `d:/IdeaReposG/untitled_cicd`
- **包含模块**:
  - `deploy-backend`: Spring Boot 3 + MyBatis-Plus + MySQL 后端
  - `deploy-frontend`: Vue 3 + Vite + Element Plus 前端
  - `deploy_workspace`: 业务项目克隆目录（不记录在本文件）
- **数据库**: MySQL, 库名 `vue_bag`
- **后端端口**: 8080
- **SQL 初始化**:
  - `deploy-backend/src/main/resources/schema.sql`: 新环境建表 + INSERT IGNORE
  - `deploy-backend/src/main/resources/schemaExec.sql`: 开发环境每次启动 TRUNCATE + 初始化默认值
  - 配置: `spring.sql.init.schema-locations: classpath:schemaExec.sql`, `mode: always`, `continue-on-error: true`

## 2. 目录结构

### deploy-backend
```
src/main/java/com/cc/deploy/
  auth/               # 认证、权限注解、Token、拦截器
  common/             # 统一响应、异常、分页
  config/             # 配置类
  controller/         # REST API
  entity/             # MyBatis-Plus 实体
  init/               # 超管初始化
  mapper/             # MyBatis-Plus Mapper
  service/            # 业务逻辑
  util/               # SSH/SFTP/Command/Zip/AES 工具
  websocket/          # 部署日志 WebSocket
  DeployApplication.java
src/main/resources/
  application.yml     # 配置: 端口、数据源、SQL init、workspace、AES key
  schema.sql          # 建表语句
  schemaExec.sql      # 开发环境初始化数据
```

### deploy-frontend
```
src/
  api/                # axios 封装 + API 定义
  components/         # LogDialog.vue, MenuItem.vue
  router/index.js     # 前端路由（硬编码）
  utils/perm.js       # 权限工具、用户信息
  views/              # 页面组件
  App.vue             # 主布局 + 侧边栏菜单
  main.js             # 应用入口，全局注册所有 Element Plus 图标
```

## 3. 关键文件索引

### 后端核心
| 文件 | 职责 |
|------|------|
| `controller/AuthController.java` | 登录 / 当前用户 / 修改密码 |
| `controller/ProjectController.java` | 项目 CRUD + 列表 VO + 保存校验（含 scriptName/scriptContent 校验） |
| `controller/ServerController.java` | 服务器 CRUD、连接测试 |
| `controller/DeployController.java` | 部署触发、历史记录、最近记录 |
| `controller/UserController.java` | 用户管理、权限分配 |
| `controller/MenuController.java` | 菜单 CRUD（超管）、当前用户可见菜单 |
| `controller/AuditController.java` | 审计日志 |
| `service/DeployService.java` | 部署调度（线程池、并发控制） |
| `service/DeployTask.java` | 单次部署流程：clone → build → artifact → upload → ensure script → remote deploy → cleanup script |
| `service/PermissionService.java` | 功能权限 + 项目部署白名单 + 菜单列表 |
| `entity/Project.java` | 项目实体，字段含 scriptName, scriptContent |
| `entity/Menu.java` | 菜单实体，字段含 type (GROUP/LINK), parentId, path, title, icon, permCode, sortOrder |
| `util/SshUtil.java` | SSH 执行、SFTP 上传 |
| `auth/RequirePerm.java` + `RequireAspect.java` | 方法级权限注解 |

### 前端核心
| 文件 | 职责 |
|------|------|
| `views/ProjectList.vue` | 项目管理：分页、搜索、编辑弹框（含脚本名称/内容输入）、部署触发 |
| `views/ServerList.vue` | 服务器管理 |
| `views/DeployHistory.vue` | 部署历史 |
| `views/UserManagement.vue` | 用户管理、权限分配 |
| `views/MenuManage.vue` | 菜单管理：支持 GROUP/LINK、任意父菜单、层级防环 |
| `views/AuditLog.vue` | 审计日志 |
| `views/Login.vue` | 登录 |
| `App.vue` | 主布局、动态菜单树、深色侧边栏样式 |
| `components/MenuItem.vue` | 递归渲染多级菜单 |
| `components/LogDialog.vue` | 部署日志弹框 |
| `router/index.js` | 路由表（/projects, /servers, /history, /users, /menus, /audit） |
| `api/index.js` | serverApi, projectApi, deployApi |
| `api/auth.js` | authApi, userApi, menuApi, auditApi |
| `utils/perm.js` | hasPerm, canDeploy, canEditProject, initUser, logout |

## 4. 数据库表结构

### `cc_user`
用户表，`ip` 字段同时作为登录账号。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| ip | 登录账号 |
| password | AES 加密后的密码 |
| nickname | 昵称 |
| is_admin | 是否超管 |
| status | 状态 |
| create_time / update_time | 时间戳 |

### `cc_permission`
全局功能权限，按用户 + perm_code 控制。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| user_id | 用户 ID |
| perm_code | 权限码 |

### `cc_func_perm_def`
权限定义表。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| perm_code | 权限码 |
| title | 权限名称 |
| perm_type | 权限类型 |

### `cc_project`
项目配置表。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| name | 项目名称 |
| git_url | Git 仓库地址 |
| branch | 分支 |
| build_command | 构建命令 |
| artifact_path | 产物相对路径 |
| upload_dir | 远程上传目录 |
| deploy_command | 远程部署命令 |
| script_name | 目录下脚本名称（可选） |
| script_content | 目录下脚本内容（可选） |
| project_type | 项目类型（如 VUE / JAVA） |
| server_ids | 目标服务器 ID 列表（逗号分隔） |
| status | 状态 |
| create_time / update_time | 时间戳 |

### `cc_server`
目标服务器表。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| name | 服务器名称 |
| host | 主机地址 |
| port | SSH 端口 |
| username | 用户名 |
| password | AES 加密后的密码 |
| status | 状态 |
| create_time / update_time | 时间戳 |

### `cc_deploy_permission`
项目部署白名单。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| user_id | 用户 ID |
| project_id | 项目 ID |

### `cc_deploy_record`
部署记录表。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| project_id | 项目 ID |
| project_name | 项目名（冗余） |
| server_name | 服务器名（冗余） |
| status | RUNNING / SUCCESS / FAILED |
| log | 日志内容 |
| start_time / end_time | 时间戳 |

### `cc_menu`
菜单表，支持多级分组和页面菜单。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| parent_id | 父菜单 ID，0 表示顶层 |
| type | 菜单类型：`GROUP` 分组、`LINK` 页面 |
| path | 路由路径；GROUP 可填占位路径 |
| title | 菜单标题 |
| icon | Element Plus 图标名称字符串 |
| perm_code | 权限码 |
| sort_order | 排序 |
| create_time / update_time | 时间戳 |

### `cc_audit_log`
审计日志表。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| user_id | 用户 ID |
| username | 用户名 |
| action | 操作类型 |
| detail | 操作详情 |
| ip | 操作 IP |
| create_time | 时间戳 |

## 5. 约定与模式

- **权限模型**: 全局功能权限（cc_permission）+ 项目部署白名单（cc_deploy_permission）
- **分页响应**: `PageResult`（total, page, pageSize, records）
- **菜单过滤**: 后端按 user.perms 过滤；前端 `App.vue.visibleMenus` 再过滤：GROUP 有有效子菜单即显示，LINK 必须 path 在前端路由注册
- **路由注册**: 前端路由硬编码，新页面必须先加 `router/index.js`
- **图标**: `main.js` 全局注册所有 Element Plus 图标，`MenuItem.vue` 直接通过字符串名称渲染
- **部署流程 6 步**: 拉取代码 → 构建（VUE 的 npm install 自动注入 --legacy-peer-deps） → 定位产物 → 上传 → 检查/创建脚本 → 远程部署 → 清理脚本

## 6. 已知问题 / Workaround

- `Setting` 图标在三级菜单嵌套下可能消失，建议分组/页面菜单使用 `SetUp` 等其他图标
- `deploy_workspace` 下的子项目（如 dfmes_fe）是独立 git 仓库，IDEA commit 窗口可能显示其变更，需在 Version Control Directory Mappings 中排除
