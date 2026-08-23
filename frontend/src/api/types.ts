// 后端接口类型定义
// 注意：后端 JacksonConfig 将 Long 序列化为字符串（防 JS 丢精度），所以 id/total 一律 string

/** 统一响应包装 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  requestId: string
}

/** 统一分页结构 */
export interface PageResult<T> {
  records: T[]
  total: string
  current: string
  size: string
}

/** 通用分页查询参数 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

// ---------- 产品 ----------
export interface Product {
  id: string
  productCode: string
  productName: string
  productType?: string
  specification?: string
  unit?: string
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface ProductSave {
  productCode: string
  productName: string
  productType?: string
  specification?: string
  unit?: string
}

export interface ProductQuery extends PageQuery {
  keyword?: string
  status?: string
}

// ---------- 物料 ----------
export interface Material {
  id: string
  materialCode: string
  materialName: string
  materialType?: string
  unit?: string
  traceRequired: boolean
  status: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface MaterialSave {
  materialCode: string
  materialName: string
  materialType?: string
  unit?: string
  traceRequired?: boolean
  remark?: string
}

export interface MaterialQuery extends PageQuery {
  keyword?: string
  status?: string
  materialType?: string
}

// ---------- 工序 ----------
export interface Process {
  id: string
  processCode: string
  processName: string
  needInspection: boolean
  standardMinutes: number
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface ProcessSave {
  processCode: string
  processName: string
  needInspection?: boolean
  standardMinutes: number
  description?: string
}

export interface ProcessQuery extends PageQuery {
  keyword?: string
}

// ---------- 工位 ----------
export interface Workstation {
  id: string
  workstationCode: string
  workstationName: string
  equipmentCode?: string
  equipmentName?: string
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface WorkstationSave {
  workstationCode: string
  workstationName: string
  equipmentCode?: string
  equipmentName?: string
}

export interface WorkstationQuery extends PageQuery {
  keyword?: string
  status?: string
}

// ---------- BOM ----------
export interface BomItem {
  id: string
  lineNo: number
  materialId: string
  materialCodeSnapshot: string
  materialNameSnapshot: string
  unitSnapshot: string
  requiredQty: number
  lossRate: number
  remark?: string
}

export interface BomItemSave {
  materialId: string
  requiredQty: number
  lossRate?: number
  remark?: string
}

export interface Bom {
  id: string
  bomNo: string
  productId: string
  productCode?: string
  productName?: string
  version?: string
  status: string
  effectiveDate?: string
  remark?: string
  items?: BomItem[]
  createdAt?: string
  updatedAt?: string
}

export interface BomSave {
  productId: string
  version?: string
  effectiveDate?: string
  remark?: string
  items: BomItemSave[]
}

export interface BomQuery extends PageQuery {
  keyword?: string
  productId?: string
  status?: string
}

// ---------- 工艺路线 ----------
export interface RouteStep {
  id: string
  sequenceNo: number
  processId: string
  processCodeSnapshot: string
  processNameSnapshot: string
  workstationId?: string | null
  workstationCode?: string
  workstationName?: string
  needInspection: boolean
  standardMinutes: number
  remark?: string
}

export interface RouteStepSave {
  processId: string
  workstationId?: string | null
  needInspection?: boolean
  remark?: string
}

export interface Route {
  id: string
  routeNo: string
  productId: string
  productCode?: string
  productName?: string
  version?: string
  status: string
  remark?: string
  steps?: RouteStep[]
  createdAt?: string
  updatedAt?: string
}

export interface RouteSave {
  productId: string
  version?: string
  remark?: string
  steps: RouteStepSave[]
}

export interface RouteQuery extends PageQuery {
  keyword?: string
  productId?: string
  status?: string
}

// ---------- 登录 ----------
export interface LoginResult {
  token: string
  username: string
  /** Long 序列化为字符串 */
  userId: string
  /** 真实姓名/昵称（顶栏展示） */
  realName?: string
  /** 角色编码集合，如 ["SUPER_ADMIN"] */
  roles?: string[]
  /** 权限标识集合，如 ["production:work-order:release"] */
  permissions?: string[]
}

