package com.smartfactory.mes.integration.wms.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 库存流水方向
 */
@Getter
public enum StockTxType {

    IN("IN", "入库"),
    OUT("OUT", "出库");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    StockTxType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
