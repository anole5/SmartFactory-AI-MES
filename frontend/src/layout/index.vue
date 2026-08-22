<template>
  <el-container class="layout">
    <!-- 侧边菜单 -->
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="22" color="#409eff"><Cpu /></el-icon>
        <span>SmartFactory MES</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.68)"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/products">
          <el-icon><Goods /></el-icon><span>产品管理</span>
        </el-menu-item>
        <el-menu-item index="/materials">
          <el-icon><Box /></el-icon><span>物料管理</span>
        </el-menu-item>
        <el-menu-item index="/processes">
          <el-icon><SetUp /></el-icon><span>工序管理</span>
        </el-menu-item>
        <el-menu-item index="/workstations">
          <el-icon><Monitor /></el-icon><span>工位管理</span>
        </el-menu-item>
        <el-menu-item index="/boms">
          <el-icon><List /></el-icon><span>BOM 管理</span>
        </el-menu-item>
        <el-menu-item index="/routes">
          <el-icon><Connection /></el-icon><span>工艺路线</span>
        </el-menu-item>
        <el-menu-item index="/work-orders">
          <el-icon><Tickets /></el-icon><span>生产工单</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><Operation /></el-icon><span>工序任务</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><DataLine /></el-icon><span>报工记录</span>
        </el-menu-item>
        <el-menu-item index="/tv-demo">
          <el-icon><VideoPlay /></el-icon><span>电视 Demo</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="page-title">{{ route.meta.title }}</div>
        <div class="header-right">
          <el-icon><User /></el-icon>
          <span class="username">{{ auth.userInfo?.realName || auth.userInfo?.username || '未登录' }}</span>
          <el-button link type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

async function handleLogout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background-color: #001529;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.aside :deep(.el-menu) {
  border-right: none;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.username {
  color: #606266;
}

.main {
  padding: 16px;
  overflow: auto;
}
</style>
