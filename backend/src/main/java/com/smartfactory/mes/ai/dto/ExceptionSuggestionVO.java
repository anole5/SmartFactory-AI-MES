package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 异常处理建议出参（生成/查询共用）
 */
@Getter
@Setter
public class ExceptionSuggestionVO {

    /** 异常单 ID */
    private Long exceptionId;

    /** 异常单号 */
    private String exceptionNo;

    /** 处理建议（生成接口为 AI 输出；查询接口为已保存回写的建议） */
    private String suggestion;

    /** 是否降级模板建议（LLM 不可用时为 true） */
    private Boolean fallback;
}
