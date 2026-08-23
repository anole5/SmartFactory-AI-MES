package com.smartfactory.mes.master.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 设备状态（数据库存 code，状态枚举禁止散落魔法字符串）
 *
 * <p>非严格状态机，允许任意切换（EquipmentSimulator 定时随机漂移 + 人工切换）。</p>
 */
@Getter
public enum EquipmentStatus {

    RUNNING("RUNNING", "运行"),
    IDLE("IDLE", "空闲"),
    STOPPED("STOPPED", "停机"),
    MAINTENANCE("MAINTENANCE", "维护");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    EquipmentStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
