package com.smartfactory.mes.integration.wms.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 库存业务类型（第 5 周：采购入库 / 工单领料 / 成品完工入库）
 */
@Getter
public enum StockBizType {

    PURCHASE_IN("PURCHASE_IN", "采购入库"),
    PICK_OUT("PICK_OUT", "工单领料"),
    FINISHED_IN("FINISHED_IN", "成品完工入库");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    StockBizType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
