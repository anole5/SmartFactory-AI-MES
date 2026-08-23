package com.smartfactory.mes.integration.wms.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 库存对象类型
 */
@Getter
public enum ItemType {

    MATERIAL("MATERIAL", "物料"),
    FINISHED("FINISHED", "成品");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    ItemType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
