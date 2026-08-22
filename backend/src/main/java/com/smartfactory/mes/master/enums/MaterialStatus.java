package com.smartfactory.mes.master.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 物料状态
 */
@Getter
public enum MaterialStatus {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    MaterialStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
