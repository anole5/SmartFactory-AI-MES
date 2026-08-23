import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/index.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layout/index.vue'),
      redirect: '/products',
      children: [
        {
          path: 'products',
          name: 'products',
          component: () => import('@/views/products/index.vue'),
          meta: { title: '产品管理' },
        },
        {
          path: 'materials',
          name: 'materials',
          component: () => import('@/views/materials/index.vue'),
          meta: { title: '物料管理' },
        },
        {
          path: 'processes',
          name: 'processes',
          component: () => import('@/views/processes/index.vue'),
          meta: { title: '工序管理' },
        },
        {
          path: 'workstations',
          name: 'workstations',
          component: () => import('@/views/workstations/index.vue'),
          meta: { title: '工位管理' },
        },
        {
          path: 'boms',
          name: 'boms',
          component: () => import('@/views/boms/index.vue'),
          meta: { title: 'BOM 管理' },
        },
        {
          path: 'routes',
          name: 'routes',
          component: () => import('@/views/routes/index.vue'),
          meta: { title: '工艺路线' },
        },
        {
          path: 'equipment',
          name: 'equipment',
          component: () => import('@/views/equipment/index.vue'),
          meta: { title: '设备管理' },
        },
        {
          path: 'work-orders',
          name: 'work-orders',
          component: () => import('@/views/work-orders/index.vue'),
          meta: { title: '生产工单' },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('@/views/tasks/index.vue'),
          meta: { title: '工序任务' },
        },
        {
          path: 'reports',
          name: 'reports',
          component: () => import('@/views/reports/index.vue'),
          meta: { title: '报工记录' },
        },
        {
          path: 'inspection-tasks',
          name: 'inspection-tasks',
          component: () => import('@/views/inspection-tasks/index.vue'),
          meta: { title: '质检任务' },
        },
        {
          path: 'defects',
          name: 'defects',
          component: () => import('@/views/defects/index.vue'),
          meta: { title: '不良记录' },
        },
        {
          path: 'exceptions',
          name: 'exceptions',
          component: () => import('@/views/exceptions/index.vue'),
          meta: { title: '异常管理' },
        },
        {
          path: 'ai-chat',
          name: 'ai-chat',
          component: () => import('@/views/ai-chat/index.vue'),
          meta: { title: 'AI 助手' },
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/knowledge/index.vue'),
          meta: { title: '工厂知识库' },
        },
        {
          path: 'ai-assistant',
          name: 'ai-assistant',
          component: () => import('@/views/ai-assistant/index.vue'),
          meta: { title: '异常建议助手' },
        },
        {
          path: 'ai-daily',
          name: 'ai-daily',
          component: () => import('@/views/ai-daily/index.vue'),
          meta: { title: '生产日报助手' },
        },
        {
          path: 'traces',
          name: 'traces',
          component: () => import('@/views/traces/index.vue'),
          meta: { title: '追溯查询' },
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { title: '生产看板' },
        },
        {
          path: 'tv-demo',
          name: 'tv-demo',
          component: () => import('@/views/tv-demo/index.vue'),
          meta: { title: '电视 Demo' },
        },
        {
          path: 'erp-orders',
          name: 'erp-orders',
          component: () => import('@/views/erp-orders/index.vue'),
          meta: { title: 'ERP 订单' },
        },
        {
          path: 'inventory',
          name: 'inventory',
          component: () => import('@/views/inventory/index.vue'),
          meta: { title: 'WMS 库存' },
        },
      ],
    },
  ],
})

// 全局前置守卫：无 token 跳登录页（第 2 周改为校验后端接口的 401 响应 + 权限菜单）
router.beforeEach((to) => {
  const token = localStorage.getItem('mes_token')
  if (to.path !== '/login' && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/' }
  }
  return true
})

// 页面标题
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - SmartFactory MES` : 'SmartFactory MES'
})

export default router
