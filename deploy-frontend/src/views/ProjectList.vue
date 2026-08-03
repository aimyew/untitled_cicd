<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-form :inline="true" @submit.prevent="onSearch" class="search-form">
        <el-form-item>
          <el-input v-model="keyword" placeholder="按项目名搜索" clearable @clear="onSearch"
                    @keyup.enter="onSearch" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
      <div>
        <el-button v-if="$hasPerm('project:add')" type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>&nbsp;新增项目
        </el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="name" label="项目名" min-width="130" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.type === 'JAVA' ? 'primary' : 'success'">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="branch" label="分支" width="90" />
      <el-table-column label="Profile" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.buildProfile" type="warning" size="small">{{ row.buildProfile }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="gitUrl" label="Git 地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="serverName" label="目标服务器" min-width="130" />
      <el-table-column prop="uploadDir" label="上传目录" min-width="160" show-overflow-tooltip />
      <el-table-column label="上次开始" min-width="135">
        <template #default="{ row }">{{ lastField(row.id, 'startTime') }}</template>
      </el-table-column>
      <el-table-column label="上次结束" min-width="135">
        <template #default="{ row }">{{ lastField(row.id, 'endTime') }}</template>
      </el-table-column>
      <el-table-column label="上次状态" width="95">
        <template #default="{ row }">
          <template v-if="lastOf(row.id)">
            <el-tag v-if="lastOf(row.id).status === 'RUNNING'" type="warning" size="small" effect="dark">部署中</el-tag>
            <el-tag v-else-if="lastOf(row.id).status === 'SUCCESS'" type="success" size="small">成功</el-tag>
            <el-tag v-else type="danger" size="small">失败</el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button v-if="$canDeploy(row.id)" size="small" type="success"
                     :loading="deployingId === row.id" @click="deploy(row)">
            {{ deployingId === row.id ? '部署中' : '部 署' }}
          </el-button>
          <el-button v-if="$canEditProject(row.id)" size="small" type="primary"
                     @click="openDialog(row)">编辑</el-button>
          <el-button v-if="$hasPerm('history:query')" size="small" type="info"
                     @click="viewLastLog(row)">部署日志</el-button>
          <el-popconfirm v-if="$user && $user.isSuperAdmin === true" title="确定删除该项目？" @confirm="remove(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
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

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑项目' : '新增项目'" width="640px" top="4vh">
    <el-form :model="form" label-width="100px">
      <el-form-item label="项目名" required>
        <el-input v-model="form.name" placeholder="如 order-service" />
      </el-form-item>
      <el-form-item label="类型" required>
        <el-radio-group v-model="form.type" @change="onTypeChange">
          <el-radio-button value="JAVA">JAVA</el-radio-button>
          <el-radio-button value="VUE">VUE</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Git 地址" required>
        <el-input v-model="form.gitUrl" placeholder="http://gitlab.xxx.com/group/xxx.git OR git@xxx:group/xxx.git" />
      </el-form-item>
      <el-form-item label="分支" required>
        <el-input v-model="form.branch" placeholder="dev" />
      </el-form-item>
      <el-form-item label="本地目录">
        <el-input v-if="!form.id || $hasPerm('server:edit')" v-model="form.localPath"
                  placeholder="留空则 clone 到全局工作区 D:/deploy-workspace/项目名" />
        <el-input v-else :model-value="form.localPath || '-'" disabled />
      </el-form-item>
      <el-form-item label="构建命令" required>
        <el-input v-if="!form.id || $hasPerm('server:edit')" v-model="form.buildCmd"
                  placeholder="npm install && npm run build:dev" />
        <el-input v-else :model-value="form.buildCmd || '-'" disabled />
      </el-form-item>
      <el-form-item v-if="form.type === 'JAVA'" label="打包Profile">
        <el-select v-if="!form.id || $hasPerm('server:edit')" v-model="form.buildProfile"
                   placeholder="留空使用项目默认(activeByDefault)" clearable filterable allow-create style="width: 100%">
          <el-option label="dev" value="dev" />
          <el-option label="test" value="test" />
          <el-option label="pre" value="pre" />
          <el-option label="prod" value="prod" />
        </el-select>
        <el-input v-else :model-value="form.buildProfile || '-'" disabled />
      </el-form-item>
      <el-form-item label="产物路径" required>
        <el-input v-if="!form.id || $hasPerm('server:edit')" v-model="form.artifactPath"
                  placeholder="JAVA: target/*.jar；VUE: dist（自动压缩为 dist.zip）" />
        <el-input v-else :model-value="form.artifactPath || '-'" disabled />
      </el-form-item>
      <el-form-item label="目标服务器" required>
        <!-- 有 server:edit 权限：可选服务器 -->
        <el-select v-if="!form.id || $hasPerm('server:edit')" v-model="form.serverId"
                   placeholder="选择服务器" style="width: 100%">
          <el-option v-for="s in servers" :key="s.id" :label="`${s.name} (${s.host})`" :value="s.id" />
        </el-select>
        <!-- 没有 server:edit：只读展示已保存的 serverName，不能换 -->
        <el-input v-else :model-value="form.serverName || '-'" disabled />
      </el-form-item>
      <el-form-item label="上传目录" required>
        <el-input v-if="!form.id || $hasPerm('server:edit')" v-model="form.uploadDir"
                  placeholder="如 /app/order-service" />
        <el-input v-else :model-value="form.uploadDir || '-'" disabled />
      </el-form-item>
      <el-form-item label="部署命令">
        <el-input v-if="!form.id || $hasPerm('server:edit')" v-model="form.deployCmd" type="textarea" :rows="2"
                  placeholder="上传后在服务器执行，如 cd /app/order-service && sh deploy.sh；VUE 项目可留空" />
        <el-input v-else :model-value="form.deployCmd || '-'" type="textarea" :rows="2" disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <LogDialog v-model="logVisible" :record-id="currentRecordId" :mode="logMode"
             :title="`部署日志 - ${currentProjectName}`" @finished="onDeployFinished" />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { projectApi, serverApi, deployApi } from '../api'
