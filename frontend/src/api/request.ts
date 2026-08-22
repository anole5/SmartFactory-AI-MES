import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { ApiResult } from './types'

// axios 实例：baseURL 走 Vite 代理（/api -> http://localhost:8080，后端 context-path 也是 /api）
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截器：统一携带 token（第 2 周接真实 JWT + 过期刷新）
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('mes_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一解包 {code,message,data}，业务失败弹错误提示
request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    if (body.code !== 0) {
      if (body.code === 401) {
        // 登录失效：清 token 回登录页（防御分支，常规 401 走下方 error 分支）
        clearLogin()
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    // 直接解包出 data，调用方不用再 .data.data
    return body.data as never
  },
  (error) => {
    // HTTP 层错误：后端 401/403/409/400 的 body 也是统一结构，优先取后端 message
    if (error.response?.status === 401) {
      // 登录失效（token 过期/伪造/用户停用）：清登录态，带 redirect 回登录页
      clearLogin()
    }
    const msg: string = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

/** 清除本地登录态并跳转登录页（带 redirect 参数，登录后回原页面） */
function clearLogin() {
  localStorage.removeItem('mes_token')
  localStorage.removeItem('mes_user')
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
}

// 泛型方法：把拦截器解包后的 data 转成期望类型
export function httpGet<T>(url: string, params?: object): Promise<T> {
  return request.get(url, { params }) as unknown as Promise<T>
}

export function httpPost<T>(url: string, data?: object): Promise<T> {
  return request.post(url, data) as unknown as Promise<T>
}

export function httpPut<T>(url: string, data?: object): Promise<T> {
  return request.put(url, data) as unknown as Promise<T>
}

export function httpDelete<T>(url: string): Promise<T> {
  return request.delete(url) as unknown as Promise<T>
}
