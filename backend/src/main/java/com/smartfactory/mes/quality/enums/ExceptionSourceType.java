package com.smartfactory.mes.quality.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 异常单来源（数据库存 code，状态枚举禁止散落魔法字符串）
 */
@Getter
public enum ExceptionSourceType {

    /** 不良记录生成（关联 defect_record_id） */
    DEFECT("DEFECT", "不良生成"),
    /** 手工创建（defect_record_id 为空） */
    MANUAL("MANUAL", "手工创建");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    ExceptionSourceType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
