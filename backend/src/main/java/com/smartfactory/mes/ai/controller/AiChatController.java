package com.smartfactory.mes.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.ai.dto.AiChatRequest;
import com.smartfactory.mes.ai.dto.AiChatVO;
import com.smartfactory.mes.ai.service.ChatService;
import com.smartfactory.mes.ai.sse.SseSupport;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一 AI 助手接口（第 4 周演示核心：一个输入框问全局，全员可用）
 *
 * <p>第 7 周：新增流式端点。SSE 端点返回裸 SseEmitter 不包 ApiResult
 * （与 ApiResult 包装的约定文档化例外：SSE 是持续事件流，JSON 信封无从谈起；
 * 鉴权失败仍走 AuthInterceptor 标准 401）。</p>
 */
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private final ChatService chatService;
    private final TaskExecutor aiExecutor;
    private final ObjectMapper objectMapper;

    public AiChatController(ChatService chatService,
                            @Qualifier("aiExecutor") TaskExecutor aiExecutor,
                            ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.aiExecutor = aiExecutor;
        this.objectMapper = objectMapper;
    }

    /** 对话入口：意图路由 → 概况/知识库/异常/日报分发 */
    @RequirePermission("ai:chat:query")
    @PostMapping
    public ApiResult<AiChatVO> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResult.success(chatService.chat(request.getQuestion().trim()));
    }

    /** 流式对话：meta{intent 先行} → delta*（打字机）→ done{recordId,intent,answer,extras} */
    @RequirePermission("ai:chat:query")
    @PostMapping("/stream")
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequest request) {
        // meta/done 由 ChatService.chatStream 编排装配（它掌握意图路由与 recordId）
        return SseSupport.start(aiExecutor, objectMapper,
                sink -> chatService.chatStream(request.getQuestion().trim(), sink));
    }
}
