import { ref, computed } from 'vue'
import { authApi } from '../api/auth'
import { setToken, getToken } from '../api/request'
import router from '../router'

/** 响应式状态：用户 + 权限码列表 */
export const user = ref(null)

export const isLoggedIn = computed(() => !!user.value && !!user.value.id)
export const isSuperAdmin = computed(() => !!user.value && user.value.isSuperAdmin === true)

/** 是否有某个权限码 */
export function hasPerm(code) {
  if (!user.value) return false
  if (user.value.isSuperAdmin) return true
  return !!user.value.perms && user.value.perms.includes(code)
}

/** 是否有某个项目的部署权限 */
export function canDeployProject(projectId) {
  if (!user.value) return false
  if (user.value.isSuperAdmin) return true
  return !!user.value.deployProjectIds && user.value.deployProjectIds.includes(projectId)
}

/** 是否有部署某个项目的整体权限（全局 project:deploy + 项目白名单） */
export function canDeploy(projectId) {
  return hasPerm('project:deploy') && canDeployProject(projectId)
}

/** 是否有编辑某个项目的整体权限（全局 project:edit + 项目白名单） */
export function canEditProject(projectId) {
  return hasPerm('project:edit') && canDeployProject(projectId)
}

/** 初始化：从本地 token 拿当前用户 */
export async function initUser() {
  if (!getToken()) {
    user.value = null
    return
  }
  try {
    const u = await authApi.me()
    user.value = u
  } catch (e) {
    user.value = null
    setToken(null)
  }
}

/** 登录成功后的处理：存 token、设 user、跳回原页面 */
export function onLoginSuccess(result, redirectTo) {
  setToken(result.token)
  user.value = result.user
  router.replace(redirectTo || '/')
}

/** 登出 */
export async function logout() {
  try { await authApi.logout() } catch (e) { /* ignore */ }
  user.value = null
  setToken(null)
  router.push('/login')
}
