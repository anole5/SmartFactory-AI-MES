package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表中心汇总出参（第 6 周）：日/周/月三种粒度统一结构，
 * 良率 = 合格 / (合格+不良) × 100，保留 2 位小数
 */
@Getter
@Setter
public class ReportSummaryVO {

    /** 报表类型：day/week/month */
    private String type;

    /** 查询基准日期（缺省今天） */
    private LocalDate date;

    /** 聚合窗口 [rangeStart, rangeEnd) */
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;

    private Integer totalGoodQty;
    private Integer totalDefectQty;
    private BigDecimal yieldRate;
    private Integer reportCount;
    private Integer workOrderCount;

    /** 明细行：日报按工序分组，周报/月报按日期分组 */
    private List<ReportRowVO> rows;
}
