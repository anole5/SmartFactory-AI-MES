package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.master.enums.ProductStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 产品分页查询入参
 */
@Getter
@Setter
public class ProductQueryDTO extends PageQuery {

    /** 编码或名称关键字 */
    private String keyword;

    /** 状态过滤 */
    private ProductStatus status;
}
