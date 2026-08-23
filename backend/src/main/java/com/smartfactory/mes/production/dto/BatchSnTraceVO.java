package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 按物料批次号反查出参（第 6 周）：批次台账 + 全部绑定记录 + 涉及工单去重 +
 * 这些工单铸出的整机 SN 列表（批次不存在 404）
 */
@Getter
@Setter
public class BatchSnTraceVO {

    private Long batchId;
    private String batchNo;
    private Long materialId;
    private String materialCodeSnapshot;
    private String materialNameSnapshot;
    private Integer batchQty;
    private Integer usedQty;
    private LocalDate inDate;
    private String supplier;

    /** 该批次全部绑定记录（报工号回填） */
    private List<MaterialBatchUsageVO> bindings;

    /** 涉及的工单去重列表 */
    private List<WorkOrderVO> workOrders;

    /** 这些工单铸出的整机 SN 列表（id 升序） */
    private List<BatchSnItemVO> sns;
}
