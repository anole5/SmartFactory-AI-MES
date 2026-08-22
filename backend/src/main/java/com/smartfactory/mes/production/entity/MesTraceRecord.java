package com.smartfactory.mes.production.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.production.enums.ActionType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生产追溯记录：每个关键业务动作写一条（CREATE/RELEASE/ASSIGN/START/PAUSE/RESUME/REPORT/CANCEL）
 *
 * <p>面试可讲：追溯链是 MES 与普通 MIS 的核心区别——任何数量/状态变化都能回答
 * 「谁在什么时间对哪张工单做了什么」，为质量追溯与审计提供依据。</p>
 */
@Getter
@Setter
@TableName("mes_trace_record")
public class MesTraceRecord extends BaseEntity {

    /** 追溯单号（生成器生成：TRC+日期+4 位流水） */
    private String traceNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工序任务 ID（工单级动作可为空） */
    private Long taskId;

    /** 动作类型：CREATE/RELEASE/ASSIGN/START/PAUSE/RESUME/REPORT/CANCEL */
    private ActionType actionType;

    /** 动作时间 */
    private LocalDateTime actionTime;

    /** 操作人 ID（当前登录用户，拦截器放入 CurrentUserContext） */
    private Long operatorId;

    /** 动作明细 JSON，如 {"taskCount":13,"routeId":2} */
    private String actionDetail;
}
