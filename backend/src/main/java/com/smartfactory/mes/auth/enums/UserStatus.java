package com.smartfactory.mes.auth.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 系统用户状态
 */
@Getter
public enum UserStatus {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    UserStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
