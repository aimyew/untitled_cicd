<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增菜单
      </el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe row-key="id">
      <el-table-column prop="id" label="#" width="70" />
      <el-table-column label="层级" width="90">
        <template #default="{ row }">
          <el-tag v-if="!row.parentId" size="small">一级</el-tag>
          <el-tag v-else size="small" type="info">子级</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="140" />
      <el-table-column prop="path" label="路由路径" min-width="160" />
      <el-table-column prop="icon" label="图标" width="180">
        <template #default="{ row }">
          <div v-if="row.icon" style="display: flex; align-items: center; gap: 8px">
            <el-icon :size="16"><component :is="row.icon" /></el-icon>
            <span>{{ row.icon }}</span>
          </div>
          <span v-else class="text-hint">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="permCode" label="权限码" min-width="160">
        <template #default="{ row }">
          <span v-if="row.permCode">{{ row.permCode }}</span>
          <span v-else class="text-hint">所有人可见</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确定删除该菜单？子菜单也会删除" @confirm="remove(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑菜单' : '新增菜单'" width="520px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="父菜单">
        <el-select v-model="form.parentId" placeholder="无（作为一级菜单）" clearable style="width: 100%">
          <el-option :value="0" label="无（一级菜单）" />
          <el-option v-for="m in parentOptions" :key="m.id" :label="m.title" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="如：菜单管理" />
      </el-form-item>
      <el-form-item label="路由路径" required>
        <el-input v-model="form.path" placeholder="如 /menus" />
      </el-form-item>
      <el-form-item label="图标名">
        <el-select v-model="form.icon" placeholder="选择一个图标" clearable filterable style="width: 100%">
          <template v-if="form.icon" #prefix>
            <el-icon :size="16"><component :is="form.icon" /></el-icon>
          </template>
          <el-option v-for="name in ICON_NAMES" :key="name" :label="name" :value="name">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon :size="16"><component :is="name" /></el-icon>
              <span>{{ name }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="权限码">
        <el-input v-model="form.permCode" placeholder="留空=所有人可见；填 project:query 等=按权限码过滤" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :max="10000" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { menuApi } from '../api/auth'

// Element Plus 全部图标名列表（按字母序），用于下拉选择
const ICON_NAMES = Object.keys(ElementPlusIconsVue).sort()

const list = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({})

const parentOptions = computed(() => list.value.filter(m => !m.parentId || m.parentId === 0))

async function load() {
  loading.value = true
  try {
    list.value = (await menuApi.list()) || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  form.value = row
    ? { ...row }
    : { parentId: 0, sortOrder: 0 }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.path) return ElMessage.warning('路由路径不能为空')
  if (!form.value.title) return ElMessage.warning('菜单标题不能为空')
  saving.value = true
  try {
    await menuApi.save(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

async function remove(row) {
  await menuApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 14px; display: flex; justify-content: space-between; }
.text-hint { color: #999; font-size: 12px; }
</style>
