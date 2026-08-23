package com.smartfactory.mes.production.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 物料批次创建入参（第 6 周）：批次号由生成器生成（MB 前缀）
 */
@Getter
@Setter
public class MaterialBatchSaveDTO {

    @NotNull(message = "物料不能为空")
    private Long materialId;

    @NotNull(message = "批次数量不能为空")
    @Min(value = 1, message = "批次数量至少为 1")
    private Integer batchQty;

    /** 入库日期（缺省今天） */
    private LocalDate inDate;

    @Size(max = 64, message = "供应商最长 64 位")
    private String supplier;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
