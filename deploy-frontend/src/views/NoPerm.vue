<template>
  <div class="no-perm-wrap">
    <el-card class="no-perm-card" shadow="hover">
      <el-empty description="暂无访问权限">
        <template #description>
          <div class="no-perm-desc">
            <p>你已成功登录，但没有访问该页面的权限。</p>
            <p v-if="required">所需权限：<el-tag type="danger">{{ required }}</el-tag></p>
            <p v-if="wanted">尝试访问：{{ wanted }}</p>
            <p class="no-perm-hint">请联系超管（10.10.12.5）为你分配权限后再试。</p>
          </div>
        </template>
        <el-button type="primary" @click="back">返回登录</el-button>
        <el-button @click="reload">刷新权限</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { initUser, logout } from '../utils/perm'

const route = useRoute()
const router = useRouter()
const wanted = route.query.wanted || ''
const required = route.query.required || ''

function back() {
  // 先清登录态，再跳登录页（避免被 beforeEach 拦回去）
  logout()
}

async function reload() {
  await initUser()
  router.replace(wanted || '/')
}
</script>

<style scoped>
.no-perm-wrap {
  height: 100vh;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.no-perm-card { width: 520px; }
.no-perm-desc { text-align: center; line-height: 1.8; }
.no-perm-desc .el-tag { margin: 0 4px; }
.no-perm-hint { color: #909399; font-size: 13px; margin-top: 8px; }
</style>
