package com.smartfactory.mes.master.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * BOM 状态（与工艺路线共用状态机：DRAFT -> ACTIVE -> OBSOLETE）
 */
@Getter
public enum BomStatus {

    DRAFT("DRAFT", "草稿"),
    ACTIVE("ACTIVE", "生效"),
    OBSOLETE("OBSOLETE", "作废");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    BomStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
