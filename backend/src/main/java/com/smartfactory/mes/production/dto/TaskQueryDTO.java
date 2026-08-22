package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.production.enums.TaskStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 工序任务分页查询入参
 */
@Getter
@Setter
public class TaskQueryDTO extends PageQuery {

    /** 工单 ID 过滤 */
    private Long workOrderId;

    /** 状态过滤 */
    private TaskStatus status;

    /** 工位 ID 过滤 */
    private Long workstationId;

    /** 操作员 ID 过滤 */
    private Long operatorId;
}