import { hasPerm } from '../utils/perm'
import LogDialog from '../components/LogDialog.vue'

const DEFAULT_CMD = {
  JAVA: { buildCmd: 'mvn clean install -DskipTests -e', artifactPath: 'target/*.jar' },
  VUE: { buildCmd: 'npm run build', artifactPath: 'dist' }
}

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({})

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

const servers = ref([])

const lastMap = ref({})

const deployingId = ref(null)
const logVisible = ref(false)
const currentRecordId = ref(null)
const currentProjectName = ref('')
const logMode = ref('static')

async function load() {
  loading.value = true
  try {
    const result = await projectApi.list({ page: page.value, pageSize: pageSize.value, keyword: keyword.value })
    list.value = result.records || []
    total.value = result.total || 0
    await refreshLast()
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function resetSearch() {
  keyword.value = ''
  page.value = 1
  load()
}

async function refreshLast() {
  const ids = list.value.map((p) => p.id)
  if (!ids.length) { lastMap.value = {}; return }
  try {
    const map = await deployApi.lastByProjects(ids)
    // axios 把数字 key 的 Map 返回后仍是对象，用数字访问即可
    lastMap.value = map || {}
  } catch (e) {
    lastMap.value = {}
  }
}

function lastOf(projectId) {
  return lastMap.value[projectId] || lastMap.value[String(projectId)] || null
}

function lastField(projectId, field) {
  const rec = lastOf(projectId)
  if (!rec || !rec[field]) return '-'
  return String(rec[field]).replace('T', ' ').substring(0, 16)
}

function openDialog(row) {
  form.value = row ? { ...row } : { type: 'JAVA', branch: 'dev', ...DEFAULT_CMD.JAVA }
  dialogVisible.value = true
}

function onTypeChange(type) {
  // 新增时切换类型自动带出默认构建命令和产物路径
  if (!form.value.id) {
    form.value.buildCmd = DEFAULT_CMD[type].buildCmd
    form.value.artifactPath = DEFAULT_CMD[type].artifactPath
  }
}

async function save() {
  saving.value = true
  try {
    await projectApi.save(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

async function remove(row) {
  await projectApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function deploy(row) {
  // 检查上次部署是否还在跑，避免重复部署
  const last = lastOf(row.id)
  if (last && last.status === 'RUNNING') {
    ElMessage.warning('上次部署尚未结束，请稍后再试')
    return
  }
  deployingId.value = row.id
  try {
    const recordId = await deployApi.start(row.id)
    currentRecordId.value = recordId
    currentProjectName.value = row.name
    logMode.value = 'live'
    logVisible.value = true
  } catch (e) { /* 拦截器已提示 */ } finally {
    deployingId.value = null
  }
}

// 查看该项目上次部署日志：先拉 last-by-projects 拿最近一次记录，无记录则提示
async function viewLastLog(row) {
  try {
    const map = await deployApi.lastByProjects([row.id])
    const last = map && map[row.id]
    if (!last || !last.id) {
      ElMessage.warning('暂无部署记录')
      return
    }
    currentRecordId.value = last.id
    currentProjectName.value = row.name
    logMode.value = last.status === 'RUNNING' ? 'live' : 'static'
    logVisible.value = true
  } catch (e) { /* 拦截器已提示 */ }
}

function onDeployFinished(status) {
  if (status === 'SUCCESS') {
    ElMessage.success(`[${currentProjectName.value}] 部署成功`)
  } else {
    ElMessage.error(`[${currentProjectName.value}] 部署失败，请查看日志`)
  }
  refreshLast()
}

onMounted(async () => {
  // 编辑弹框服务器下拉使用（仅拥有 server:edit 权限才拉，避免无权限用户触发 403）
  if (hasPerm('server:edit')) {
    try {
      const r = await serverApi.list({ pageSize: 10000 })
      servers.value = r.records || []
    } catch (e) {
      servers.value = []
    }
  }
  load()
})
</script>

<style scoped>
.toolbar { margin-bottom: 14px; display: flex; justify-content: space-between; align-items: flex-start; }
.search-form { margin-bottom: 0; }
.pagination-wrap { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
