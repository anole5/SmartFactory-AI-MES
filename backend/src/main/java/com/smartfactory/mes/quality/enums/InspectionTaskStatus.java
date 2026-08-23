package com.smartfactory.mes.quality.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 质检任务状态（数据库存 code，状态枚举禁止散落魔法字符串）
 *
 * <p>状态机：PENDING → INSPECTING → COMPLETED；
 * PENDING / INSPECTING 可 → CANCELLED（工单取消时级联）。</p>
 */
@Getter
public enum InspectionTaskStatus {

    PENDING("PENDING", "待检验"),
    INSPECTING("INSPECTING", "检验中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    InspectionTaskStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
