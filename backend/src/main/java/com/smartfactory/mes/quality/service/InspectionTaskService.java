package com.smartfactory.mes.quality.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.quality.dto.InspectionRecordVO;
import com.smartfactory.mes.quality.dto.InspectionTaskQueryDTO;
import com.smartfactory.mes.quality.dto.InspectionTaskVO;

import java.util.List;

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

    /** 质检任务分页列表（工单号/质检员名称批量回填） */
    PageResult<InspectionTaskVO> page(InspectionTaskQueryDTO query);

    /** 质检任务详情 */
    InspectionTaskVO getDetail(Long id);

    /** 某质检任务的质检记录列表（按时间升序，质检员名称回填） */
    List<InspectionRecordVO> listRecords(Long taskId);

    /** 开始检验：PENDING -> INSPECTING（同状态幂等，其余 409），回填质检员与开始时间 */
    void start(Long taskId);
}
