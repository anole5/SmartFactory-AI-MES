package com.smartfactory.mes.integration.wms.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存分页查询
 */
@Getter
@Setter
public class InventoryQueryDTO extends PageQuery {

    /** 库存对象类型：MATERIAL/FINISHED（可空 = 全部） */
    private String itemType;

    /** 关键词：物料编码/名称（MATERIAL）或产品编码/名称（FINISHED） */
    private String keyword;
}
