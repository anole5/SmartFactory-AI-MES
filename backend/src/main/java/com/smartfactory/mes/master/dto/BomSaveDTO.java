package com.smartfactory.mes.master.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * BOM 新增/编辑入参（头 + 明细整单提交）
 */
@Getter
@Setter
public class BomSaveDTO {

    @NotNull(message = "产品不能为空")
    private Long productId;

    @Size(max = 16, message = "版本号最长 16 位")
    private String version;

    /** 生效日期（仅展示用，状态机控制实际生效） */
    private LocalDate effectiveDate;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;

    @NotEmpty(message = "BOM 明细不能为空")
    @Valid
    private List<BomItemDTO> items;

    /**
     * BOM 明细行入参（materialId 引用物料主数据，快照字段服务端回填）
     */
    @Getter
    @Setter
    public static class BomItemDTO {

        @NotNull(message = "物料不能为空")
        private Long materialId;

        @NotNull(message = "单位用量不能为空")
        @DecimalMin(value = "0.0001", message = "单位用量必须大于 0")
        private BigDecimal requiredQty;

        /** 损耗率（%），可空，默认 0 */
        @DecimalMin(value = "0", message = "损耗率不能为负数")
        @DecimalMax(value = "100", message = "损耗率不能超过 100")
        private BigDecimal lossRate;

        @Size(max = 255, message = "备注最长 255 位")
        private String remark;
    }
}
