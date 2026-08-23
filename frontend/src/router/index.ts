import { createRouter, createWebHistory } from 'vue-router'
import { useMenuStore } from '@/stores/menu'

/**
 * 静态骨架（第 5 周动态路由改造后）：仅登录页 + 布局根路由。
 * 业务页不再静态注册——登录后由 stores/menu 按后端菜单树动态 addRoute。
 * '/' redirect 改 '/dashboard'：动态化后 operator/qa 无产品菜单，原 '/products' 是死链，
 * 生产看板（205）全角色都有。
 */
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
      name: 'layout',
      component: () => import('@/layout/index.vue'),
      redirect: '/dashboard',
      children: [],
    },
  ],
})

// 全局前置守卫：无 token 跳登录页；登录后首次导航拉菜单树并注册动态路由
router.beforeEach(async (to) => {
  const token = localStorage.getItem('mes_token')
  if (to.path === '/login') {
    return token ? { path: '/' } : true
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 守卫回调体内取 store：store 与 router 相互引用，顶层取用会踩模块初始化时序
  const menuStore = useMenuStore()
  if (!menuStore.loaded) {
    await menuStore.init()
    // 关键一步：注册完成后带着原目标重新进守卫（redirect/动态路由都需重新解析）
    return { ...to, replace: true }
  }
  return true
})

// 页面标题（动态路由的 meta.title 来自后端菜单名）
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - SmartFactory MES` : 'SmartFactory MES'
})

export default router
