package com.smartfactory.mes.integration.erp.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * ERP 外部订单状态机：PENDING(已接收) → SYNCED(已转工单) → DONE(工单完工回传)
 */
@Getter
public enum ExternalOrderStatus {

    PENDING("PENDING", "待转工单"),
    SYNCED("SYNCED", "已转工单"),
    DONE("DONE", "已完工回传");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    ExternalOrderStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
