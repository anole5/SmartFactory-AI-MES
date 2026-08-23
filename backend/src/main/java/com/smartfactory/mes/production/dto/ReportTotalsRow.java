package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 报表中心汇总行（第 6 周，Mapper 聚合出参）
 */
@Getter
@Setter
public class ReportTotalsRow {

    private Integer goodQty;
    private Integer defectQty;
    private Integer reportCount;
    private Integer workOrderCount;
}
