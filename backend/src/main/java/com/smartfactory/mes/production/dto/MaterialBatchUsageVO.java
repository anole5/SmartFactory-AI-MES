package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 关键件批次使用情况出参（第 6 周）：一条绑定行 = 某报工使用了某物料批次；
 * SN 追溯里按物料+批次聚合去重（跨报工合并，qtyUsed 求和）
 */
@Getter
@Setter
public class MaterialBatchUsageVO {

    private Long reportId;
    private String reportNo;
    private Long batchId;
    private String batchNo;
    private Long materialId;
    private String materialCodeSnapshot;
    private String materialNameSnapshot;
    private Integer qtyUsed;
    private LocalDateTime createdAt;
}
