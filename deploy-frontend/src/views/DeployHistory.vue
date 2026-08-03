<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-form :inline="true" @submit.prevent="onSearch" class="search-form">
        <el-form-item>
          <el-select v-model="filterProjectId" placeholder="按项目筛选" clearable style="width: 220px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="projectName" placeholder="项目名模糊" clearable @clear="onSearch"
                    @keyup.enter="onSearch" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="status" placeholder="状态" clearable @clear="onSearch" style="width: 140px">
            <el-option label="部署中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="#" width="70" />
      <el-table-column prop="projectName" label="项目" min-width="140" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'RUNNING'" type="warning" effect="dark">部署中</el-tag>
          <el-tag v-else-if="row.status === 'SUCCESS'" type="success">成功</el-tag>
          <el-tag v-else type="danger">失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentStep" label="步骤" width="110" />
      <el-table-column label="开始时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ duration(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="viewLog(row)">查看日志</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>
  </el-card>

  <LogDialog v-model="logVisible" :record-id="currentRecordId" :mode="logMode"
             :title="`部署日志 - ${currentProjectName}`" @finished="load" />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { deployApi, projectApi } from '../api'
import LogDialog from '../components/LogDialog.vue'

const list = ref([])
const projects = ref([])
const loading = ref(false)
const filterProjectId = ref(null)
const projectName = ref('')
const status = ref('')

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const logVisible = ref(false)
const logMode = ref('static')
const currentRecordId = ref(null)
const currentProjectName = ref('')

async function load() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      pageSize: pageSize.value,
      projectId: filterProjectId.value || undefined,
      projectName: projectName.value || undefined,
      status: status.value || undefined
    }
    const result = await deployApi.records(params)
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function resetSearch() {
  filterProjectId.value = null
  projectName.value = ''
  status.value = ''
  page.value = 1
  load()
}

function viewLog(row) {
  currentRecordId.value = row.id
  currentProjectName.value = row.projectName
  // 进行中的看实时日志，已结束的看归档日志
  logMode.value = row.status === 'RUNNING' ? 'live' : 'static'
  logVisible.value = true
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

function duration(row) {
  if (!row.startTime || !row.endTime) return '-'
  const seconds = Math.round((new Date(row.endTime) - new Date(row.startTime)) / 1000)
  return seconds >= 60 ? `${Math.floor(seconds / 60)}分${seconds % 60}秒` : `${seconds}秒`
}

onMounted(async () => {
  try {
    const r = await projectApi.list({ pageSize: 10000 })
    projects.value = r.records || []
  } catch (e) {
    projects.value = []
  }
  load()
})
</script>

<style scoped>
.toolbar { margin-bottom: 14px; }
.search-form { margin-bottom: 0; }
.pagination-wrap { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
