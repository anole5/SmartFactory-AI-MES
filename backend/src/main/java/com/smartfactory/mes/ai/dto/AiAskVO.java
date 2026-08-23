package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * AI 问答出参（回答 + 引用来源 + 是否降级模板）
 */
@Getter
@Setter
public class AiAskVO {

    /** AI 回答文本 */
    private String answer;

    /** 引用来源（知识库命中文档，空列表 = 兜底回答） */
    private List<AiReferenceVO> references;

    /** 是否降级模板回答（LLM 不可用时为 true，前端可提示"模板回答"） */
    private Boolean fallback;

    /** 问答记录 ID（用于有用/无用反馈） */
    private Long recordId;
}
