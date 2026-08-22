import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 登录态（第 1 周简化版：token 存 localStorage；
 * 第 2 周接真实用户表 + JWT 时把用户信息/权限也放这里）
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('mes_token') ?? '')
  const username = ref(localStorage.getItem('mes_username') ?? '')

  function setLogin(newToken: string, name: string) {
    token.value = newToken
    username.value = name
    localStorage.setItem('mes_token', newToken)
    localStorage.setItem('mes_username', name)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('mes_token')
    localStorage.removeItem('mes_username')
  }

  return { token, username, setLogin, logout }
})
