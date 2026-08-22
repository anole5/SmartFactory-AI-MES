import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 按钮级权限指令：
 *   v-permission="'production:work-order:release'"          单权限
 *   v-permission="['production:work-order:release', '...']"  多权限任一命中即显示
 * 无权限时直接从 DOM 移除元素。后端拦截器是真正的安全边界，
 * 本指令只负责「没权限的人不看到按钮」的体验。
 */
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const auth = useAuthStore()
    if (!auth.hasPerm(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}
