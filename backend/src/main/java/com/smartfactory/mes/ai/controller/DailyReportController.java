package com.smartfactory.mes.ai.controller;

import com.smartfactory.mes.ai.dto.DailyPreviewRequest;
import com.smartfactory.mes.ai.dto.DailyPreviewVO;
import com.smartfactory.mes.ai.dto.DailyReportQueryDTO;
import com.smartfactory.mes.ai.dto.DailyReportSaveRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.ai.dto.DailyReportVO;
import com.smartfactory.mes.ai.enums.AiIntent;
import com.smartfactory.mes.ai.service.DailyReportService;
import com.smartfactory.mes.ai.sse.SseSupport;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 生产日报助手接口（第 4 周：生成/保存日报）
 *
 * <p>第 7 周：preview/stream 流式端点返回裸 SseEmitter（见 {@link SseSupport}）。</p>
 */
@RestController
@RequestMapping("/ai/daily")
public class DailyReportController {

    private final DailyReportService dailyReportService;
    private final TaskExecutor aiExecutor;
    private final ObjectMapper objectMapper;

    public DailyReportController(DailyReportService dailyReportService,
                                 @Qualifier("aiExecutor") TaskExecutor aiExecutor,
                                 ObjectMapper objectMapper) {
        this.dailyReportService = dailyReportService;
        this.aiExecutor = aiExecutor;
        this.objectMapper = objectMapper;
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

    /** 流式日报预览：meta{reportDate} → delta* → done{reportDate,answer,summary,fallback}（不落库） */
    @RequirePermission("ai:daily:generate")
    @PostMapping("/preview/stream")
    public SseEmitter previewStream(@Valid @RequestBody DailyPreviewRequest request) {
        return SseSupport.start(aiExecutor, objectMapper, sink -> {
            sink.sendMeta(Map.of("intent", AiIntent.REPORT.getCode(),
                    "reportDate", request.getReportDate().toString()));
            DailyPreviewVO vo = dailyReportService.previewStream(request.getReportDate(), sink);
            if (vo != null) {
                sink.sendDone(Map.of(
                        "intent", AiIntent.REPORT.getCode(),
                        "reportDate", vo.getReportDate().toString(),
                        "answer", vo.getContent(),
                        "summary", vo.getSummary(),
                        "fallback", Boolean.TRUE.equals(vo.getFallback())));
            }
        });
    }

    /** 保存日报（同一 report_date 幂等覆盖） */
    @RequirePermission("ai:daily:save")
    @PostMapping("/save")
    public ApiResult<Void> save(@Valid @RequestBody DailyReportSaveRequest request) {
        dailyReportService.save(request.getReportDate(), request.getContent().trim());
        return ApiResult.success();
    }
}
