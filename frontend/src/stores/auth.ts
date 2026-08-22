import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginResult } from '@/api/types'

/**
 * 登录态：token + 用户信息（含角色/权限集合）持久化到 localStorage，刷新不丢
 *
 * <p>第 2 周起登录返回真实 JWT + RBAC 权限集合；权限判断只做按钮级显隐，
 * 真正的防线是后端拦截器（前端指令只是体验优化）。</p>
 */
export interface UserInfo {
  userId: string
  username: string
  realName?: string
  roles: string[]
  permissions: string[]
}

const USER_KEY = 'mes_user'

function readUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserInfo) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('mes_token') ?? '')
  const userInfo = ref<UserInfo | null>(readUser())

  function setLogin(result: LoginResult) {
    token.value = result.token
    userInfo.value = {
      userId: result.userId,
      username: result.username,
      realName: result.realName,
      roles: result.roles ?? [],
      permissions: result.permissions ?? [],
    }
    localStorage.setItem('mes_token', result.token)
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
  }

  /**
   * 按钮级权限判断：传单个权限标识或数组（数组任一命中即放行）。
   * SUPER_ADMIN 直接放行——管理员权限集合理论上全量，双保险避免漏配按钮。
   */
  function hasPerm(perm: string | string[]): boolean {
    if (!userInfo.value) return false
    if (userInfo.value.roles.includes('SUPER_ADMIN')) return true
    const perms = Array.isArray(perm) ? perm : [perm]
    return perms.some((p) => userInfo.value!.permissions.includes(p))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('mes_token')
    localStorage.removeItem(USER_KEY)
  }

  return { token, userInfo, setLogin, hasPerm, logout }
})
