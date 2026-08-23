package com.smartfactory.mes.integration.wms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单领料入参（第 5 周：按工单 BOM 关键物料自动领料）
 */
@Getter
@Setter
public class PickRequest {

    @NotNull(message = "工单不能为空")
    private Long workOrderId;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
