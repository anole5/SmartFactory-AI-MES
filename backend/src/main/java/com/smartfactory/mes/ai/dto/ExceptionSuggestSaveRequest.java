package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 异常处理建议保存回写入参（admin/qa：写回异常单 + 追溯）
 */
@Getter
@Setter
public class ExceptionSuggestSaveRequest {

    @NotNull(message = "exceptionId 不能为空")
    private Long exceptionId;

    @NotBlank(message = "suggestion 不能为空")
    private String suggestion;
}
