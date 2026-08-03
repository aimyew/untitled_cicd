<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-form :inline="true" @submit.prevent="onSearch" class="search-form">
        <el-form-item>
          <el-input v-model="keyword" placeholder="IP / 昵称" clearable @clear="onSearch"
                    @keyup.enter="onSearch" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="ip" label="IP" width="150" />
      <el-table-column label="昵称" width="150">
        <template #default="{ row }">
          <el-input v-if="editingNickId === row.id" v-model="row.nickname" size="small" style="width: 100px" />
          <span v-else>{{ row.nickname || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.role === 'SUPER_ADMIN'" type="danger" effect="dark">超级管理员</el-tag>
          <el-tag v-else>普通用户</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch v-model="row.status" active-value="ENABLED" inactive-value="DISABLED"
                     :disabled="row.role === 'SUPER_ADMIN'" @change="onStatusChange(row)" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="420">
        <template #default="{ row }">
          <template v-if="row.role !== 'SUPER_ADMIN'">
            <el-button size="small" @click="startNickEdit(row)">改昵称</el-button>
            <el-button size="small" v-if="editingNickId === row.id" type="success"
                       @click="saveNick(row)">保存</el-button>
            <el-button size="small" type="warning" @click="startPwdEdit(row)">重置密码</el-button>
            <el-button size="small" type="primary" @click="openPermDialog(row)">配置权限</el-button>

            <!-- 重置密码的行内编辑区 -->
            <div v-if="editingPwdId === row.id" class="inline-pwd">
              <el-input v-model="newPwd" type="password" show-password size="small"
                        placeholder="新密码" style="width: 120px" />
              <el-input v-model="confirmPwd" type="password" show-password size="small"
                        placeholder="确认密码" style="width: 120px" />
              <el-button size="small" type="success" @click="savePwd(row)">保存</el-button>
              <el-button size="small" @click="cancelPwdEdit">取消</el-button>
            </div>
          </template>
          <span v-else class="text-hint">超管无需配置</span>
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

    <!-- 权限配置弹框 -->
    <el-dialog v-model="permVisible" :title="`配置权限 - ${current.ip}`" width="720px">
      <el-tabs v-model="permTab">
        <el-tab-pane label="项目权限" name="project">
          <el-alert type="info" :closable="false" style="margin-bottom: 10px">
            勾选的项目允许该用户查看 / 编辑 / 部署；具体能点的按钮由「部署权限」决定。
          </el-alert>
          <el-checkbox-group v-model="checkedDeployProjectIds">
            <el-checkbox v-for="p in projects" :key="p.id" :label="p.id" style="width: 100%">
              [{{ p.type }}] {{ p.name }} <span class="text-hint">— {{ p.gitUrl }}</span>
            </el-checkbox>
          </el-checkbox-group>
          <el-empty v-if="!projects.length" description="暂无项目" />
        </el-tab-pane>
        <el-tab-pane label="部署权限" name="deploy">
          <el-checkbox-group v-model="checkedFuncPerms">
            <el-checkbox v-for="item in deployPermDefs" :key="item.permCode" :label="item.permCode" class="perm-chk">
              <span class="perm-title">{{ item.title }}</span>
              <span class="perm-desc">{{ item.description }}</span>
            </el-checkbox>
          </el-checkbox-group>
          <el-empty v-if="!deployPermDefs.length" description="暂无部署权限项" />
        </el-tab-pane>
        <el-tab-pane label="系统权限" name="system">
          <el-checkbox-group v-model="checkedFuncPerms">
            <el-checkbox v-for="item in systemPermDefs" :key="item.permCode" :label="item.permCode" class="perm-chk">
              <span class="perm-title">{{ item.title }}</span>
              <span class="perm-desc">{{ item.description }}</span>
            </el-checkbox>
          </el-checkbox-group>
          <el-empty v-if="!systemPermDefs.length" description="暂无系统权限项" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerms">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/auth'
import { projectApi } from '../api'

const loading = ref(false)
const list = ref([])
const projects = ref([])
const funcPermDefs = ref([])

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const usersResult = await userApi.list({
      page: page.value, pageSize: pageSize.value, keyword: keyword.value
    })
    list.value = usersResult.records || []
    total.value = usersResult.total || 0
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [projs, defs] = await Promise.all([
      projectApi.list({ pageSize: 10000 }),
      userApi.funcPermDefs()
    ])
    projects.value = projs.records || []
    funcPermDefs.value = defs
  } catch (e) {
    projects.value = []
    funcPermDefs.value = []
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

onMounted(() => {
  loadOptions()
  load()
})

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 19)
}

