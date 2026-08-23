package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * AI 周报生成出参（content = pro 档生成的周报正文，summary = 两周逐日趋势原始数据）
 */
@Getter
@Setter
public class WeeklyPreviewVO {

    private LocalDate endDate;

    /** AI 生成的周报正文 */
    private String content;

    /** 趋势统计摘要（逐日行 + 本周/上周合计 + 环比，透传给 LLM 的输入） */
    private String summary;

    /** 是否降级模板周报（LLM 不可用时为 true） */
    private Boolean fallback;
}
