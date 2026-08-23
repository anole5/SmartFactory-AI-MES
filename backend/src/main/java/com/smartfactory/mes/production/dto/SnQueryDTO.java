package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 整机 SN 分页查询入参
 */
@Getter
@Setter
public class SnQueryDTO extends PageQuery {

    /** 工单 ID 过滤 */
    private Long workOrderId;

    /** 关键字：SN 模糊匹配 */
    private String keyword;
}