// 昵称编辑
const editingNickId = ref(null)
function startNickEdit(row) {
  editingNickId.value = row.id
}
async function saveNick(row) {
  try {
    await userApi.updateNickname(row.id, row.nickname || '')
    ElMessage.success('昵称已更新')
    editingNickId.value = null
  } catch (e) { load() }
}

// 重置密码（行内编辑）
const editingPwdId = ref(null)
const newPwd = ref('')
const confirmPwd = ref('')
function startPwdEdit(row) {
  editingPwdId.value = row.id
  newPwd.value = ''
  confirmPwd.value = ''
}
function cancelPwdEdit() {
  editingPwdId.value = null
  newPwd.value = ''
  confirmPwd.value = ''
}
async function savePwd(row) {
  if (!newPwd.value) return ElMessage.warning('新密码不能为空')
  if (newPwd.value !== confirmPwd.value) return ElMessage.error('两次密码不一致')
  try {
    await userApi.resetPassword(row.id, newPwd.value)
    ElMessage.success('密码已重置')
    cancelPwdEdit()
  } catch (e) { /* 拦截器已处理 */ }
}

// 状态切换
async function onStatusChange(row) {
  try {
    await userApi.updateStatus(row.id, row.status)
    ElMessage.success(`用户已${row.status === 'ENABLED' ? '启用' : '禁用'}`)
  } catch (e) { load() }
}

// 重置密码
async function resetPwd(row) {
  try {
    await userApi.resetPassword(row.id, '123456')
    ElMessage.success('密码已重置为 123456')
  } catch (e) { /* 拦截器已处理 */ }
}

// 权限配置
const permVisible = ref(false)
const permSaving = ref(false)
const permTab = ref('project')
const current = ref({})
const checkedFuncPerms = ref([])
const checkedDeployProjectIds = ref([])

// 按 permType 分组（后端返回的 funcPermDefs 每项带 permType）
const deployPermDefs = computed(() => funcPermDefs.value.filter(d => d.permType === 'DEPLOY'))
const systemPermDefs = computed(() => funcPermDefs.value.filter(d => d.permType === 'SYSTEM'))

function openPermDialog(row) {
  current.value = row
  checkedFuncPerms.value = [...(row.perms || [])]
  checkedDeployProjectIds.value = [...(row.deployProjectIds || [])]
  permTab.value = 'project'
  permVisible.value = true
}

async function savePerms() {
  permSaving.value = true
  try {
    await userApi.updatePermissions(current.value.id, checkedFuncPerms.value)
    await userApi.updateDeployPermissions(current.value.id, checkedDeployProjectIds.value)
    ElMessage.success('权限已保存')
    permVisible.value = false
    load()
  } catch (e) { /* 拦截器已处理 */ } finally {
    permSaving.value = false
  }
}
</script>

<style scoped>
.toolbar { margin-bottom: 14px; }
.search-form { margin-bottom: 0; }
.pagination-wrap { margin-top: 14px; display: flex; justify-content: flex-end; }
.inline-pwd { display: inline-flex; gap: 6px; margin-left: 8px; vertical-align: middle; }
.perm-chk { display: block; margin-bottom: 6px; }
.perm-title { font-weight: 500; }
.perm-desc { color: #999; font-size: 12px; margin-left: 8px; }
.text-hint { color: #999; font-size: 12px; }
</style>
