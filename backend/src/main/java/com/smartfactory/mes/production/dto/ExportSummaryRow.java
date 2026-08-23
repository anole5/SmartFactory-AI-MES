package com.smartfactory.mes.production.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 报表导出-汇总 sheet 行（第 6 周，EasyExcel 表头注解）
 */
@Getter
@Setter
public class ExportSummaryRow {

    @ExcelProperty("报表类型")
    private String type;

    @ExcelProperty("基准日期")
    private String date;

    @ExcelProperty("窗口开始")
    private String rangeStart;

    @ExcelProperty("窗口结束")
    private String rangeEnd;

    @ExcelProperty("合格数量")
    private Integer totalGoodQty;

    @ExcelProperty("不良数量")
    private Integer totalDefectQty;

    @ColumnWidth(16)
    @ExcelProperty("良率(%)")
    private BigDecimal yieldRate;

    @ExcelProperty("报工数")
    private Integer reportCount;

    @ExcelProperty("工单数")
    private Integer workOrderCount;

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static ExportSummaryRow of(ReportSummaryVO vo) {
        ExportSummaryRow row = new ExportSummaryRow();
        row.setType(vo.getType());
        row.setDate(vo.getDate() == null ? null : vo.getDate().toString());
        row.setRangeStart(vo.getRangeStart() == null ? null : vo.getRangeStart().format(DATETIME));
        row.setRangeEnd(vo.getRangeEnd() == null ? null : vo.getRangeEnd().format(DATETIME));
        row.setTotalGoodQty(vo.getTotalGoodQty());
        row.setTotalDefectQty(vo.getTotalDefectQty());
        row.setYieldRate(vo.getYieldRate());
        row.setReportCount(vo.getReportCount());
        row.setWorkOrderCount(vo.getWorkOrderCount());
        return row;
    }
}
