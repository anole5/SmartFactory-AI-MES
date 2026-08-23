package com.smartfactory.mes.ai.controller;

import com.smartfactory.mes.ai.dto.AiChatRequest;
import com.smartfactory.mes.ai.dto.AiChatVO;
import com.smartfactory.mes.ai.service.ChatService;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一 AI 助手接口（第 4 周演示核心：一个输入框问全局，全员可用）
 */
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private final ChatService chatService;

    public AiChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 对话入口：意图路由 → 概况/知识库/异常/日报分发 */
    @RequirePermission("ai:chat:query")
    @PostMapping
    public ApiResult<AiChatVO> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResult.success(chatService.chat(request.getQuestion().trim()));
    }
}
