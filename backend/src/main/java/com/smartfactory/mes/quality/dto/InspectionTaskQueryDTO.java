package com.smartfactory.mes.quality.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.quality.enums.InspectionTaskStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 质检任务分页查询入参
 */
@Getter
@Setter
public class InspectionTaskQueryDTO extends PageQuery {

    /** 工单 ID 过滤 */
    private Long workOrderId;

    /** 状态过滤 */
    private InspectionTaskStatus status;

    /** 关键字：质检任务号/工序名称 模糊 */
    private String keyword;
}
