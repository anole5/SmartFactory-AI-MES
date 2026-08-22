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
}
