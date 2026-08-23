import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import router from '@/router'
import { authApi } from '@/api'
import type { MenuNode } from '@/api/types'

/**
 * 动态路由菜单 store（第 5 周）：登录后从 GET /auth/menus 拉本人菜单树，
 * 按树注册动态路由（组件用 import.meta.glob 按 /path -> /src/views/path/index.vue 约定反查），
 * 侧边栏递归渲染同一棵树。
 *
 * 关键设计：
 * - pending Promise 并发去重：守卫可能连续触发多次，只发一次请求
 * - epoch 会话版本号：菜单拉取途中退出登录/换账号（401 踢下线）时，
 *   迟到的响应直接丢弃，防止 A 用户的菜单树挂到 B 用户的会话上
 * - 菜单接口失败降级：用本地全量静态菜单树继续注册路由（只降级数据不降级页面，
 *   避免「接口挂了整个系统白屏」——与 AI 双档降级同一精神），后端权限注解仍是真防线
 * - reset()：退出/401 时 removeRoute 逐个清理动态路由（防换账号残留旧角色路由）
 */
const FALLBACK_MENUS: MenuNode[] = [
  {
    id: '1', parentId: '0', menuName: '基础资料', menuType: 'M', icon: 'Box', children: [
      { id: '101', parentId: '1', menuName: '产品管理', menuType: 'C', path: '/products', icon: 'Goods', children: [] },
      { id: '102', parentId: '1', menuName: '物料管理', menuType: 'C', path: '/materials', icon: 'Files', children: [] },
      { id: '103', parentId: '1', menuName: '工序管理', menuType: 'C', path: '/processes', icon: 'SetUp', children: [] },
      { id: '104', parentId: '1', menuName: '工位管理', menuType: 'C', path: '/workstations', icon: 'Monitor', children: [] },
      { id: '105', parentId: '1', menuName: 'BOM 管理', menuType: 'C', path: '/boms', icon: 'Tickets', children: [] },
      { id: '106', parentId: '1', menuName: '工艺路线', menuType: 'C', path: '/routes', icon: 'Connection', children: [] },
      { id: '107', parentId: '1', menuName: '设备管理', menuType: 'C', path: '/equipment', icon: 'Cpu', children: [] },
    ],
  },
  {
    id: '2', parentId: '0', menuName: '生产管理', menuType: 'M', icon: 'Operation', children: [
      { id: '201', parentId: '2', menuName: '工单管理', menuType: 'C', path: '/work-orders', icon: 'Document', children: [] },
      { id: '202', parentId: '2', menuName: '工序任务', menuType: 'C', path: '/tasks', icon: 'List', children: [] },
      { id: '203', parentId: '2', menuName: '报工记录', menuType: 'C', path: '/reports', icon: 'DataLine', children: [] },
      { id: '204', parentId: '2', menuName: '追溯查询', menuType: 'C', path: '/traces', icon: 'Search', children: [] },
      { id: '205', parentId: '2', menuName: '生产看板', menuType: 'C', path: '/dashboard', icon: 'DataAnalysis', children: [] },
    ],
  },
  {
    id: '3', parentId: '0', menuName: '质量管理', menuType: 'M', icon: 'Stamp', children: [
      { id: '301', parentId: '3', menuName: '质检任务', menuType: 'C', path: '/inspection-tasks', icon: 'CircleCheck', children: [] },
      { id: '302', parentId: '3', menuName: '不良记录', menuType: 'C', path: '/defects', icon: 'Warning', children: [] },
      { id: '303', parentId: '3', menuName: '异常管理', menuType: 'C', path: '/exceptions', icon: 'AlarmClock', children: [] },
    ],
  },
  {
    id: '4', parentId: '0', menuName: 'AI 应用', menuType: 'M', icon: 'MagicStick', children: [
      { id: '401', parentId: '4', menuName: 'AI 助手', menuType: 'C', path: '/ai-chat', icon: 'ChatDotRound', children: [] },
      { id: '402', parentId: '4', menuName: '工厂知识库', menuType: 'C', path: '/knowledge', icon: 'Collection', children: [] },
      { id: '403', parentId: '4', menuName: '异常建议助手', menuType: 'C', path: '/ai-assistant', icon: 'Opportunity', children: [] },
      { id: '404', parentId: '4', menuName: '生产日报助手', menuType: 'C', path: '/ai-daily', icon: 'Document', children: [] },
    ],
  },
  {
    id: '5', parentId: '0', menuName: '系统集成', menuType: 'M', icon: 'Link', children: [
      { id: '501', parentId: '5', menuName: 'ERP 订单', menuType: 'C', path: '/erp-orders', icon: 'ShoppingCart', children: [] },
      { id: '502', parentId: '5', menuName: 'WMS 库存', menuType: 'C', path: '/inventory', icon: 'OfficeBuilding', children: [] },
    ],
  },
  { id: '500', parentId: '0', menuName: '电视 Demo 大屏', menuType: 'C', path: '/tv-demo', icon: 'VideoPlay', children: [] },
]

