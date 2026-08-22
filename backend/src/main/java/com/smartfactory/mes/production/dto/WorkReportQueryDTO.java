package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 报工记录分页查询入参
 */
@Getter
@Setter
public class WorkReportQueryDTO extends PageQuery {

    /** 工单 ID 过滤 */
    private Long workOrderId;

    /** 任务 ID 过滤 */
    private Long taskId;

    /** 报工人 ID 过滤 */
    private Long operatorId;
}
