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
  INSPECT_TASK: '生成质检任务',
  INSPECT: '质检录入',
  DEFECT: '不良登记',
  EXCEPTION_CREATE: '创建异常单',
  EXCEPTION_PROCESS: '处理异常',
  EXCEPTION_CLOSE: '关闭异常',
  AI_SUGGEST: 'AI 处理建议回写',
  BATCH_BIND: '关键件批次绑定',
  ERP_DONE: '工单完工回传 ERP',
  WMS_FINISHED_IN: '成品完工入库',
}

/** 质检任务状态机（第 3 周） */
export const INSPECTION_TASK_STATUS: Record<string, string> = {
  PENDING: '待检验',
  INSPECTING: '检验中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

/** 异常单状态机（第 3 周） */
export const EXCEPTION_STATUS: Record<string, string> = {
  OPEN: '待处理',
  PROCESSING: '处理中',
  CLOSED: '已关闭',
}

/** 异常单来源（第 3 周） */
export const EXCEPTION_SOURCE_TYPE: Record<string, string> = {
  DEFECT: '不良生成',
  MANUAL: '手工创建',
}

/** 设备状态（第 3 周：状态漂移模拟，任意切换） */
export const EQUIPMENT_STATUS: Record<string, string> = {
  RUNNING: '运行',
  IDLE: '空闲',
  STOPPED: '停机',
  MAINTENANCE: '维护',
}

/** 不良编码字典（检验录入下拉，对齐演示文档异常清单） */
export const DEFECT_CODES: Record<string, string> = {
  BLACK_SCREEN: '黑屏',
  FLOWER_SCREEN: '花屏',
  NO_SOUND: '无声音',
  HDMI_ABNORMAL: 'HDMI 接口异常',
  BURN_FAIL: '烧录失败',
  AGING_RESTART: '老化重启',
  ACCESSORY_MISSING: '附件缺失',
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
  INSPECTING: 'primary',
  OPEN: 'danger',
  PROCESSING: 'warning',
  IDLE: 'info',
  STOPPED: 'danger',
  MAINTENANCE: 'warning',
  SYNCED: 'warning',
  DONE: 'success',
  IN: 'success',
  OUT: 'warning',
}

/** 字典取值（找不到时原样返回，避免字典漏配时页面空白） */
export function labelOf(dict: Record<string, string>, code?: string): string {
  return (code && dict[code]) || code || '-'
}

export function tagTypeOf(code?: string): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  return (code && STATUS_TAG_TYPE[code]) || 'info'
}

/** 知识库文档类型（第 4 周） */
export const KNOWLEDGE_DOC_TYPE: Record<string, string> = {
  SOP: '作业指导书',
  QUALITY_STANDARD: '质量标准',
  EQUIPMENT_MANUAL: '设备手册',
  FAULT_GUIDE: '故障指南',
}

/** 知识库文档状态（第 4 周） */
export const KNOWLEDGE_DOC_STATUS: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

/** AI 助手意图（第 4 周） */
export const AI_INTENT: Record<string, string> = {
  OVERVIEW: '生产概况',
  KNOWLEDGE: '知识库',
  EXCEPTION: '异常建议',
  REPORT: '生产日报',
}

/** 外部订单状态机（第 5 周 ERP 集成） */
export const EXTERNAL_ORDER_STATUS: Record<string, string> = {
  PENDING: '待转工单',
  SYNCED: '已转工单',
  DONE: '已完成',
}

/** 库存条目类型（第 5 周 WMS） */
export const ITEM_TYPE: Record<string, string> = {
  MATERIAL: '物料',
  FINISHED: '成品',
}

/** 库存流水方向（第 5 周 WMS） */
export const STOCK_TX_TYPE: Record<string, string> = {
  IN: '入库',
  OUT: '出库',
}

/** 库存业务类型（第 5 周 WMS） */
export const STOCK_BIZ_TYPE: Record<string, string> = {
  PURCHASE_IN: '采购入库',
  PICK_OUT: '工单领料',
  FINISHED_IN: '完工入库',
}
