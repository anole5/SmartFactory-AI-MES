package com.smartfactory.mes.ai.controller;

import com.smartfactory.mes.ai.dto.WeeklyPreviewRequest;
import com.smartfactory.mes.ai.dto.WeeklyPreviewVO;
import com.smartfactory.mes.ai.dto.WeeklyReportSaveRequest;
import com.smartfactory.mes.ai.service.WeeklyReportService;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 周报接口（第 7 周：近两周趋势聚合 + pro 档趋势描述 + 环比）
 *
 * <p>权限复用 ai:daily:generate/save（周报是日报助手的粒度扩展，不加新菜单——
 * 决策记录）。周报不走流式：pro 档推理耗时且前端一次性渲染趋势分析。</p>
 */
@RestController
@RequestMapping("/ai/weekly")
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    public WeeklyReportController(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    /** 生成周报预览（两周聚合 + pro 档润色，不落库） */
    @RequirePermission("ai:daily:generate")
    @PostMapping("/preview")
    public ApiResult<WeeklyPreviewVO> preview(@Valid @RequestBody WeeklyPreviewRequest request) {
        return ApiResult.success(weeklyReportService.preview(request.getEndDate()));
    }

    /** 保存周报（同 (endDate, WEEK) 幂等覆盖，DB 唯一键兜底） */
    @RequirePermission("ai:daily:save")
    @PostMapping("/save")
    public ApiResult<Void> save(@Valid @RequestBody WeeklyReportSaveRequest request) {
        weeklyReportService.save(request.getEndDate(), request.getContent().trim());
        return ApiResult.success();
    }
}
