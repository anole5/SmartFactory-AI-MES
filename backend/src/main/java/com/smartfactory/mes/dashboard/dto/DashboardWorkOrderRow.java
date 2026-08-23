package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 进行中/已下发工单进度行
 */
@Getter
@Setter
public class DashboardWorkOrderRow {

    private Long id;
    private String workOrderNo;
    private String productCodeSnapshot;
    private String productNameSnapshot;
    private Integer planQty;
    private Integer completedQty;
    private String status;
}
