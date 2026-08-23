package com.smartfactory.mes.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 关键件批次绑定入参（第 6 周）：报工内嵌绑定与补录接口共用的单行 {物料, 批次号}
 */
@Getter
@Setter
public class MaterialBatchBindDTO {

    @NotNull(message = "物料不能为空")
    private Long materialId;

    @NotBlank(message = "物料批次号不能为空")
    @Size(max = 64, message = "物料批次号最长 64 位")
    private String batchNo;
}
