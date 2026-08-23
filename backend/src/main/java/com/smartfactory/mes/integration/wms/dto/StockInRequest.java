package com.smartfactory.mes.integration.wms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 采购入库入参（第 5 周：模拟供应商来料入库）
 */
@Getter
@Setter
public class StockInRequest {

    @NotNull(message = "物料不能为空")
    private Long materialId;

    @NotNull(message = "入库数量不能为空")
    @Min(value = 1, message = "入库数量最小为 1")
    @Max(value = 999999, message = "入库数量最大为 999999")
    private Integer qty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
