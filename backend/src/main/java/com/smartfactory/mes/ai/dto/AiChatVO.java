package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 统一 AI 助手对话出参（按意图路由分发的统一视图）
 */
@Getter
@Setter
public class AiChatVO {

    /** 意图：OVERVIEW/KNOWLEDGE/EXCEPTION/REPORT */
    private String intent;

    /** 回答正文 */
    private String answer;

    /** 引用来源（知识库意图时非空） */
    private List<AiReferenceVO> references;

    /** 是否降级模板回答（LLM 不可用时为 true） */
    private Boolean fallback;

    /** 问答记录 ID（用于有用/无用反馈） */
    private Long recordId;

    /** 异常单 ID（EXCEPTION 意图且识别出异常单号时回填） */
    private Long exceptionId;

    /** 日报日期（REPORT 意图回填） */
    private LocalDate reportDate;

    /** 数据摘要（OVERVIEW/REPORT 的统计数据来源，供前端展示） */
    private String summary;
}
