<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="filters.userIp" placeholder="用户 IP" style="width: 160px" clearable />
      <el-select v-model="filters.action" placeholder="操作" clearable style="width: 180px">
        <el-option label="登录 LOGIN" value="LOGIN" />
        <el-option label="新增服务器 SERVER_ADD" value="SERVER_ADD" />
        <el-option label="编辑服务器 SERVER_EDIT" value="SERVER_EDIT" />
        <el-option label="删除服务器 SERVER_DELETE" value="SERVER_DELETE" />
        <el-option label="新增项目 PROJECT_ADD" value="PROJECT_ADD" />
        <el-option label="编辑项目 PROJECT_EDIT" value="PROJECT_EDIT" />
        <el-option label="删除项目 PROJECT_DELETE" value="PROJECT_DELETE" />
        <el-option label="触发部署 DEPLOY" value="DEPLOY" />
        <el-option label="用户状态变更 USER_STATUS" value="USER_STATUS" />
        <el-option label="重置密码 USER_RESET_PASSWORD" value="USER_RESET_PASSWORD" />
        <el-option label="权限配置 USER_PERMISSIONS" value="USER_PERMISSIONS" />
      </el-select>
      <el-select v-model="filters.targetType" placeholder="目标类型" clearable style="width: 150px">
        <el-option label="服务器" value="SERVER" />
        <el-option label="项目" value="PROJECT" />
        <el-option label="用户" value="USER" />
        <el-option label="部署" value="DEPLOY" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="time" label="时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.time) }}</template>
      </el-table-column>
      <el-table-column prop="userIp" label="操作人 IP" width="150" />
      <el-table-column prop="action" label="操作" width="200" />
      <el-table-column label="目标" width="150">
        <template #default="{ row }">
          {{ row.targetType || '-' }}
          <span v-if="row.targetId">#{{ row.targetId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="详情" min-width="300" show-overflow-tooltip />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.result === 'SUCCESS'" type="success" size="small">成功</el-tag>
          <el-tag v-else type="danger" size="small">失败</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, sizes, prev, pager, next"
                     :total="total" v-model:current-page="page" v-model:page-size="pageSize"
                     :page-sizes="[20, 50, 100]" @current-change="load" @size-change="load" />
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { auditApi } from '../api/auth'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ userIp: '', action: '', targetType: '' })

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (filters.userIp) params.userIp = filters.userIp
    if (filters.action) params.action = filters.action
    if (filters.targetType) params.targetType = filters.targetType
    const p = await auditApi.page(params)
    list.value = p.records || []
    total.value = p.total || 0
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.userIp = ''
  filters.action = ''
  filters.targetType = ''
  page.value = 1
  load()
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 19)
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
