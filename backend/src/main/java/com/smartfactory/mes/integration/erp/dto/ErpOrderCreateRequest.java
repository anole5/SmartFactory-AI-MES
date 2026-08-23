package com.smartfactory.mes.integration.erp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * ERP 模拟下单入参（模拟外部 ERP 系统推来的生产订单）
 */
@Getter
@Setter
public class ErpOrderCreateRequest {

    @NotNull(message = "产品不能为空")
    private Long productId;

    @NotNull(message = "计划数量不能为空")
    @Min(value = 1, message = "计划数量最小为 1")
    @Max(value = 999999, message = "计划数量最大为 999999")
    private Integer planQty;

    /** 优先级：HIGH/NORMAL/LOW，缺省 NORMAL */
    private String priority;

    /** 计划开始/完成日期（可空，透传工单） */
    private LocalDate planStartTime;

    private LocalDate planEndTime;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
