<template>
  <div class="tv-page" v-loading="loading">
    <!-- 顶栏 -->
    <div class="tv-header">
      <div class="tv-header-left">
        <el-icon :size="26" color="#00d4ff"><Monitor /></el-icon>
        <span class="tv-title">SmartFactory MES · 智能电视生产 Demo</span>
      </div>
      <div class="tv-header-right">
        <span class="tv-time">{{ now }}</span>
      </div>
    </div>

    <template v-if="bom">
      <!-- 产品信息卡 -->
      <div class="tv-product-card">
        <div class="tv-product-main">
          <div class="tv-product-code">{{ product?.productCode ?? '-' }}</div>
          <div class="tv-product-name">{{ product?.productName ?? '-' }}</div>
        </div>
        <div class="tv-product-meta">
          <div class="meta-item"><span>规格型号</span><b>{{ product?.specification || '-' }}</b></div>
          <div class="meta-item"><span>产品类型</span><b>{{ product?.productType || '-' }}</b></div>
          <div class="meta-item"><span>BOM 版本</span><b>{{ bom.version || '-' }}</b></div>
          <div class="meta-item"><span>BOM 状态</span><b class="status-active">生效 ACTIVE</b></div>
        </div>
      </div>

      <div class="tv-body">
        <!-- 左侧统计 -->
        <div class="tv-stats">
          <div class="stat-block">
            <div class="stat-num">{{ bom.items?.length ?? 0 }}</div>
            <div class="stat-label">BOM 物料行数</div>
          </div>
          <div class="stat-block">
            <div class="stat-num" style="color: #ffb020">{{ traceCount }}</div>
            <div class="stat-label">批次追溯关键件</div>
          </div>
          <div class="stat-block">
            <div class="stat-num" style="color: #00d4ff">{{ route?.steps?.length ?? 0 }}</div>
            <div class="stat-label">工艺步骤数</div>
          </div>
          <div class="stat-block">
            <div class="stat-num" style="color: #5ee2a0">{{ standardTotal }} 分</div>
            <div class="stat-label">标准工时合计</div>
          </div>
        </div>

        <!-- 中间 BOM 明细 -->
        <div class="tv-panel">
          <div class="panel-title">BOM 物料清单（{{ bom.bomNo }}）</div>
          <el-table :data="bom.items ?? []" size="small" class="dark-table" max-height="440">
            <el-table-column prop="lineNo" label="行号" width="70" />
            <el-table-column prop="materialCodeSnapshot" label="物料编码" min-width="200" />
            <el-table-column prop="materialNameSnapshot" label="物料名称" min-width="220" />
            <el-table-column label="单位" width="80">
              <template #default="{ row }">{{ row.unitSnapshot }}</template>
            </el-table-column>
            <el-table-column label="单位用量" width="100">
              <template #default="{ row }">{{ row.requiredQty }}</template>
            </el-table-column>
            <el-table-column label="损耗率" width="90">
              <template #default="{ row }">{{ row.lossRate ?? 0 }}%</template>
            </el-table-column>
            <el-table-column label="批次追溯" width="100">
              <template #default="{ row }">
                <el-tag :type="traceMap[row.materialId] ? 'warning' : 'info'" size="small" effect="dark">
                  {{ traceMap[row.materialId] ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 底部工艺路线步骤流 -->
      <div class="tv-panel tv-route-panel" v-if="route">
        <div class="panel-title">工艺路线（{{ route.routeNo }}）</div>
        <div class="step-flow">
          <template v-for="(step, i) in route.steps ?? []" :key="step.id">
            <div class="step-node" :class="{ inspect: step.needInspection }">
              <div class="step-seq">{{ step.sequenceNo }}</div>
              <div class="step-name">{{ step.processNameSnapshot }}</div>
              <div class="step-min">{{ step.standardMinutes }} 分</div>
              <div class="step-ws">{{ step.workstationCode || '通用工位' }}</div>
            </div>
            <div v-if="i < (route.steps?.length ?? 0) - 1" class="step-arrow">➜</div>
          </template>
        </div>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="未找到 AOC 55 英寸电视的生效 BOM，请先在产品/BOM 模块维护" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { bomApi, materialApi, productApi, routeApi } from '@/api'
import type { Bom, Product, Route } from '@/api/types'

const loading = ref(false)
const product = ref<Product | null>(null)
const bom = ref<Bom | null>(null)
const route = ref<Route | null>(null)
/** materialId -> 是否批次追溯（由物料主数据匹配） */
const traceMap = ref<Record<string, boolean>>({})
const now = ref(new Date().toLocaleString())

// 演示页时钟（分钟级刷新即可）
setInterval(() => {
  now.value = new Date().toLocaleString()
}, 1000)

const traceCount = computed(
  () => (bom.value?.items ?? []).filter((i) => traceMap.value[i.materialId]).length,
)

const standardTotal = computed(
  () => (route.value?.steps ?? []).reduce((sum, s) => sum + Number(s.standardMinutes ?? 0), 0),
)

onMounted(async () => {
  loading.value = true
  try {
    // 1. 按编码找主产品
    const productPage = await productApi.page({ pageNum: 1, pageSize: 10, keyword: 'TV-AOC-55U4K-001' })
    product.value = productPage.records[0] ?? null
    if (!product.value) return

    // 2. 找该产品的生效 BOM 与生效工艺路线（种子数据已配好）
    const [bomPage, routePage, materialPage] = await Promise.all([
      bomApi.page({ pageNum: 1, pageSize: 10, productId: product.value.id, status: 'ACTIVE' }),
      routeApi.page({ pageNum: 1, pageSize: 10, productId: product.value.id, status: 'ACTIVE' }),
      materialApi.page({ pageNum: 1, pageSize: 100 }),
    ])
    traceMap.value = Object.fromEntries(materialPage.records.map((m) => [m.id, m.traceRequired]))
    const bomId = bomPage.records[0]?.id
    const routeId = routePage.records[0]?.id
    if (bomId) bom.value = await bomApi.detail(bomId)
    if (routeId) route.value = await routeApi.detail(routeId)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.tv-page {
  min-height: 100%;
  background: linear-gradient(160deg, #0a1628 0%, #10233f 60%, #0d1b31 100%);
  border-radius: 6px;
  padding: 0 16px 20px;
}

.tv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid rgba(0, 212, 255, 0.25);
}

.tv-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tv-title {
  font-size: 20px;
  font-weight: 700;
  color: #e6f7ff;
  letter-spacing: 1px;
}

.tv-time {
  color: #8fc7e8;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.tv-product-card {
  margin-top: 16px;
  padding: 18px 24px;
  background: rgba(0, 212, 255, 0.06);
  border: 1px solid rgba(0, 212, 255, 0.3);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.tv-product-code {
  font-size: 28px;
  font-weight: 800;
  color: #00d4ff;
  letter-spacing: 2px;
}

.tv-product-name {
  font-size: 15px;
  color: #b8d8e8;
  margin-top: 4px;
}

.tv-product-meta {
  display: flex;
  gap: 28px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-item span {
  font-size: 12px;
  color: #6f9ab5;
}

.meta-item b {
  font-size: 15px;
  color: #d8ecf7;
}

.status-active {
  color: #5ee2a0 !important;
}

.tv-body {
  display: flex;
  gap: 16px;
  margin-top: 16px;
}

.tv-stats {
  width: 200px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.stat-block {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.stat-num {
  font-size: 30px;
  font-weight: 800;
  color: #e6f7ff;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  margin-top: 6px;
  font-size: 12px;
  color: #6f9ab5;
}

.tv-panel {
  flex: 1;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 14px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #d8ecf7;
  margin-bottom: 12px;
}

/* 深色表格定制 */
.dark-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(0, 212, 255, 0.08);
  --el-table-header-text-color: #9fd4ea;
  --el-table-text-color: #c9e3f0;
  --el-table-border-color: rgba(255, 255, 255, 0.12);
  --el-table-row-hover-bg-color: rgba(0, 212, 255, 0.1);
}

.tv-route-panel {
  margin-top: 16px;
}

.step-flow {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow-x: auto;
  padding: 6px 2px;
}

.step-node {
  min-width: 150px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  padding: 10px 14px;
  text-align: center;
  flex-shrink: 0;
}

.step-node.inspect {
  border-color: #ffb020;
  box-shadow: 0 0 8px rgba(255, 176, 32, 0.35);
}

.step-seq {
  font-size: 12px;
  color: #00d4ff;
  font-weight: 700;
}

.step-name {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #d8ecf7;
}

.step-min {
  margin-top: 4px;
  font-size: 12px;
  color: #6f9ab5;
}

.step-ws {
  margin-top: 2px;
  font-size: 12px;
  color: #8fc7e8;
}

.step-arrow {
  color: #00d4ff;
  font-size: 18px;
  flex-shrink: 0;
}
</style>
