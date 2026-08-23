package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 进行中工单行（生产概况聚合 SQL 出参）
 */
@Getter
@Setter
public class ActiveWorkOrderRow {

    private String workOrderNo;
    private String productNameSnapshot;
    private Long planQty;
    private Long completedQty;
    private String status;
}