/** 启用用户下拉项（派工弹窗选择操作员） */
export interface UserOption {
  id: string
  username: string
  realName?: string
}

// ---------- 生产工单 ----------
export interface WorkOrder {
  id: string
  workOrderNo: string
  externalOrderNo?: string
  productId: string
  productCodeSnapshot?: string
  productNameSnapshot?: string
  bomId: string
  routeId: string
  planQty: number
  completedQty: number
  goodQty: number
  defectQty: number
  status: string
  priority: string
  planStartTime?: string
  planEndTime?: string
  actualStartTime?: string
  actualEndTime?: string
  remark?: string
  tasks?: OperationTask[]
  reportCount?: string
  createdAt?: string
  updatedAt?: string
}

export interface WorkOrderSave {
  productId: string
  planQty: number
  externalOrderNo?: string
  priority?: string
  planStartTime?: string
  planEndTime?: string
  remark?: string
}

export interface WorkOrderQuery extends PageQuery {
  keyword?: string
  productId?: string
  status?: string
  planStartFrom?: string
  planEndTo?: string
}

// ---------- 工序任务 ----------
export interface OperationTask {
  id: string
  taskNo: string
  workOrderId: string
  workOrderNo?: string
  processId: string
  processCodeSnapshot?: string
  processNameSnapshot?: string
  sequenceNo: number
  workstationId?: string | null
  workstationCode?: string
  workstationName?: string
  operatorId?: string | null
  operatorName?: string
  equipmentCodeSnapshot?: string
  equipmentNameSnapshot?: string
  planQty: number
  completedQty: number
  goodQty: number
  defectQty: number
  status: string
  needInspection: boolean
  standardMinutes?: number
  startTime?: string
  endTime?: string
  createdAt?: string
}

export interface TaskAssign {
  operatorId: string
  workstationId?: string
}

export interface TaskQuery extends PageQuery {
  workOrderId?: string
  status?: string
  workstationId?: string
  operatorId?: string
}

// ---------- 报工 ----------
export interface WorkReport {
  id: string
  reportNo: string
  workOrderId: string
  workOrderNo?: string
  taskId: string
  taskNo?: string
  processNameSnapshot?: string
  operatorId: string
  operatorName?: string
  productBatchNo?: string
  reportQty: number
  goodQty: number
  defectQty: number
  startTime?: string
  endTime?: string
  remark?: string
  createdAt?: string
}

export interface WorkReportSave {
  taskId: string
  reportQty: number
  goodQty: number
  defectQty: number
  productBatchNo?: string
  startTime?: string
  endTime?: string
  remark?: string
}

export interface WorkReportQuery extends PageQuery {
  workOrderId?: string
  taskId?: string
  operatorId?: string
}

// ---------- 追溯 ----------
export interface TraceRecord {
  id: string
  traceNo: string
  workOrderId: string
  taskId?: string | null
  actionType: string
  actionTime: string
  operatorId: string
  operatorName?: string
  actionDetail?: string
}

// ---------- 质检任务（第 3 周） ----------
export interface InspectionTask {
  id: string
  inspectionTaskNo: string
  workOrderId: string
  workOrderNo?: string
  operationTaskId: string
  processCodeSnapshot?: string
  processNameSnapshot?: string
  workstationId?: string | null
  planQty: number
  inspectedQty: number
  goodQty: number
  defectQty: number
  status: string
  inspectorId?: string | null
  inspectorName?: string
  startTime?: string
  endTime?: string
  remark?: string
}

export interface InspectionTaskQuery extends PageQuery {
  workOrderId?: string
  status?: string
  keyword?: string
}

// ---------- 质检记录（第 3 周） ----------
export interface InspectionRecord {
  id: string
  inspectionRecordNo: string
  inspectionTaskId: string
  workOrderId: string
  operationTaskId: string
  goodQty: number
  defectQty: number
  inspectTime?: string
  inspectorId: string
  inspectorName?: string
  remark?: string
}

/** 检验录入不良行 */
export interface DefectItem {
  defectCode: string
  defectQty: number
  remark?: string
}

