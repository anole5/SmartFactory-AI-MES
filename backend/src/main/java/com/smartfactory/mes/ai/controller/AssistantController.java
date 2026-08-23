package com.smartfactory.mes.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.ai.dto.ExceptionSuggestionVO;
import com.smartfactory.mes.ai.dto.ExceptionSuggestRequest;
import com.smartfactory.mes.ai.dto.ExceptionSuggestSaveRequest;
import com.smartfactory.mes.ai.enums.AiIntent;
import com.smartfactory.mes.ai.service.AssistantService;
import com.smartfactory.mes.ai.sse.SseSupport;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 异常建议助手接口（第 4 周：生成建议全员可用，保存回写仅 admin/qa）
 *
 * <p>第 7 周：suggest/stream 流式端点返回裸 SseEmitter（见 {@link SseSupport}）。</p>
 */
@RestController
@RequestMapping("/ai/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final TaskExecutor aiExecutor;
    private final ObjectMapper objectMapper;

    public AssistantController(AssistantService assistantService,
                               @Qualifier("aiExecutor") TaskExecutor aiExecutor,
                               ObjectMapper objectMapper) {
        this.assistantService = assistantService;
        this.aiExecutor = aiExecutor;
        this.objectMapper = objectMapper;
    }

    /** 生成处理建议（pro 档推理，不落库） */
    @RequirePermission("ai:assistant:generate")
    @PostMapping("/suggest")
    public ApiResult<ExceptionSuggestionVO> suggest(@Valid @RequestBody ExceptionSuggestRequest request) {
        return ApiResult.success(assistantService.suggest(request.getExceptionId()));
    }

    /** 流式建议：meta{exceptionId} → delta* → done{exceptionId,exceptionNo,answer,fallback}（不落库） */
    @RequirePermission("ai:assistant:generate")
    @PostMapping("/suggest/stream")
    public SseEmitter suggestStream(@Valid @RequestBody ExceptionSuggestRequest request) {
        return SseSupport.start(aiExecutor, objectMapper, sink -> {
            sink.sendMeta(Map.of("intent", AiIntent.EXCEPTION.getCode(),
                    "exceptionId", request.getExceptionId()));
            ExceptionSuggestionVO vo = assistantService.suggestStream(request.getExceptionId(), sink);
            if (vo != null) {
                sink.sendDone(Map.of(
                        "intent", AiIntent.EXCEPTION.getCode(),
                        "exceptionId", vo.getExceptionId(),
                        "exceptionNo", vo.getExceptionNo(),
                        "answer", vo.getSuggestion(),
                        "fallback", Boolean.TRUE.equals(vo.getFallback())));
            }
        });
    }

    /** 保存建议回写异常单（写追溯 AI_SUGGEST） */
    @RequirePermission("ai:assistant:save")
    @PostMapping("/save")
    public ApiResult<Void> save(@Valid @RequestBody ExceptionSuggestSaveRequest request) {
        assistantService.save(request.getExceptionId(), request.getSuggestion().trim());
        return ApiResult.success();
    }

    /** 查询异常单已保存的建议 */
    @RequirePermission("ai:assistant:query")
    @GetMapping("/suggestion/{exceptionId}")
    public ApiResult<ExceptionSuggestionVO> get(@PathVariable Long exceptionId) {
        return ApiResult.success(assistantService.getSuggestion(exceptionId));
    }
}
