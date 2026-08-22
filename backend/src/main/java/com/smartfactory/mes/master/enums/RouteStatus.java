package com.smartfactory.mes.master.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 工艺路线状态（状态机与 BOM 一致：DRAFT -> ACTIVE -> OBSOLETE）
 */
@Getter
public enum RouteStatus {

    DRAFT("DRAFT", "草稿"),
    ACTIVE("ACTIVE", "生效"),
    OBSOLETE("OBSOLETE", "作废");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    RouteStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
