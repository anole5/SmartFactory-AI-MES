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
          path: 'tv-demo',
          name: 'tv-demo',
          component: () => import('@/views/tv-demo/index.vue'),
          meta: { title: '电视 Demo' },
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
