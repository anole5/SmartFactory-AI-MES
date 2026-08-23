package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * 按批次号追溯出参：批次全部报工记录 + 涉及的工单（去重）
 */
@Getter
@Setter
public class BatchTraceVO {

    private List<WorkReportVO> reports;
    private List<WorkOrderVO> workOrders;

    /** 空批次（无报工记录） */
    public static BatchTraceVO empty() {
        BatchTraceVO vo = new BatchTraceVO();
        vo.setReports(Collections.emptyList());
        vo.setWorkOrders(Collections.emptyList());
        return vo;
    }
}
