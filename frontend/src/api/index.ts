// 各模块 API 集中定义（按后端 Controller 分组）
import { httpDelete, httpGet, httpPost, httpPut } from './request'
import type {
  AiAskResult,
  AiChatResult,
  BatchTrace,
  Bom,
  BomQuery,
  BomSave,
  DailyPreview,
  DailyReport,
  DailyReportQuery,
  DailyReportSave,
  DashboardEquipment,
  DashboardQuality,
  DashboardSummary,
  DashboardWorkOrderItem,
  DefectQuery,
  DefectRecord,
  Equipment,
  EquipmentQuery,
  EquipmentSave,
  ErpOrder,
  ErpOrderQuery,
  ErpOrderSave,
  ExceptionOrder,
  ExceptionQuery,
  ExceptionSave,
  ExceptionSuggestion,
  InspectionRecord,
  InspectionRecordSave,
  InspectionTask,
  InspectionTaskQuery,
  InventoryItem,
  InventoryQuery,
  KnowledgeDoc,
  KnowledgeDocQuery,
  KnowledgeDocSave,
  LoginResult,
  Material,
  MaterialQuery,
  MaterialSave,
  OperationTask,
  PageResult,
  PickResult,
  Process,
  ProcessQuery,
  ProcessSave,
  Product,
  ProductQuery,
  ProductSave,
  Route,
  RouteQuery,
  RouteSave,
  Sn,
  SnQuery,
  SnTrace,
  StockInSave,
  StockTx,
  StockTxQuery,
  TaskAssign,
  TaskQuery,
  TraceRecord,
  UserOption,
  WorkOrder,
  WorkOrderQuery,
  WorkOrderSave,
  WorkReport,
  WorkReportQuery,
  WorkReportSave,
  Workstation,
  WorkstationQuery,
  WorkstationSave,
} from './types'

// ---------- 认证 ----------
export const authApi = {
  login: (username: string, password: string) =>
    httpPost<LoginResult>('/auth/login', { username, password }),
  users: () => httpGet<UserOption[]>('/auth/users'),
}

// ---------- 生产工单 ----------
export const workOrderApi = {
  page: (params: WorkOrderQuery) => httpGet<PageResult<WorkOrder>>('/production/work-orders/page', params),
  detail: (id: string) => httpGet<WorkOrder>(`/production/work-orders/${id}`),
  create: (data: WorkOrderSave) => httpPost<string>('/production/work-orders', data),
  update: (id: string, data: WorkOrderSave) => httpPut<void>(`/production/work-orders/${id}`, data),
  release: (id: string) => httpPost<void>(`/production/work-orders/${id}/release`),
  cancel: (id: string) => httpPut<void>(`/production/work-orders/${id}/cancel`),
}

// ---------- 工序任务 ----------
export const taskApi = {
  page: (params: TaskQuery) => httpGet<PageResult<OperationTask>>('/production/tasks/page', params),
  listByWorkOrder: (workOrderId: string) =>
    httpGet<OperationTask[]>(`/production/tasks/for-work-order/${workOrderId}`),
  assign: (id: string, data: TaskAssign) => httpPut<void>(`/production/tasks/${id}/assign`, data),
  start: (id: string) => httpPut<void>(`/production/tasks/${id}/start`),
  pause: (id: string) => httpPut<void>(`/production/tasks/${id}/pause`),
  resume: (id: string) => httpPut<void>(`/production/tasks/${id}/resume`),
}

// ---------- 报工 ----------
export const reportApi = {
  page: (params: WorkReportQuery) => httpGet<PageResult<WorkReport>>('/production/reports/page', params),
  create: (data: WorkReportSave) => httpPost<void>('/production/reports', data),
}

// ---------- 追溯 ----------
export const traceApi = {
  listByWorkOrder: (workOrderId: string) =>
    httpGet<TraceRecord[]>('/production/traces', { workOrderId }),
  bySn: (sn: string) => httpGet<SnTrace>('/production/traces/sn', { sn }),
  byBatch: (batchNo: string) => httpGet<BatchTrace>('/production/traces/batch', { batchNo }),
}

// ---------- 整机 SN（第 3 周） ----------
export const snApi = {
  page: (params: SnQuery) => httpGet<PageResult<Sn>>('/production/sns/page', params),
}

