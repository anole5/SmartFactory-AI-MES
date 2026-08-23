package com.smartfactory.mes.production.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 报工-物料批次绑定（第 6 周）：报工（或补录）时绑定关键件批次，SN↔批次正反向追溯的关键链。
 * 只增不改（审计口径）；同报工同物料只绑一次、换批 409 由 Service 层拦截。
 */
@Getter
@Setter
@TableName("mes_report_material_batch")
public class MesReportMaterialBatch extends BaseEntity {

    /** 报工记录 ID */
    private Long reportId;

    /** 工单 ID（冗余，反查免 join） */
    private Long workOrderId;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码快照 */
    private String materialCodeSnapshot;

    /** 物料名称快照 */
    private String materialNameSnapshot;

    /** 物料批次 ID */
    private Long batchId;

    /** 批次号快照 */
    private String batchNoSnapshot;

    /** 本次消耗数量（演示口径 1:1 取报工台数） */
    private Integer qtyUsed;
}
