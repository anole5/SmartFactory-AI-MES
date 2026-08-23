package com.smartfactory.mes.quality.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 不良记录分页查询入参
 */
@Getter
@Setter
public class DefectQueryDTO extends PageQuery {

    /** 工单 ID 过滤 */
    private Long workOrderId;

    /** 不良代码过滤 */
    private String defectCode;

    /** 关键字：不良单号 模糊 */
    private String keyword;
}
