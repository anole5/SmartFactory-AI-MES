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
