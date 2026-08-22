package com.smartfactory.mes.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 物料新增/编辑入参
 */
@Getter
@Setter
public class MaterialSaveDTO {

    @NotBlank(message = "物料编码不能为空")
    @Size(max = 64, message = "物料编码最长 64 位")
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    @Size(max = 128, message = "物料名称最长 128 位")
    private String materialName;

    @Size(max = 32, message = "物料类型最长 32 位")
    private String materialType;

    @Size(max = 32, message = "单位最长 32 位")
    private String unit;

    /** 是否批次追溯 */
    private Boolean traceRequired;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
