package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 看板工单进度出参（进度百分比服务端算好，前端直接渲染）
 */
@Getter
@Setter
public class DashboardWorkOrderVO {

    private Long id;
    private String workOrderNo;
    private String productCodeSnapshot;
    private String productNameSnapshot;
    private Integer planQty;
    private Integer completedQty;
    private String status;

    /** 进度百分比（0-100 整数） */
    private Integer progressPercent;
}