// ---------- 产品 ----------
export const productApi = {
  page: (params: ProductQuery) => httpGet<PageResult<Product>>('/master/products/page', params),
  detail: (id: string) => httpGet<Product>(`/master/products/${id}`),
  create: (data: ProductSave) => httpPost<string>('/master/products', data),
  update: (id: string, data: ProductSave) => httpPut<void>(`/master/products/${id}`, data),
  changeStatus: (id: string, status: string) =>
    httpPut<void>(`/master/products/${id}/status`, { status }),
  remove: (id: string) => httpDelete<void>(`/master/products/${id}`),
}

// ---------- 物料 ----------
export const materialApi = {
  page: (params: MaterialQuery) => httpGet<PageResult<Material>>('/master/materials/page', params),
  detail: (id: string) => httpGet<Material>(`/master/materials/${id}`),
  create: (data: MaterialSave) => httpPost<string>('/master/materials', data),
  update: (id: string, data: MaterialSave) => httpPut<void>(`/master/materials/${id}`, data),
  changeStatus: (id: string, status: string) =>
    httpPut<void>(`/master/materials/${id}/status`, { status }),
  remove: (id: string) => httpDelete<void>(`/master/materials/${id}`),
}

// ---------- 工序 ----------
export const processApi = {
  page: (params: ProcessQuery) => httpGet<PageResult<Process>>('/master/processes/page', params),
  detail: (id: string) => httpGet<Process>(`/master/processes/${id}`),
  create: (data: ProcessSave) => httpPost<string>('/master/processes', data),
  update: (id: string, data: ProcessSave) => httpPut<void>(`/master/processes/${id}`, data),
  remove: (id: string) => httpDelete<void>(`/master/processes/${id}`),
}

// ---------- 工位 ----------
export const workstationApi = {
  page: (params: WorkstationQuery) =>
    httpGet<PageResult<Workstation>>('/master/workstations/page', params),
  detail: (id: string) => httpGet<Workstation>(`/master/workstations/${id}`),
  create: (data: WorkstationSave) => httpPost<string>('/master/workstations', data),
  update: (id: string, data: WorkstationSave) => httpPut<void>(`/master/workstations/${id}`, data),
  changeStatus: (id: string, status: string) =>
    httpPut<void>(`/master/workstations/${id}/status`, { status }),
  remove: (id: string) => httpDelete<void>(`/master/workstations/${id}`),
}

// ---------- BOM ----------
export const bomApi = {
  page: (params: BomQuery) => httpGet<PageResult<Bom>>('/master/boms/page', params),
  detail: (id: string) => httpGet<Bom>(`/master/boms/${id}`),
  create: (data: BomSave) => httpPost<string>('/master/boms', data),
  update: (id: string, data: BomSave) => httpPut<void>(`/master/boms/${id}`, data),
  changeStatus: (id: string, status: string) =>
    httpPut<void>(`/master/boms/${id}/status`, { status }),
  remove: (id: string) => httpDelete<void>(`/master/boms/${id}`),
}

// ---------- 工艺路线 ----------
export const routeApi = {
  page: (params: RouteQuery) => httpGet<PageResult<Route>>('/master/routes/page', params),
  detail: (id: string) => httpGet<Route>(`/master/routes/${id}`),
  create: (data: RouteSave) => httpPost<string>('/master/routes', data),
  update: (id: string, data: RouteSave) => httpPut<void>(`/master/routes/${id}`, data),
  changeStatus: (id: string, status: string) =>
    httpPut<void>(`/master/routes/${id}/status`, { status }),
  remove: (id: string) => httpDelete<void>(`/master/routes/${id}`),
}

// ---------- 生产看板（第 3 周） ----------
export const dashboardApi = {
  summary: () => httpGet<DashboardSummary>('/dashboard/summary'),
  workOrders: () => httpGet<DashboardWorkOrderItem[]>('/dashboard/work-orders'),
  quality: () => httpGet<DashboardQuality>('/dashboard/quality'),
  equipment: () => httpGet<DashboardEquipment>('/dashboard/equipment'),
}

// ---------- 设备（第 3 周） ----------
export const equipmentApi = {
  page: (params: EquipmentQuery) => httpGet<PageResult<Equipment>>('/master/equipment/page', params),
  detail: (id: string) => httpGet<Equipment>(`/master/equipment/${id}`),
  create: (data: EquipmentSave) => httpPost<string>('/master/equipment', data),
  update: (id: string, data: EquipmentSave) => httpPut<void>(`/master/equipment/${id}`, data),
  changeStatus: (id: string, status: string) =>
    httpPut<void>(`/master/equipment/${id}/status`, { status }),
}

