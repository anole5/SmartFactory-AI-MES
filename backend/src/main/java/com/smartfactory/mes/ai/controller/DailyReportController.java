package com.smartfactory.mes.ai.controller;

import com.smartfactory.mes.ai.dto.DailyPreviewRequest;
import com.smartfactory.mes.ai.dto.DailyPreviewVO;
import com.smartfactory.mes.ai.dto.DailyReportQueryDTO;
import com.smartfactory.mes.ai.dto.DailyReportSaveRequest;
import com.smartfactory.mes.ai.dto.DailyReportVO;
import com.smartfactory.mes.ai.service.DailyReportService;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产日报助手接口（第 4 周：生成/保存日报）
 */
@RestController
@RequestMapping("/ai/daily")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    public DailyReportController(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    /** 日报分页 */
    @RequirePermission("ai:daily:query")
    @GetMapping("/page")
    public ApiResult<PageResult<DailyReportVO>> page(@Valid DailyReportQueryDTO query) {
        return ApiResult.success(dailyReportService.page(query));
    }

    /** 生成日报预览（数据聚合 + flash 润色，不落库） */
    @RequirePermission("ai:daily:generate")
    @PostMapping("/preview")
    public ApiResult<DailyPreviewVO> preview(@Valid @RequestBody DailyPreviewRequest request) {
        return ApiResult.success(dailyReportService.preview(request.getReportDate()));
    }

    /** 保存日报（同一 report_date 幂等覆盖） */
    @RequirePermission("ai:daily:save")
    @PostMapping("/save")
    public ApiResult<Void> save(@Valid @RequestBody DailyReportSaveRequest request) {
        dailyReportService.save(request.getReportDate(), request.getContent().trim());
        return ApiResult.success();
    }
}
