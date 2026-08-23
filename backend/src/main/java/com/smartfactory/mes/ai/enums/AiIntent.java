package com.smartfactory.mes.ai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * AI 助手意图（统一对话入口的四类场景路由结果，问答记录落库用）
 */
@Getter
public enum AiIntent {

    OVERVIEW("OVERVIEW", "生产概况"),
    KNOWLEDGE("KNOWLEDGE", "知识问答"),
    EXCEPTION("EXCEPTION", "异常建议"),
    REPORT("REPORT", "生产日报");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    AiIntent(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
