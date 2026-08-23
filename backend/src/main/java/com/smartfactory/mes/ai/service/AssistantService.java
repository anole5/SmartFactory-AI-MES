package com.smartfactory.mes.ai.service;

import com.smartfactory.mes.ai.dto.ExceptionSuggestionVO;

/**
 * 异常处理建议助手：知识库 + 异常单上下文 → pro 档推理 → 建议回写异常单（admin/qa）
 */
public interface AssistantService {

    /** 生成处理建议（不落库；知识库召回 FAULT_GUIDE 文档 + pro 档推理） */
    ExceptionSuggestionVO suggest(Long exceptionId);

    /** 保存建议回写异常单 ai_suggestion 列 + 追溯 AI_SUGGEST */
    void save(Long exceptionId, String suggestion);

    /** 查询异常单已保存的建议 */
    ExceptionSuggestionVO getSuggestion(Long exceptionId);
}
