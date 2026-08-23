package com.smartfactory.mes.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 异常单手工创建入参（source_type=MANUAL，不良生成异常单走 DefectService.toException）
 */
@Getter
@Setter
public class ExceptionSaveDTO {

    @NotBlank(message = "异常描述不能为空")
    @Size(max = 255, message = "异常描述最长 255 位")
    private String description;

    /** 工单 ID（可空；关联后可写追溯） */
    private Long workOrderId;

    /** 工序任务 ID（可空） */
    private Long operationTaskId;

    /** 质检任务 ID（可空） */
    private Long inspectionTaskId;

    /** 不良代码（手工创建可空） */
    @Size(max = 64, message = "不良代码最长 64 位")
    private String defectCode;
}
