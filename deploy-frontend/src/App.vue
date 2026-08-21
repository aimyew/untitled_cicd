<template>
  <router-view v-if="$route.path === '/login'" />
  <el-container v-else class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">🚀 CC 部署系统</div>
      <el-menu router :default-active="$route.path" class="menu">
        <MenuItem v-for="item in visibleMenus" :key="item.path" :menu="item" />
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ $route.meta.title }}</div>
        <div class="header-user">
          <el-dropdown trigger="click" @command="onCommand">
            <span class="user-label">
              <el-icon><User /></el-icon>
              {{ userLabel }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 修改密码弹框 -->
  <el-dialog v-model="pwdVisible" title="修改密码" width="420px" :close-on-click-modal="false">
    <el-form :model="pwdForm" label-width="90px">
      <el-form-item label="旧密码">
        <el-input v-model="pwdForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="pwdForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="pwdForm.confirm" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdSaving" @click="submitPwd">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, ArrowDown } from '@element-plus/icons-vue'
import MenuItem from './components/MenuItem.vue'
import { user, logout } from './utils/perm'
import { authApi, menuApi } from './api/auth'

// 从后台拉取的菜单原始数据（扁平结构）
const rawMenus = ref([])
async function loadMenus() {
  try {
    rawMenus.value = (await menuApi.currentVisible()) || []
  } catch (e) {
    rawMenus.value = []
  }
}
// 仅当"用户真正登录/切换"时触发（user.id 从 null → 有值，或从一个 id 变成另一个 id）
// 避免在登录页 onMounted 时（还没 token）就发请求导致 1001
watch(() => user.value?.id, (id, oldId) => {
  if (id && id !== oldId) loadMenus()
}, { immediate: true })
// 兜底：F5 刷新时 user 可能已由 router.beforeEach 里的 initUser 设好，
// 但 watch 的 immediate 已经触发过，这里不需要再调

// 按 parentId 递归组装 N 级树
function buildTree(list, parentId = 0) {
  return list
    .filter(m => (m.parentId || 0) === parentId)
    .map(m => ({ ...m, children: buildTree(list, m.id) }))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
}

// 前端路由已注册的 path 集合（避免后台误配一个不存在的路由，前台点了进 404）
const registeredPaths = new Set(useRouter().getRoutes().map(r => r.path))

// 可见菜单（后端已按 user.perms 过滤）
// GROUP 菜单：只要有有效子菜单就显示
// LINK 菜单：自身 path 必须已在前端路由注册
const visibleMenus = computed(() => {
  function filter(node) {
    const validChildren = (node.children || []).map(filter).filter(Boolean)
    if (validChildren.length > 0) {
      return { ...node, children: validChildren }
    }
    return node.type === 'LINK' && registeredPaths.has(node.path) ? { ...node, children: [] } : null
  }
  return buildTree(rawMenus.value).map(filter).filter(Boolean)
})

const userLabel = computed(() => {
  if (!user.value) return ''
  return (user.value.nickname || user.value.ip) + (user.value.isSuperAdmin ? ' (超管)' : '')
})

// 修改密码
const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })

function onCommand(cmd) {
  if (cmd === 'logout') logout()
  if (cmd === 'password') {
    pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
    pwdVisible.value = true
  }
}

async function submitPwd() {
  const { oldPassword, newPassword, confirm } = pwdForm.value
  if (!oldPassword || !newPassword) return ElMessage.warning('请填写完整')
  if (newPassword !== confirm) return ElMessage.error('两次密码不一致')
  pwdSaving.value = true
  try {
    await authApi.changePassword({ oldPassword, newPassword })
    ElMessage.success('密码已修改，请重新登录')
    pwdVisible.value = false
    logout()
  } catch (e) { /* 拦截器已提示 */ } finally {
    pwdSaving.value = false
  }
}
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
.layout { height: 100%; }
.aside { background: #001529; }
.logo {
  height: 60px; line-height: 60px; text-align: center;
  color: #fff; font-size: 16px; font-weight: bold;
}
.menu {
  border-right: none;
  /* Element Plus 菜单主题色变量，所有层级（含嵌套）统一生效 */
  --el-menu-text-color: #ccc;
  --el-menu-hover-text-color: #fff;
  --el-menu-active-color: #fff;
  --el-menu-bg-color: #001529;
  --el-menu-hover-bg-color: #112a45;
  background: #001529;
}
/* 强制所有层级菜单项与分组标题颜色，避免嵌套时被覆盖成白色 */
.menu .el-menu-item,
.menu .el-sub-menu__title { color: #ccc !important; }
.menu .el-menu-item:hover,
.menu .el-sub-menu__title:hover { color: #fff !important; }
.menu .el-menu-item.is-active { background: #1890ff !important; color: #fff !important; }
.menu .el-sub-menu.is-active > .el-sub-menu__title { color: #fff !important; }
/* 强制嵌套子菜单容器与菜单项背景统一，覆盖 Element Plus 默认白底/黑底 */
.menu,
.menu .el-menu--inline,
.menu .el-sub-menu .el-menu,
.menu .el-menu-item,
.menu .el-sub-menu__title { background-color: #001529 !important; }
.header {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 18px; font-weight: 600;
  border-bottom: 1px solid #e8e8e8; background: #fff;
}
.header-title { flex: 1; }
.header-user { font-size: 14px; font-weight: normal; }
.user-label {
  display: inline-flex; align-items: center; gap: 4px;
  cursor: pointer; color: #606266;
}
.main { background: #f0f2f5; }
</style>
