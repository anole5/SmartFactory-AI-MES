// 状态字典：后端存英文 code，前端映射中文（第 2 周可改为后端下发字典表）

export const PRODUCT_STATUS: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

export const MATERIAL_STATUS: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

export const WORKSTATION_STATUS: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

/** BOM 与工艺路线共用同一状态机 */
export const DRAFT_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '生效',
  OBSOLETE: '作废',
}

/** 生产工单状态机（第 2 周） */
export const WORK_ORDER_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  RELEASED: '已下发',
  IN_PROGRESS: '生产中',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

/** 工序任务状态机（第 2 周） */
export const TASK_STATUS: Record<string, string> = {
  PENDING: '待派工',
  ASSIGNED: '已派工',
  RUNNING: '生产中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

/** 工单优先级 */
export const PRIORITY: Record<string, string> = {
  HIGH: '高',
  NORMAL: '普通',
  LOW: '低',
}

/** 追溯动作类型（工单详情时间线） */
export const ACTION_TYPE: Record<string, string> = {
  CREATE: '创建工单',
  RELEASE: '工单下发',
  ASSIGN: '派工',
  START: '开工',
  PAUSE: '暂停',
  RESUME: '继续',
  REPORT: '报工',
  CANCEL: '取消',
}

/** 状态对应的 el-tag 颜色类型 */
export const STATUS_TAG_TYPE: Record<string, 'success' | 'info' | 'warning' | 'danger' | 'primary'> = {
  ENABLED: 'success',
  DISABLED: 'info',
  DRAFT: 'info',
  ACTIVE: 'success',
  OBSOLETE: 'danger',
  RELEASED: 'warning',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger',
  PENDING: 'info',
  ASSIGNED: 'warning',
  RUNNING: 'primary',
  PAUSED: 'warning',
  HIGH: 'danger',
  NORMAL: 'info',
  LOW: 'info',
}

/** 字典取值（找不到时原样返回，避免字典漏配时页面空白） */
export function labelOf(dict: Record<string, string>, code?: string): string {
  return (code && dict[code]) || code || '-'
}

export function tagTypeOf(code?: string): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  return (code && STATUS_TAG_TYPE[code]) || 'info'
}
