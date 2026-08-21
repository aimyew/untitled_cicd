<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-form :inline="true" @submit.prevent="onSearch" class="search-form">
        <el-form-item>
          <el-input v-model="keyword" placeholder="名称 / Host" clearable @clear="onSearch"
                    @keyup.enter="onSearch" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
      <div>
        <el-button v-if="$hasPerm('server:add')" type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>&nbsp;新增服务器
        </el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="host" label="Host" min-width="140" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="username" label="用户名" width="110" />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button v-if="$hasPerm('server:query')" size="small" :loading="testingId === row.id"
                     @click="testConnect(row)">测试连接</el-button>
          <el-button v-if="$user && $user.isSuperAdmin === true" size="small" type="warning"
                     @click="openCommandDialog(row)">远程命令</el-button>
          <el-button v-if="$hasPerm('server:edit')" size="small" type="primary"
                     @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="$user && $user.isSuperAdmin === true" title="确定删除该服务器？" @confirm="remove(row)">
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

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑服务器' : '新增服务器'" width="520px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="如：测试环境-应用服务器" />
      </el-form-item>
      <el-form-item label="Host" required>
        <el-input v-model="form.host" placeholder="IP 或域名" />
      </el-form-item>
      <el-form-item label="SSH端口" required>
        <el-input-number v-model="form.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="用户名" required>
        <el-input v-model="form.username" placeholder="如 root" />
      </el-form-item>
      <el-form-item label="密码" :required="!form.id">
        <el-input v-model="form.password" type="password" show-password
                  :placeholder="form.id ? '留空表示不修改密码' : 'SSH 登录密码'" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <!-- 远程命令弹窗 -->
  <el-dialog v-model="cmdDialogVisible" :title="'远程命令 - ' + cmdServer?.name" width="700px">
    <!-- 生成 SQL 区域 -->
    <div style="margin-bottom: 16px">
      <div style="font-weight: bold; margin-bottom: 8px">新增命令（生成 SQL）</div>
      <el-form :inline="true" style="margin-bottom: 0">
        <el-form-item style="margin-bottom: 8px">
          <el-input v-model="newCmd.name" placeholder="命令名称，如：清理日志" style="width: 180px" />
        </el-form-item>
        <el-form-item style="margin-bottom: 8px">
          <el-input v-model="newCmd.command" placeholder="Linux 命令，如：rm -rf /home/*.log" style="width: 340px" />
        </el-form-item>
        <el-form-item style="margin-bottom: 8px">
          <el-button type="info" @click="generateSql">生成 SQL</el-button>
        </el-form-item>
      </el-form>
      <el-input v-if="generatedSql" v-model="generatedSql" readonly type="textarea" :rows="2"
                style="font-family: monospace; margin-top: 4px" />
    </div>

    <el-divider />

    <!-- 已配置命令列表 -->
    <div>
      <div style="font-weight: bold; margin-bottom: 8px">已配置命令</div>
      <el-table :data="commands" v-loading="cmdLoading" size="small" style="width: 100%">
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="command" label="命令" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" :loading="executingId === row.id"
                       @click="executeCommand(row)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 执行结果 -->
    <div v-if="execOutput !== null" style="margin-top: 12px">
      <div style="font-weight: bold; margin-bottom: 4px">
        执行结果（exitCode: <span :style="{ color: execExitCode === 0 ? '#67c23a' : '#f56c6c' }">{{ execExitCode }}</span>）
      </div>
      <el-input v-model="execOutput" readonly type="textarea" :rows="8"
                style="font-family: monospace" />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { serverApi } from '../api'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const testingId = ref(null)
const form = ref({})

// 远程命令相关
const cmdDialogVisible = ref(false)
const cmdServer = ref(null)
const commands = ref([])
const cmdLoading = ref(false)
const newCmd = ref({ name: '', command: '' })
const generatedSql = ref('')
const executingId = ref(null)
const execOutput = ref(null)
const execExitCode = ref(null)

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const result = await serverApi.list({ page: page.value, pageSize: pageSize.value, keyword: keyword.value })
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
  keyword.value = ''
  page.value = 1
  load()
}

function openDialog(row) {
  form.value = row ? { ...row, password: '' } : { port: 22 }
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (form.value.id) {
      await serverApi.update(form.value.id, form.value)
    } else {
      await serverApi.create(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

async function remove(row) {
  await serverApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function testConnect(row) {
  testingId.value = row.id
  try {
    await serverApi.test(row.id)
    ElMessage.success(`[${row.name}] 连接成功`)
  } catch (e) { /* 拦截器已提示 */ } finally {
    testingId.value = null
  }
}

async function openCommandDialog(row) {
  cmdServer.value = row
  commands.value = []
  newCmd.value = { name: '', command: '' }
  generatedSql.value = ''
  execOutput.value = null
  execExitCode.value = null
  cmdDialogVisible.value = true
  cmdLoading.value = true
  try {
    commands.value = await serverApi.listCommands(row.id) || []
  } catch (e) { /* 拦截器已提示 */ } finally {
    cmdLoading.value = false
  }
}

async function generateSql() {
  if (!newCmd.value.name || !newCmd.value.command) {
    ElMessage.warning('请填写命令名称和命令内容')
    return
  }
  try {
    const sql = await serverApi.generateSql(cmdServer.value.id, newCmd.value)
    generatedSql.value = sql
  } catch (e) { /* 拦截器已提示 */ }
}

async function executeCommand(cmd) {
  executingId.value = cmd.id
  execOutput.value = null
  execExitCode.value = null
  try {
    const result = await serverApi.execute(cmdServer.value.id, cmd.id)
    execExitCode.value = result.exitCode
    execOutput.value = result.output || '(无输出)'
  } catch (e) {
    execOutput.value = '执行失败'
    execExitCode.value = -1
  } finally {
    executingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 14px; display: flex; justify-content: space-between; align-items: flex-start; }
.search-form { margin-bottom: 0; }
.pagination-wrap { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
