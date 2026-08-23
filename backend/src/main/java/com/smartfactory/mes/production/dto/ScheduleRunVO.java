package com.smartfactory.mes.production.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 排程执行结果出参（第 6 周）：重跑覆盖计划时间列即幂等
 */
@Getter
@Setter
@AllArgsConstructor
public class ScheduleRunVO {

    /** 参与排程的活跃工单数（RELEASED/IN_PROGRESS） */
    private Integer workOrderCount;

    /** 更新的任务计划时间行数（未完成任务） */
    private Integer taskCount;

    /** 排程执行时间 */
    private LocalDateTime runAt;
}
