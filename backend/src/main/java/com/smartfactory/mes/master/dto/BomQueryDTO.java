package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.master.enums.BomStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * BOM 分页查询入参
 */
@Getter
@Setter
public class BomQueryDTO extends PageQuery {

    /** BOM 编号关键字 */
    private String keyword;

    /** 产品过滤 */
    private Long productId;

    /** 状态过滤 */
    private BomStatus status;
}
