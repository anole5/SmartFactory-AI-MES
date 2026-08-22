package com.smartfactory.mes.production.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报工入参：合格 + 不良 = 报工数量由 Service 强校验（DTO 只做基本约束）
 */
@Getter
@Setter
public class WorkReportSaveDTO {

    @NotNull(message = "工序任务不能为空")
    private Long taskId;

    @NotNull(message = "报工数量不能为空")
    @Min(value = 0, message = "报工数量不能为负")
    private Integer reportQty;

    @NotNull(message = "合格数量不能为空")
    @Min(value = 0, message = "合格数量不能为负")
    private Integer goodQty;

    @NotNull(message = "不良数量不能为空")
    @Min(value = 0, message = "不良数量不能为负")
    private Integer defectQty;

    /** 生产批次号（可空，第 2 周不做 SN 绑定） */
    @Size(max = 64, message = "批次号最长 64 位")
    private String productBatchNo;

    /** 本批次开始时间（缺省当前时间） */
    private LocalDateTime startTime;

    /** 本批次结束时间（缺省当前时间） */
    private LocalDateTime endTime;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
