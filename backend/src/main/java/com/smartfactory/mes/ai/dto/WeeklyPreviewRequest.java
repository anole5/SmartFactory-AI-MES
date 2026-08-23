package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * AI 周报生成入参（endDate = 截止日期，本周窗口 = E-7..E-1，上周 = E-14..E-8）
 */
@Getter
@Setter
public class WeeklyPreviewRequest {

    @NotNull(message = "endDate 不能为空")
    private LocalDate endDate;
}