export interface InspectionRecordSave {
  inspectionTaskId: string
  goodQty: number
  defectQty: number
  defectItems?: DefectItem[]
  remark?: string
}

// ---------- 不良记录（第 3 周） ----------
export interface DefectRecord {
  id: string
  defectNo: string
  inspectionRecordId: string
  inspectionTaskId: string
  workOrderId: string
  workOrderNo?: string
  operationTaskId: string
  processCodeSnapshot?: string
  processNameSnapshot?: string
  defectCode: string
  defectQty: number
  remark?: string
}

export interface DefectQuery extends PageQuery {
  workOrderId?: string
  defectCode?: string
  keyword?: string
}

// ---------- 异常单（第 3 周） ----------
export interface ExceptionOrder {
  id: string
  exceptionNo: string
  sourceType: string
  defectRecordId?: string | null
  defectNo?: string
  workOrderId?: string | null
  workOrderNo?: string
  operationTaskId?: string | null
  inspectionTaskId?: string | null
  defectCode?: string
  description: string
  status: string
  handlerId?: string | null
  handlerName?: string
  resolveRemark?: string
  resolvedAt?: string
  createdAt?: string
}

export interface ExceptionSave {
  description: string
  workOrderId?: string
  operationTaskId?: string
  inspectionTaskId?: string
  defectCode?: string
}

export interface ExceptionQuery extends PageQuery {
  workOrderId?: string
  status?: string
  keyword?: string
}

// ---------- 设备（第 3 周：状态漂移模拟） ----------
export interface Equipment {
  id: string
  equipmentCode: string
  equipmentName: string
  model?: string
  workstationId?: string | null
  workstationName?: string
  status: string
  remark?: string
  createdAt?: string
}

export interface EquipmentSave {
  equipmentCode: string
  equipmentName: string
  model?: string
  workstationId?: string | null
  remark?: string
}

export interface EquipmentQuery extends PageQuery {
  keyword?: string
  workstationId?: string
  status?: string
}

// ---------- 整机 SN（第 3 周） ----------
export interface Sn {
  id: string
  sn: string
  workOrderId: string
  workOrderNo?: string
  productId: string
  productCodeSnapshot?: string
  productNameSnapshot?: string
  reportId?: string | null
  reportNo?: string
  createdAt?: string
}

export interface SnQuery extends PageQuery {
  workOrderId?: string
  keyword?: string
}

/** 按 SN 追溯结果：出生信息 + 工单全时间线 */
export interface SnTrace {
  id: string
  sn: string
  workOrderId: string
  workOrderNo?: string
  workOrderStatus?: string
  productCodeSnapshot?: string
  productNameSnapshot?: string
  reportId?: string | null
  reportNo?: string
  createdAt?: string
  timeline: TraceRecord[]
}

/** 按批次号追溯结果：批次全部报工 + 涉及工单（去重） */
export interface BatchTrace {
  reports: WorkReport[]
  workOrders: WorkOrder[]
}

// ---------- 生产看板（第 3 周） ----------
/** 状态计数（设备分布，Long 序列化为字符串） */
export interface StatusCount {
  status: string
  count: string
}

export interface DashboardSummary {
  todayOutputQty: string
  todayReportCount: string
  todayDefectQty: string
  /** 今日良率百分比，无数据时 null */
  todayYieldRate: number | null
  inProgressWorkOrderCount: string
  openExceptionCount: string
  equipmentStatusCounts: StatusCount[]
}

export interface DashboardWorkOrderItem {
  id: string
  workOrderNo: string
  productCodeSnapshot?: string
  productNameSnapshot?: string
  planQty: number
  completedQty: number
  status: string
  progressPercent: number
}

export interface ProcessYield {
  processName: string
  goodQty: string
  defectQty: string
  /** 工序良率百分比，无数据时 null */
  yieldRate: number | null
}

export interface DefectCount {
  defectCode: string
  count: string
}

export interface DashboardQuality {
  overallYieldRate: number | null
  processYields: ProcessYield[]
  defectDistribution: DefectCount[]
}

