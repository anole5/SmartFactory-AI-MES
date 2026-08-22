package com.smartfactory.mes.production.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.WorkOrderQueryDTO;
import com.smartfactory.mes.production.dto.WorkOrderSaveDTO;
import com.smartfactory.mes.production.dto.WorkOrderVO;

/**
 * 生产工单服务
 */
public interface WorkOrderService {

    /** 分页查询 */
    PageResult<WorkOrderVO> page(WorkOrderQueryDTO query);

    /** 详情（T6 补任务列表、T8 补报工统计） */
    WorkOrderVO getDetail(Long id);

    /** 创建工单（DRAFT）：单号生成 + 产品校验 + 自动解析 ACTIVE BOM/路线 + 快照回填 + CREATE 追溯 */
    Long create(WorkOrderSaveDTO dto);

    /** 编辑工单（仅 DRAFT）：改产品时刷新 BOM/路线解析与快照 */
    void update(Long id, WorkOrderSaveDTO dto);

    /** 取消工单：DRAFT/RELEASED/IN_PROGRESS -> CANCELLED（同状态幂等，其余 409） */
    void cancel(Long id);
}
