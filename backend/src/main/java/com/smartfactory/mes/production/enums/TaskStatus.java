package com.smartfactory.mes.production.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 工序任务状态机（术语表权威版）：
 * PENDING -> ASSIGNED -> RUNNING <-> PAUSED -> COMPLETED
 * PENDING / ASSIGNED 可 -> CANCELLED（工单取消时级联）
 *
 * <p>注意：PAUSED 只存在于任务级，工单级没有暂停状态。</p>
 */
@Getter
public enum TaskStatus {

    PENDING("PENDING", "待派工"),
    ASSIGNED("ASSIGNED", "已派工"),
    RUNNING("RUNNING", "进行中"),
    PAUSED("PAUSED", "已暂停"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    TaskStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
