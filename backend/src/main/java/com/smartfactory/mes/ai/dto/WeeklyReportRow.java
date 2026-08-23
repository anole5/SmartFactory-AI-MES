package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * AI 周报逐日聚合行（WeeklyReportMapper 查询出参）
 */
@Getter
@Setter
public class WeeklyReportRow {

    /** 报工日期 */
    private LocalDate reportDate;

    /** 当日合格数 */
    private Long goodQty;

    /** 当日不良数 */
    private Long defectQty;

    /** 当日报工笔数 */
    private Long reportCount;
}
