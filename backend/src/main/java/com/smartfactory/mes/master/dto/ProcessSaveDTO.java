package com.smartfactory.mes.master.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工序新增/编辑入参
 */
@Getter
@Setter
public class ProcessSaveDTO {

    @NotBlank(message = "工序编码不能为空")
    @Size(max = 64, message = "工序编码最长 64 位")
    private String processCode;

    @NotBlank(message = "工序名称不能为空")
    @Size(max = 128, message = "工序名称最长 128 位")
    private String processName;

    /** 是否需要质检 */
    private Boolean needInspection;

    /** 标准工时（分钟） */
    @NotNull(message = "标准工时不能为空")
    @DecimalMin(value = "0", message = "标准工时不能为负数")
    private BigDecimal standardMinutes;

    @Size(max = 255, message = "工序说明最长 255 位")
    private String description;
}
