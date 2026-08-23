package com.smartfactory.mes.production.controller;

import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.production.dto.ExportDetailRow;
import com.smartfactory.mes.production.dto.ExportSummaryRow;
import com.smartfactory.mes.production.dto.ReportSummaryVO;
import com.smartfactory.mes.production.service.ReportCenterService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 报表中心接口（第 6 周）：日/周/月汇总 + Excel 导出（双 sheet）。
 * 查询限 production:report:center:query、导出限 production:report:export（admin/计划员）。
 */
@RestController
@RequestMapping("/production/reports-center")
public class ReportCenterController {

    private final ReportCenterService reportCenterService;

    public ReportCenterController(ReportCenterService reportCenterService) {
        this.reportCenterService = reportCenterService;
    }

    /** 汇总报表（type=day/week/month，date 缺省今天） */
    @RequirePermission("production:report:center:query")
    @GetMapping("/summary")
    public ApiResult<ReportSummaryVO> summary(@RequestParam String type,
                                              @RequestParam(required = false) LocalDate date) {
        return ApiResult.success(reportCenterService.summary(type, date));
    }

    /**
     * 导出 Excel（双 sheet：汇总 + 明细）。
     * 【惯例例外】文件下载直接写文件流，不包 ApiResult——前端用裸 axios downloadRequest
     * （仅 token 拦截器，不经过 ApiResult 解包拦截器，Blob 不会当 JSON 炸）；
     * Content-Disposition 用 filename*（RFC 5987）带 UTF-8 中文文件名。
     */
    @RequirePermission("production:report:export")
    @GetMapping("/export")
    public void export(@RequestParam String type,
                       @RequestParam(required = false) LocalDate date,
                       HttpServletResponse response) throws IOException {
        ReportSummaryVO summary = reportCenterService.summary(type, date);
        String filename = "生产报表_" + summary.getType() + "_" + summary.getDate() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        try (ServletOutputStream out = response.getOutputStream();
             ExcelWriter writer = com.alibaba.excel.EasyExcel.write(out).build()) {
            WriteSheet summarySheet = com.alibaba.excel.EasyExcel.writerSheet("汇总")
                    .head(ExportSummaryRow.class).build();
            writer.write(List.of(ExportSummaryRow.of(summary)), summarySheet);
            WriteSheet detailSheet = com.alibaba.excel.EasyExcel.writerSheet("明细")
                    .head(ExportDetailRow.class).build();
            List<ExportDetailRow> details = summary.getRows().stream()
                    .map(ExportDetailRow::of).toList();
            writer.write(details, detailSheet);
            writer.finish();
        }
    }
}
