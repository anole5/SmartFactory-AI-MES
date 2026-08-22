package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.master.enums.RouteStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 工艺路线分页查询入参
 */
@Getter
@Setter
public class RouteQueryDTO extends PageQuery {

    /** 路线编号关键字 */
    private String keyword;

    /** 产品过滤 */
    private Long productId;

    /** 状态过滤 */
    private RouteStatus status;
}
