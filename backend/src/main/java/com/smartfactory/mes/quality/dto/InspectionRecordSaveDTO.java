package com.smartfactory.mes.quality.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 质检录入入参：合格/不良数量 + 不良明细行（不良行数量合计必须等于不良数量，Service 强校验）
 */
@Getter
@Setter
public class InspectionRecordSaveDTO {

    @NotNull(message = "质检任务不能为空")
    private Long inspectionTaskId;

    @NotNull(message = "合格数量不能为空")
    @Min(value = 0, message = "合格数量不能为负")
    private Integer goodQty;

    @NotNull(message = "不良数量不能为空")
    @Min(value = 0, message = "不良数量不能为负")
    private Integer defectQty;

    @Size(max = 255, message = "检验说明最长 255 位")
    private String remark;

    /** 不良明细行（defectQty > 0 时必填） */
    @Valid
    private List<DefectItem> defectItems;

    /** 不良明细行：一次检验每种不良码一条 */
    @Getter
    @Setter
    public static class DefectItem {

        @NotBlank(message = "不良代码不能为空")
        @Size(max = 64, message = "不良代码最长 64 位")
        private String defectCode;

        @NotNull(message = "不良数量不能为空")
        @Min(value = 1, message = "不良数量至少为 1")
        private Integer defectQty;

        @Size(max = 255, message = "备注最长 255 位")
        private String remark;
    }
}