// ---------- 质检任务（第 3 周） ----------
export const inspectionTaskApi = {
  page: (params: InspectionTaskQuery) =>
    httpGet<PageResult<InspectionTask>>('/quality/inspection-tasks/page', params),
  records: (id: string) => httpGet<InspectionRecord[]>(`/quality/inspection-tasks/${id}/records`),
  start: (id: string) => httpPut<void>(`/quality/inspection-tasks/${id}/start`),
}

// ---------- 质检录入（第 3 周） ----------
export const inspectionRecordApi = {
  create: (data: InspectionRecordSave) => httpPost<void>('/quality/inspection-records', data),
}

// ---------- 不良记录（第 3 周） ----------
export const defectApi = {
  page: (params: DefectQuery) => httpGet<PageResult<DefectRecord>>('/quality/defects/page', params),
  toException: (id: string) => httpPut<string>(`/quality/defects/${id}/to-exception`),
}

// ---------- 异常单（第 3 周） ----------
export const exceptionApi = {
  page: (params: ExceptionQuery) => httpGet<PageResult<ExceptionOrder>>('/quality/exceptions/page', params),
  create: (data: ExceptionSave) => httpPost<string>('/quality/exceptions', data),
  process: (id: string) => httpPut<void>(`/quality/exceptions/${id}/process`),
  close: (id: string, resolveRemark: string) =>
    httpPut<void>(`/quality/exceptions/${id}/close`, { resolveRemark }),
}

// ---------- AI 统一助手（第 4 周） ----------
export const aiChatApi = {
  chat: (question: string) => httpPost<AiChatResult>('/ai/chat', { question }),
}

// ---------- 工厂知识库（第 4 周） ----------
export const knowledgeApi = {
  docsPage: (params: KnowledgeDocQuery) =>
    httpGet<PageResult<KnowledgeDoc>>('/ai/knowledge/docs/page', params),
  docsDetail: (id: string) => httpGet<KnowledgeDoc>(`/ai/knowledge/docs/${id}`),
  docsCreate: (data: KnowledgeDocSave) => httpPost<string>('/ai/knowledge/docs', data),
  docsUpdate: (id: string, data: KnowledgeDocSave) => httpPut<void>(`/ai/knowledge/docs/${id}`, data),
  ask: (question: string) => httpPost<AiAskResult>('/ai/knowledge/ask', { question }),
  feedback: (recordId: string, useful: boolean) =>
    httpPut<void>(`/ai/knowledge/qa-records/${recordId}/feedback`, { useful }),
}

// ---------- 异常建议助手（第 4 周） ----------
export const assistantApi = {
  suggest: (exceptionId: string) => httpPost<ExceptionSuggestion>('/ai/assistant/suggest', { exceptionId }),
  save: (exceptionId: string, suggestion: string) =>
    httpPost<void>('/ai/assistant/save', { exceptionId, suggestion }),
  getSuggestion: (exceptionId: string) =>
    httpGet<ExceptionSuggestion>(`/ai/assistant/suggestion/${exceptionId}`),
}

// ---------- 生产日报助手（第 4 周） ----------
export const dailyApi = {
  page: (params: DailyReportQuery) => httpGet<PageResult<DailyReport>>('/ai/daily/page', params),
  preview: (reportDate: string) => httpPost<DailyPreview>('/ai/daily/preview', { reportDate }),
  save: (data: DailyReportSave) => httpPost<void>('/ai/daily/save', data),
}

// ---------- 系统集成：ERP 外部订单（第 5 周） ----------
export const erpOrderApi = {
  page: (params: ErpOrderQuery) =>
    httpGet<PageResult<ErpOrder>>('/integration/erp/orders/page', params),
  create: (data: ErpOrderSave) => httpPost<string>('/integration/erp/orders', data),
  toWorkOrder: (id: string) => httpPut<void>(`/integration/erp/orders/${id}/to-work-order`),
}

// ---------- 系统集成：WMS 库存（第 5 周） ----------
export const wmsApi = {
  inventoryPage: (params: InventoryQuery) =>
    httpGet<PageResult<InventoryItem>>('/integration/wms/inventory/page', params),
  txPage: (params: StockTxQuery) =>
    httpGet<PageResult<StockTx>>('/integration/wms/transactions/page', params),
  stockIn: (data: StockInSave) => httpPost<void>('/integration/wms/stock-in', data),
  pick: (workOrderId: string) => httpPost<PickResult>('/integration/wms/pick', { workOrderId }),
}
