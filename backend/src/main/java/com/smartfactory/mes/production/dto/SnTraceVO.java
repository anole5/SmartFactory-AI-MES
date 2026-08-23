package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按 SN 追溯出参：SN 出生信息 + 出生工单摘要 + 工单全时间线
 */
@Getter
@Setter
public class SnTraceVO {

    private Long id;
    private String sn;
    private Long workOrderId;
    private String workOrderNo;
    private String workOrderStatus;
    private String productCodeSnapshot;
    private String productNameSnapshot;
    private Long reportId;
    private String reportNo;
    private LocalDateTime createdAt;

    /** 出生工单全时间线（复用 listByWorkOrder，按动作时间升序） */
    private List<TraceRecordVO> timeline;

    /** 关键件批次使用情况（第 6 周：出生工单全部报工绑定行按物料+批次聚合去重） */
    private List<MaterialBatchUsageVO> materialBatches;
}
