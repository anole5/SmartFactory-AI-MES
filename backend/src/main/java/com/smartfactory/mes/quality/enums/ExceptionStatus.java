package com.smartfactory.mes.quality.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 异常单状态（数据库存 code，状态枚举禁止散落魔法字符串）
 *
 * <p>状态机：OPEN → PROCESSING → CLOSED（显式流转，不可跳转）。</p>
 */
@Getter
public enum ExceptionStatus {

    OPEN("OPEN", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    CLOSED("CLOSED", "已关闭");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    ExceptionStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