export interface DashboardEquipmentRow {
  equipmentCode: string
  equipmentName: string
  status: string
  workstationName?: string
}

export interface DashboardEquipment {
  equipment: DashboardEquipmentRow[]
  statusCounts: StatusCount[]
}

// ---------- AI 知识库（第 4 周） ----------
export interface AiReference {
  docId: string
  docName: string
}

/** 知识库问答出参（POST /ai/knowledge/ask） */
export interface AiAskResult {
  answer: string
  references: AiReference[]
  fallback: boolean
  recordId?: string
}

/** 统一 AI 助手出参（POST /ai/chat） */
export interface AiChatResult {
  intent: string
  answer: string
  references?: AiReference[]
  fallback?: boolean
  recordId?: string
  exceptionId?: string
  reportDate?: string
  summary?: string
}

export interface KnowledgeDoc {
  id: string
  docName: string
  docType: string
  keywords: string
  content: string
  status: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeDocQuery extends PageQuery {
  keyword?: string
  docType?: string
  status?: string
}

export interface KnowledgeDocSave {
  docName: string
  docType: string
  keywords: string
  content: string
  status?: string
  remark?: string
}

// ---------- AI 异常建议（第 4 周） ----------
export interface ExceptionSuggestion {
  exceptionId: string
  exceptionNo: string
  suggestion?: string
  fallback?: boolean
}

// ---------- AI 生产日报（第 4 周） ----------
export interface DailyPreview {
  reportDate: string
  content: string
  summary?: string
  fallback?: boolean
}

export interface DailyReport {
  id: string
  reportDate: string
  content: string
  createdAt?: string
  updatedAt?: string
}

export interface DailyReportSave {
  reportDate: string
  content: string
}

export interface DailyReportQuery extends PageQuery {
  reportDate?: string
}

// ---------- 系统集成：ERP 外部订单（第 5 周） ----------
export interface ErpOrder {
  id: string
  externalOrderNo: string
  productId: string
  productCodeSnapshot: string
  productNameSnapshot: string
  planQty: number
  priority: string
  planStartTime?: string
  planEndTime?: string
  status: string
  workOrderId?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface ErpOrderQuery extends PageQuery {
  keyword?: string
  status?: string
}

/** 模拟下单入参（planStartTime/planEndTime 为 yyyy-MM-dd） */
export interface ErpOrderSave {
  productId: string
  planQty: number
  priority?: string
  planStartTime?: string
  planEndTime?: string
  remark?: string
}

// ---------- 系统集成：WMS 库存（第 5 周） ----------
export interface InventoryItem {
  id: string
  itemType: string
  itemRefId: string
  itemCode?: string
  itemName?: string
  unit?: string
  qty: number
  remark?: string
  updatedAt?: string
}

export interface InventoryQuery extends PageQuery {
  itemType?: string
  keyword?: string
}

export interface StockInSave {
  materialId: string
  qty: number
  remark?: string
}

export interface StockTx {
  id: string
  txNo: string
  txType: string
  itemType: string
  itemRefId: string
  itemCode?: string
  itemName?: string
  qty: number
  bizType: string
  workOrderId?: string
  remark?: string
  createdAt?: string
}

export interface StockTxQuery extends PageQuery {
  workOrderId?: string
  itemType?: string
  bizType?: string
}

export interface PickItem {
  materialId: string
  materialCode: string
  materialName: string
  needQty: number
  actualPickedQty: number
}

export interface PickResult {
  workOrderId: string
  workOrderNo: string
  items: PickItem[]
}

// ---------- 动态路由：菜单树节点（第 5 周，GET /auth/menus） ----------
export interface MenuNode {
  id: string
  parentId: string
  menuName: string
  /** M 目录 / C 菜单（F 按钮不进树） */
  menuType: string
  /** 前端路由路径（仅 C 级非空，如 /erp-orders） */
  path?: string
  perm?: string
  /** Element Plus 图标名（全量注册后按名字符串解析） */
  icon?: string
  orderNum?: number
  children: MenuNode[]
}
