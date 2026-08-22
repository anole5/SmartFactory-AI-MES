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

/** 状态对应的 el-tag 颜色类型 */
export const STATUS_TAG_TYPE: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
  ENABLED: 'success',
  DISABLED: 'info',
  DRAFT: 'info',
  ACTIVE: 'success',
  OBSOLETE: 'danger',
}

/** 字典取值（找不到时原样返回，避免字典漏配时页面空白） */
export function labelOf(dict: Record<string, string>, code?: string): string {
  return (code && dict[code]) || code || '-'
}

export function tagTypeOf(code?: string): 'success' | 'info' | 'warning' | 'danger' {
  return (code && STATUS_TAG_TYPE[code]) || 'info'
}
