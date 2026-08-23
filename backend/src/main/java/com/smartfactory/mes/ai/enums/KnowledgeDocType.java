package com.smartfactory.mes.ai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 知识库文档类型（数据库存 code）
 */
@Getter
public enum KnowledgeDocType {

    SOP("SOP", "作业指导书"),
    QUALITY_STANDARD("QUALITY_STANDARD", "质量标准"),
    EQUIPMENT_MANUAL("EQUIPMENT_MANUAL", "设备手册"),
    FAULT_GUIDE("FAULT_GUIDE", "故障手册");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    KnowledgeDocType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
