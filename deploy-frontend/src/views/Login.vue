<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="hover">
      <template #header>
        <div class="login-title">🚀 CC 部署系统 · 登录</div>
      </template>
      <el-form :model="form" label-width="80px" @submit.prevent="handleLogin">
        <el-form-item label="访问IP">
          <el-input v-model="form.ip" placeholder="点击右侧按钮自动填充">
            <template #append>
              <el-button :loading="ipLoading" @click="autoFillIp">获取本机 IP</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password
                    placeholder="请输入密码" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'
import { onLoginSuccess } from '../utils/perm'
import request from '../api/request'

const route = useRoute()
const loading = ref(false)
const ipLoading = ref(false)
const form = ref({ ip: '', password: '' })

onMounted(() => {
  autoFillIp()
})

async function autoFillIp() {
  ipLoading.value = true
  try {
    const ip = await request.get('/auth/client-ip')
    if (ip) form.value.ip = ip
  } catch (e) { /* 忽略：用户可以手动输入 */ } finally {
    ipLoading.value = false
  }
}

async function handleLogin() {
  if (!form.value.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (!form.value.ip) {
    ElMessage.warning('请输入本机 IP（如 10.10.12.101）')
    return
  }
  loading.value = true
  try {
    const result = await authApi.login({ ip: form.value.ip, password: form.value.password })
    ElMessage.success('登录成功')
    onLoginSuccess(result, route.query.from || '/')
  } catch (e) { /* 拦截器已处理 */ } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card { width: 420px; }
.login-title { text-align: center; font-size: 18px; font-weight: 600; }
</style>
