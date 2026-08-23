package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 看板汇总出参（产量/报工/良率/异常/设备状态分布）
 */
@Getter
@Setter
public class DashboardSummaryVO {

    private Long todayOutputQty;
    private Long todayReportCount;
    private Long todayDefectQty;

    /** 今日整体良率百分比（保留 1 位小数），今日无报工为 null（前端显示 --） */
    private BigDecimal todayYieldRate;

    private Long inProgressWorkOrderCount;
    private Long openExceptionCount;

    /** 设备状态分布（RUNNING/IDLE/STOPPED/MAINTENANCE 全量填充） */
    private List<StatusCountVO> equipmentStatusCounts;
}
