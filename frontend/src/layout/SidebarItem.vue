<script setup lang="ts">
import type { MenuNode } from '@/api/types'

// 递归菜单项（第 5 周动态路由）：目录渲染 el-sub-menu，菜单渲染 el-menu-item
defineOptions({ name: 'SidebarItem' })

defineProps<{ node: MenuNode }>()
</script>

<template>
  <!-- M 级：index 用菜单 id（非 / 开头，点击只展开不触发路由导航） -->
  <el-sub-menu v-if="node.menuType === 'M' && node.children?.length" :index="String(node.id)">
    <template #title>
      <el-icon v-if="node.icon"><component :is="node.icon" /></el-icon>
      <span>{{ node.menuName }}</span>
    </template>
    <SidebarItem v-for="child in node.children" :key="child.id" :node="child" />
  </el-sub-menu>
  <!-- C 级：index 用绝对路径（el-menu router 模式点击即导航） -->
  <el-menu-item v-else-if="node.path" :index="node.path">
    <el-icon v-if="node.icon"><component :is="node.icon" /></el-icon>
    <span>{{ node.menuName }}</span>
  </el-menu-item>
</template>
