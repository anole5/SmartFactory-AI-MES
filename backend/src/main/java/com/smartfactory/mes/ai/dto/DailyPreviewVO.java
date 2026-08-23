package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 生产日报生成出参（content = AI 润色正文，summary = 原始统计数据供前端展示数据来源）
 */
@Getter
@Setter
public class DailyPreviewVO {

    private LocalDate reportDate;

    /** AI 润色后的日报正文 */
    private String content;

    /** 原始统计数据摘要（数据来源，透传给 LLM 的输入） */
    private String summary;

    /** 是否降级模板日报（LLM 不可用时为 true） */
    private Boolean fallback;
}
