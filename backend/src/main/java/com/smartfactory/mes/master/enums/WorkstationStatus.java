package com.smartfactory.mes.master.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 工位状态
 */
@Getter
public enum WorkstationStatus {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    WorkstationStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
