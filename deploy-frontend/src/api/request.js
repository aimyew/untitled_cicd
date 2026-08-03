import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { logout } from '../utils/perm'

const TOKEN_KEY = 'cc_deploy_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 未登录统一处理：延迟 2 秒后走完整登出流程（清 token + 清 user + 跳登录页）
function handleUnauthorized() {
  if (router.currentRoute.value.path === '/login') return
  ElMessage.warning('登录已过期，请重新登录')
  setTimeout(() => {
    logout()
  }, 2000)
}

/**
 * 系统业务状态码（与后端 ResultCode 对应）
 */
const CODE = {
  SUCCESS: 0,
  UNAUTHORIZED: 1001,
  FORBIDDEN: 1003
}

// HTTP 始终 200，业务状态码由 body.code 表达
request.interceptors.response.use(
  (res) => {
    const r = res.data
    if (r.code === CODE.SUCCESS) {
      return r.data
    }
    if (r.code === CODE.UNAUTHORIZED) {
      handleUnauthorized()
      return Promise.reject(new Error(r.msg))
    }
    if (r.code === CODE.FORBIDDEN) {
      ElMessage.error(r.msg || '无权限访问')
      return Promise.reject(new Error(r.msg))
    }
    // 其他失败（1 通用失败 / 1004 参数错误 / 1005 系统异常 等）
    ElMessage.error(r.msg || '请求失败')
    return Promise.reject(new Error(r.msg))
  },
  (err) => {
    // 这里只处理网络错误 / 超时 / 非 200 状态码等极少数情况
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default request
