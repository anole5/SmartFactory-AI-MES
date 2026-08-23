package com.smartfactory.mes.quality.service;

import com.smartfactory.mes.production.entity.MesOperationTask;

/**
 * 质检任务服务
 */
public interface InspectionTaskService {

    /**
     * 工序任务报工达 COMPLETED 且需质检时，生成质检任务（第 3 周报工事务内调用）
     *
     * <p>plan_qty = 任务累计完成数；工序编码/名称/工位快照固化；
     * 失败随报工整单回滚，不产生半截质检任务。</p>
     *
     * @param workOrderId 工单 ID
     * @param task        报工后重读的工序任务（含累计完成数）
     */
    void generateFromCompletedTask(Long workOrderId, MesOperationTask task);

    /**
     * 工单取消时级联取消未完成质检任务（PENDING/INSPECTING -> CANCELLED），
     * 已完成质检任务保留历史
     *
     * @return 实际取消数量（用于 CANCEL 追溯明细）
     */
    int cancelByWorkOrder(Long workOrderId);
}
