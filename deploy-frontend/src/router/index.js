import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/request'
import { user, initUser } from '../utils/perm'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { guestOnly: true }
  },
  { path: '/', redirect: '/projects' },
  {
    path: '/projects', name: 'projects',
    component: () => import('../views/ProjectList.vue'),
    meta: { title: '项目管理', perm: 'project:query' }
  },
  {
    path: '/servers', name: 'servers',
    component: () => import('../views/ServerList.vue'),
    meta: { title: '服务器管理', perm: 'server:query' }
  },
  {
    path: '/history', name: 'history',
    component: () => import('../views/DeployHistory.vue'),
    meta: { title: '部署历史', perm: 'history:query' }
  },
  {
    path: '/users', name: 'users',
    component: () => import('../views/UserManagement.vue'),
    meta: { title: '用户管理', perm: 'user:manage' }
  },
  {
    path: '/audit', name: 'audit',
    component: () => import('../views/AuditLog.vue'),
    meta: { title: '审计日志', perm: 'audit:view' }
  },
  {
    path: '/menus', name: 'menus',
    component: () => import('../views/MenuManage.vue'),
    meta: { title: '菜单管理', perm: 'menu:manage' }
  },
  {
    path: '/no-perm',
    name: 'no-perm',
    component: () => import('../views/NoPerm.vue'),
    meta: { title: '无权限' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 找出用户"第一个有权限访问的路由"，用于无权限时给他一个落地页。
 * 返回 null 表示没有任何可访问的菜单。
 */
function findFirstAllowedRoute(u) {
  if (!u) return null
  const candidates = [
    { path: '/projects', perm: 'project:query' },
    { path: '/servers', perm: 'server:query' },
    { path: '/history', perm: 'history:query' },
    { path: '/users', perm: 'user:manage' },
    { path: '/menus', perm: 'menu:manage' },
    { path: '/audit', perm: 'audit:view' }
  ]
  for (const c of candidates) {
    if (u.isSuperAdmin === true || (u.perms && u.perms.includes(c.perm))) {
      return c.path
    }
  }
  return null
}

router.beforeEach(async (to) => {
  // 无权限页本身不需要鉴权
  if (to.path === '/no-perm') return true

  // 未初始化过 user 时拉一次
  if (!user.value) await initUser()

  // 未登录 → 除登录页外全部跳登录
  if (!user.value) {
    if (to.path === '/login') return true
    return { path: '/login', query: { from: to.fullPath } }
  }

  // 已登录访问登录页 → 直接跳到用户有权限的第一个页面（避免回到没权限的首页）
  if (to.path === '/login') {
    const landing = findFirstAllowedRoute(user.value) || '/no-perm'
    return { path: landing }
  }

  // / 是个 redirect 路由（→ /projects），如果用户没 project:query 权限就会死循环，
  // 所以在 redirect 生效前主动替换成用户有权限的页面
  if (to.path === '/') {
    const landing = findFirstAllowedRoute(user.value) || '/no-perm'
    return { path: landing, replace: true }
  }

  // 权限码校验（超管自动放行）
  if (to.meta.perm) {
    const hasCode = user.value.isSuperAdmin === true ||
      (user.value.perms && user.value.perms.includes(to.meta.perm))
    if (!hasCode) {
      // 没权限：跳无权限页（不再跳 / 避免死循环）
      return { path: '/no-perm', query: { wanted: to.fullPath, required: to.meta.perm } }
    }
  }
  return true
})

// 兜底：如果 localStorage 有 token 但 /me 失败了（比如后端重启导致 token 失效），
// 这里强制清一次，让守卫下次重新走未登录逻辑
router.beforeResolve(async () => {
  if (getToken() && !user.value) {
    await initUser()
  }
})

export default router
