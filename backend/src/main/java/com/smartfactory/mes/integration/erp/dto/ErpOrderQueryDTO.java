package com.smartfactory.mes.integration.erp.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * ERP 外部订单分页查询
 */
@Getter
@Setter
public class ErpOrderQueryDTO extends PageQuery {

    /** 关键词：外部订单号 / 产品名称快照 */
    private String keyword;

    /** 状态：PENDING/SYNCED/DONE */
    private String status;
}
