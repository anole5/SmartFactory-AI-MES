package com.smartfactory.mes.production.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 工单优先级
 */
@Getter
public enum OrderPriority {

    HIGH("HIGH", "高"),
    NORMAL("NORMAL", "正常"),
    LOW("LOW", "低");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    OrderPriority(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
