<template>
  <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.path">
    <template #title>
      <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
      <span>{{ menu.title }}</span>
    </template>
    <MenuItem v-for="child in menu.children" :key="child.path" :menu="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="menu.path">
    <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
    <span>{{ menu.title }}</span>
  </el-menu-item>
</template>

<script setup>
defineProps({
  menu: { type: Object, required: true }
})
</script>
