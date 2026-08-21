<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增菜单
      </el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table
      ref="tableRef"
      :data="treeList"
      v-loading="loading"
      row-key="id"
      :tree-props="{ children: 'children' }"
      default-expand-all
      stripe
      class="menu-table"
    >
      <el-table-column label="标题" min-width="180">
        <template #default="{ row }">
          <span
            :class="{ 'title-text': true, 'title-expandable': row.children && row.children.length }"
            @click="toggleTitle(row)"
          >{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'GROUP'" size="small" type="info">分组</el-tag>
          <el-tag v-else size="small" type="success">页面</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="路由路径" min-width="140">
        <template #default="{ row }">
          <span v-if="row.type === 'GROUP'" class="text-hint">-</span>
          <code v-else class="path-code">{{ row.path }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="icon" label="图标" width="160">
        <template #default="{ row }">
          <div v-if="row.icon" class="icon-cell">
            <el-icon :size="16"><component :is="row.icon" /></el-icon>
            <span class="icon-name">{{ row.icon }}</span>
          </div>
          <span v-else class="text-hint">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="permCode" label="权限码" min-width="140">
        <template #default="{ row }">
          <code v-if="row.permCode" class="perm-code">{{ row.permCode }}</code>
          <span v-else class="text-hint">所有人可见</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="操作" width="160" fixed="right" align="center">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确定删除该菜单？子菜单也会删除" @confirm="remove(row)">
            <template #reference>
              <el-button size="small" type="danger" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑菜单' : '新增菜单'" width="520px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="菜单类型" required>
        <el-radio-group v-model="form.type">
          <el-radio-button value="LINK">页面菜单</el-radio-button>
          <el-radio-button value="GROUP">分组菜单</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="父菜单">
        <el-select v-model="form.parentId" placeholder="无（作为一级菜单）" clearable style="width: 100%">
          <el-option :value="0" label="无（一级菜单）" />
          <el-option v-for="m in parentOptions" :key="m.id" :label="m.title" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="如：菜单管理" />
      </el-form-item>
      <el-form-item v-if="form.type === 'LINK'" label="路由路径" required>
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
      <el-form-item v-if="form.type === 'LINK'" label="权限码">
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
const tableRef = ref(null)

const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({})

// 收集 menuId 的所有后代 id，避免把自己或后代选为父菜单形成环
function collectDescendantIds(menuId, allMenus) {
  const ids = new Set()
  const children = allMenus.filter(m => m.parentId === menuId)
  children.forEach(child => {
    ids.add(child.id)
    collectDescendantIds(child.id, allMenus).forEach(id => ids.add(id))
  })
  return ids
}

// 父菜单可选任意菜单，但编辑时排除自身及其后代防止循环
const parentOptions = computed(() => {
  const excludeIds = form.value.id ? collectDescendantIds(form.value.id, list.value) : new Set()
  excludeIds.add(form.value.id)
  return list.value.filter(m => !excludeIds.has(m.id))
})

// 将扁平列表按 parentId 递归构建树，同级按 sortOrder 排序
function buildTree(list, parentId = 0) {
  return list
    .filter(m => (m.parentId || 0) === parentId)
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map(m => ({ ...m, children: buildTree(list, m.id) }))
}

const treeList = computed(() => buildTree(list.value))

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
    : { parentId: 0, type: 'LINK', sortOrder: 0 }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.title) return ElMessage.warning('菜单标题不能为空')
  if (!form.value.type) return ElMessage.warning('菜单类型不能为空')
  if (form.value.type === 'LINK' && !form.value.path) return ElMessage.warning('页面菜单的路由路径不能为空')
  saving.value = true
  try {
    if (form.value.id) {
      // 编辑：只发允许修改的字段
      await menuApi.update(form.value.id, {
        title: form.value.title,
        icon: form.value.icon,
        permCode: form.value.permCode,
        sortOrder: form.value.sortOrder
      })
    } else {
      // 新增：发所有字段
      await menuApi.create(form.value)
    }
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

function toggleTitle(row) {
  if (row.children && row.children.length) {
    tableRef.value.toggleRowExpansion(row)
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 14px; display: flex; justify-content: space-between; }
.text-hint { color: #999; font-size: 12px; }

/* 树形表格统一行高与细节 */
.menu-table :deep(.el-table__row) {
  height: 48px;
}
.menu-table :deep(.el-table__cell) {
  padding: 0 !important;
}
.menu-table :deep(.el-table__cell .cell) {
  height: 48px;
  line-height: 24px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  box-sizing: border-box;
}
/* 隐藏默认展开箭头，由点击标题触发 */
.menu-table :deep(.el-table__expand-icon) {
  display: none;
}
.menu-table :deep(.el-table__expand-icon--placeholder),
.menu-table :deep(.el-table__placeholder) {
  display: none;
}

/* 可展开的标题文字 */
.title-expandable {
  cursor: pointer;
  color: #409eff;
}

.icon-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.icon-name {
  color: #606266;
  font-size: 13px;
}

.path-code {
  background: #f5f7fa;
  color: #409eff;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  font-family: monospace;
}
.perm-code {
  background: #fef0f0;
  color: #f56c6c;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  font-family: monospace;
}
</style>
