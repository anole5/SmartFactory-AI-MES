package com.smartfactory.mes.production.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 生产追溯动作类型：第 2 周每个关键业务动作写一条 mes_trace_record（第 3 周做查询）
 */
@Getter
public enum ActionType {

    CREATE("CREATE", "创建工单"),
    RELEASE("RELEASE", "工单下发"),
    ASSIGN("ASSIGN", "派工"),
    START("START", "开工"),
    PAUSE("PAUSE", "暂停"),
    RESUME("RESUME", "继续"),
    REPORT("REPORT", "报工"),
    CANCEL("CANCEL", "取消"),
    INSPECT_TASK("INSPECT_TASK", "质检任务生成"),
    INSPECT("INSPECT", "质检录入"),
    DEFECT("DEFECT", "不良登记"),
    EXCEPTION_CREATE("EXCEPTION_CREATE", "异常单创建"),
    EXCEPTION_PROCESS("EXCEPTION_PROCESS", "异常处理"),
    EXCEPTION_CLOSE("EXCEPTION_CLOSE", "异常关闭");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    ActionType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
