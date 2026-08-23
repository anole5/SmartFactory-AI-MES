package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 生产日报保存入参（同一 report_date 幂等覆盖）
 */
@Getter
@Setter
public class DailyReportSaveRequest {

    @NotNull(message = "reportDate 不能为空")
    private LocalDate reportDate;

    @NotBlank(message = "content 不能为空")
    private String content;
}
