package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 排程甘特图任务行出参（第 6 周）：只含计划窗口与查询日期有交集的任务，
 * 跨日任务两天各返回一次（前端横道按起止时刻渲染，注释说明口径）
 */
@Getter
@Setter
public class GanttTaskVO {

    private Long taskId;
    private String taskNo;
    private Long workOrderId;
    private String workOrderNo;
    private String processCodeSnapshot;
    private String processNameSnapshot;
    private Integer sequenceNo;
    private Long workstationId;
    private String workstationCode;
    private String workstationName;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private String status;
    private String priority;
    private Integer planQty;

    /** 逾期（计划完工已过当前时间且任务未完成/未取消） */
    private Boolean isOverdue;
}
