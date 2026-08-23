package com.smartfactory.mes.production.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 报表导出-明细 sheet 行（第 6 周）：日报分组=工序名、周报/月报分组=日期，
 * 共用同一张表（工序编码列周报/月报为空）
 */
@Getter
@Setter
public class ExportDetailRow {

    @ExcelProperty("分组（工序/日期）")
    private String groupKey;

    @ExcelProperty("工序编码")
    private String processCode;

    @ExcelProperty("合格数量")
    private Integer goodQty;

    @ExcelProperty("不良数量")
    private Integer defectQty;

    @ExcelProperty("报工数")
    private Integer reportCount;

    @ExcelProperty("工单数")
    private Integer workOrderCount;

    public static ExportDetailRow of(ReportRowVO vo) {
        ExportDetailRow row = new ExportDetailRow();
        row.setGroupKey(vo.getGroupKey());
        row.setProcessCode(vo.getProcessCode());
        row.setGoodQty(vo.getGoodQty());
        row.setDefectQty(vo.getDefectQty());
        row.setReportCount(vo.getReportCount());
        row.setWorkOrderCount(vo.getWorkOrderCount());
        return row;
    }
}
