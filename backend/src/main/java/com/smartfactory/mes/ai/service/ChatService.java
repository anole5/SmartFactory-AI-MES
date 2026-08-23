package com.smartfactory.mes.ai.service;

import com.smartfactory.mes.ai.dto.AiChatVO;

/**
 * 统一 AI 助手服务：一句话问全局，意图路由分发四类场景
 */
public interface ChatService {

    /** 对话入口：规则/LLM 意图识别 → 路由分发（概况/知识库/异常/日报）→ 统一出参 */
    AiChatVO chat(String question);
}
