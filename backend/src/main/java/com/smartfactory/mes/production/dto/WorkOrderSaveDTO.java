package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.production.enums.OrderPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生产工单新增/编辑入参
 */
@Getter
@Setter
public class WorkOrderSaveDTO {

    @NotNull(message = "产品不能为空")
    private Long productId;

    @NotNull(message = "计划数量不能为空")
    @Min(value = 1, message = "计划数量最小为 1")
    @Max(value = 999999, message = "计划数量最大为 999999")
    private Integer planQty;

    @Size(max = 64, message = "外部订单号最长 64 位")
    private String externalOrderNo;

    /** 优先级，缺省 NORMAL */
    private OrderPriority priority;

    /** 计划开始/结束时间（可空，演示场景宽松处理） */
    private LocalDateTime planStartTime;

    private LocalDateTime planEndTime;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
