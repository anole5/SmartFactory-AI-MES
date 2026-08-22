package com.smartfactory.mes.master.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 产品状态（数据库存 code，状态枚举禁止散落魔法字符串）
 */
@Getter
public enum ProductStatus {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    ProductStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
