// 各模块 API 集中定义（按后端 Controller 分组）
import { httpDelete, httpGet, httpPost, httpPut } from './request'
import type {
  Bom,
  BomQuery,
  BomSave,
  DefectQuery,
  DefectRecord,
  ExceptionOrder,
  ExceptionQuery,
  ExceptionSave,
  InspectionRecord,
  InspectionRecordSave,
  InspectionTask,
  InspectionTaskQuery,
  LoginResult,
  Material,
  MaterialQuery,
  MaterialSave,
  OperationTask,
  PageResult,
  Process,
  ProcessQuery,
  ProcessSave,
  Product,
  ProductQuery,
  ProductSave,
  Route,
  RouteQuery,
  RouteSave,
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
