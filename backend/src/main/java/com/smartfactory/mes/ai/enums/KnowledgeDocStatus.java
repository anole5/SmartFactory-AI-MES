package com.smartfactory.mes.ai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 知识库文档状态（停用不参与检索）
 */
@Getter
public enum KnowledgeDocStatus {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    KnowledgeDocStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
