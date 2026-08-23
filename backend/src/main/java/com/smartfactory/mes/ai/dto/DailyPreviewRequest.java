package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 生产日报生成入参
 */
@Getter
@Setter
public class DailyPreviewRequest {

    @NotNull(message = "reportDate 不能为空")
    private LocalDate reportDate;
}
