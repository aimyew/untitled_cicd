<template>
  <router-view v-if="$route.path === '/login'" />
  <el-container v-else class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">🚀 CC 部署系统</div>
      <el-menu router :default-active="$route.path" class="menu">
        <template v-for="item in visibleMenus" :key="item.path">
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <el-icon><component :is="iconComp(item.icon)" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="sub in item.children" :key="sub.path" :index="sub.path">
              <el-icon><component :is="iconComp(sub.icon)" /></el-icon>
              <span>{{ sub.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="iconComp(item.icon)" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
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
import { computed, ref, onMounted, markRaw, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Folder, Monitor, Clock, User as UserIcon, DataLine, Setting, Menu as MenuIcon,
  Document, Files, Picture, VideoPlay, Headset, Promotion, Link, HomeFilled,
  List, Grid, Tickets, Collection, Management, Opportunity, TrendCharts, QuestionFilled
} from '@element-plus/icons-vue'
import { user, logout } from './utils/perm'
import { authApi, menuApi } from './api/auth'

// element-plus 图标组件映射表（后台菜单配置里的图标名 → 实际组件）
const ICON_MAP = {
  Folder, Monitor, Clock, User: UserIcon, DataLine, Setting, Menu: MenuIcon,
  Document, Files, Picture, VideoPlay, Headset, Promotion, Link, HomeFilled,
  List, Grid, Tickets, Collection, Management, Opportunity, TrendCharts, QuestionFilled
}
function iconComp(name) {
  return name && ICON_MAP[name] ? markRaw(ICON_MAP[name]) : markRaw(MenuIcon)
}

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

// 按 parentId 组装树（只保留一级 + 有子的一级）
function buildTree(list) {
  const root = list.filter(m => !m.parentId || m.parentId === 0).map(m => ({ ...m, children: [] }))
  const idMap = new Map(root.map(m => [m.id, m]))
  list.forEach(m => {
    if (m.parentId && idMap.has(m.parentId)) {
      idMap.get(m.parentId).children.push(m)
    }
  })
  return root
}

// 前端路由已注册的 path 集合（避免后台误配一个不存在的路由，前台点了进 404）
const registeredPaths = new Set(useRouter().getRoutes().map(r => r.path))

// 可见菜单（后端已按 user.perms 过滤，前端只过滤前端路由未注册的 path）
const visibleMenus = computed(() => {
  const tree = buildTree(rawMenus.value.filter(m => registeredPaths.has(m.path)))
  return tree.filter(node => node.children.length > 0 || registeredPaths.has(node.path))
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
.menu { border-right: none; background: #001529; }
.menu .el-menu-item { color: #ccc; }
.menu .el-menu-item:hover { background: #112a45; color: #fff; }
.menu .el-menu-item.is-active { background: #1890ff; color: #fff; }
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
