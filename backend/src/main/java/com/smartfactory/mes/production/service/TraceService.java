package com.smartfactory.mes.production.service;

import com.smartfactory.mes.production.dto.TraceRecordVO;
import com.smartfactory.mes.production.enums.ActionType;

import java.util.List;

/**
 * 追溯记录服务：工单全生命周期动作统一入口
 */
public interface TraceService {

    /**
     * 写一条追溯记录
     *
     * @param workOrderId 工单 ID
     * @param taskId      工序任务 ID（工单级动作传 null）
     * @param actionType  动作类型
     * @param detail      动作明细对象（序列化为 JSON，可 null）
     */
    void write(Long workOrderId, Long taskId, ActionType actionType, Object detail);

    /**
     * 查询某工单的追溯时间线（按动作时间升序，操作人名称批量回填）
     *
     * @param workOrderId 工单 ID
     */
    List<TraceRecordVO> listByWorkOrder(Long workOrderId);
}