// 组件映射：按 path 约定反查视图组件（/products -> /src/views/products/index.vue），
// 新页面零注册成本（第 5 周 21 个页面全部满足约定）
const viewModules = import.meta.glob('@/views/**/index.vue')

export const useMenuStore = defineStore('menu', () => {
  const menus = ref<MenuNode[]>([])
  const loaded = ref(false)
  const pending = ref<Promise<MenuNode[]> | null>(null)
  const addedNames = ref<string[]>([])
  /** 会话版本号：reset（退出/401）时自增，迟到的菜单响应据此丢弃 */
  let epoch = 0

  async function init(): Promise<MenuNode[]> {
    if (pending.value) return pending.value
    if (loaded.value) return menus.value
    const myEpoch = epoch
    pending.value = authApi.menus()
      .then((tree) => ({ tree }))
      // 菜单接口失败降级：本地全量静态树继续渲染+注册（后端权限注解仍是真防线）
      .catch(() => ({ tree: FALLBACK_MENUS }))
      .then(({ tree }) => {
        if (myEpoch === epoch) {
          menus.value = tree
          buildRoutes(tree)
          loaded.value = true
        }
        return tree
      })
      .finally(() => {
        pending.value = null
      })
    return pending.value
  }

  function buildRoutes(tree: MenuNode[]) {
    const walk = (nodes: MenuNode[]) => {
      for (const node of nodes) {
        if (node.menuType === 'C' && node.path) {
          const viewKey = `/src/views${node.path}/index.vue`
          // 路径约定反查不到组件的菜单（如后端配错 path）静默跳过，不阻塞整树注册
          if (viewKey in viewModules) {
            const component = viewModules[viewKey]
            const name = node.path.slice(1)
            // 重复注册前先移除：重新登录时 meta/组件以最新菜单为准
            if (router.hasRoute(name)) {
              router.removeRoute(name)
            }
            router.addRoute('layout', {
              path: node.path,
              name,
              component,
              meta: { title: node.menuName, perm: node.perm },
            })
            addedNames.value.push(name)
          }
        }
        walk(node.children ?? [])
      }
    }
    walk(tree)
    // 404 catch-all 最后注册：动态路由注册完毕前，无权限深链匹配不到也不空白
    const notFound: RouteRecordRaw = {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/not-found/index.vue'),
      meta: { title: '页面不存在' },
    }
    if (router.hasRoute('not-found')) {
      router.removeRoute('not-found')
    }
    router.addRoute(notFound)
    addedNames.value.push('not-found')
  }

  /** 清理动态路由：退出登录/401 踢下线时调用（防换账号残留旧角色路由） */
  function reset() {
    epoch++
    for (const name of addedNames.value) {
      if (router.hasRoute(name)) {
        router.removeRoute(name)
      }
    }
    addedNames.value = []
    menus.value = []
    loaded.value = false
    pending.value = null
  }

  return { menus, loaded, init, reset }
})
