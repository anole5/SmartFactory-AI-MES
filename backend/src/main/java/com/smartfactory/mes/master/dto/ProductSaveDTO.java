package com.smartfactory.mes.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 产品新增/编辑入参
 */
@Getter
@Setter
public class ProductSaveDTO {

    @NotBlank(message = "产品编码不能为空")
    @Size(max = 64, message = "产品编码最长 64 位")
    private String productCode;

    @NotBlank(message = "产品名称不能为空")
    @Size(max = 128, message = "产品名称最长 128 位")
    private String productName;

    @Size(max = 32, message = "产品类型最长 32 位")
    private String productType;

    @Size(max = 255, message = "规格型号最长 255 位")
    private String specification;

    @Size(max = 32, message = "单位最长 32 位")
    private String unit;
}
