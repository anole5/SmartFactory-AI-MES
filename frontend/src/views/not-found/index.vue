<template>
  <div class="not-found">
    <el-result icon="warning" title="404" sub-title="页面不存在或无权访问">
      <template #extra>
        <el-button type="primary" @click="goHome">回生产看板</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
const goHome = () => router.push('/dashboard')
</script>

<style scoped>
.not-found {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
