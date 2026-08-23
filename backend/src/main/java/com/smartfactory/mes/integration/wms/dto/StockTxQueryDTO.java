package com.smartfactory.mes.integration.wms.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存流水分页查询
 */
@Getter
@Setter
public class StockTxQueryDTO extends PageQuery {

    /** 工单 ID（领料/成品入库流水过滤） */
    private Long workOrderId;

    /** 库存对象类型：MATERIAL/FINISHED */
    private String itemType;

    /** 业务类型：PURCHASE_IN/PICK_OUT/FINISHED_IN */
    private String bizType;
}
