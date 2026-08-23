package com.smartfactory.mes.quality.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.quality.enums.ExceptionStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 异常单分页查询入参
 */
@Getter
@Setter
public class ExceptionQueryDTO extends PageQuery {

    /** 工单 ID 过滤 */
    private Long workOrderId;

    /** 状态过滤 */
    private ExceptionStatus status;

    /** 关键字：异常单号/异常描述 模糊 */
    private String keyword;
}
