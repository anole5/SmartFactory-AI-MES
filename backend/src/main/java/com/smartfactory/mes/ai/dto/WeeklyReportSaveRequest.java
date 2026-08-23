package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * AI 周报保存入参（同 (endDate, WEEK) 幂等覆盖，唯一键 uk_report_date_type 兜底）
 */
@Getter
@Setter
public class WeeklyReportSaveRequest {

    @NotNull(message = "endDate 不能为空")
    private LocalDate endDate;

    @NotBlank(message = "content 不能为空")
    private String content;
}
