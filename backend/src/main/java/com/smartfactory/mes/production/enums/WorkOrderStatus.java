package com.smartfactory.mes.production.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 工单状态机（术语表权威版）：
 * DRAFT -> RELEASED -> IN_PROGRESS -> COMPLETED -> CLOSED
 * DRAFT / RELEASED / IN_PROGRESS 可 -> CANCELLED
 *
 * <p>code 与枚举 name 保持一致：GET 参数（?status=RELEASED）由 Spring 按 name 绑定。</p>
 */
@Getter
public enum WorkOrderStatus {

    DRAFT("DRAFT", "草稿"),
    RELEASED("RELEASED", "已下发"),
    IN_PROGRESS("IN_PROGRESS", "生产中"),
    COMPLETED("COMPLETED", "已完成"),
    CLOSED("CLOSED", "已关闭"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    WorkOrderStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
