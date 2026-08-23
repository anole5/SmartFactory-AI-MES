package com.smartfactory.mes.production.service;

import com.smartfactory.mes.production.dto.BatchSnTraceVO;
import com.smartfactory.mes.production.dto.BatchTraceVO;
import com.smartfactory.mes.production.dto.SnTraceVO;
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

    /**
     * 按整机 SN 追溯：SN 出生信息 + 出生工单摘要 + 该工单全时间线（未知 SN 404）
     *
     * @param sn 整机序列号
     */
    SnTraceVO snTrace(String sn);

    /**
     * 按批次号追溯：该批次全部报工记录（含工单号/工序/操作人回填）+ 涉及的工单去重列表
     *
     * @param batchNo 生产批次号
     */
    BatchTraceVO batchTrace(String batchNo);

    /**
     * 按物料批次号反查（第 6 周）：批次台账 + 全部绑定记录 + 涉及工单去重 +
     * 工单铸出的整机 SN 列表（批次不存在 404）
     *
     * @param batchNo 物料批次号（MB 前缀）
     */
    BatchSnTraceVO batchSnTrace(String batchNo);
}
