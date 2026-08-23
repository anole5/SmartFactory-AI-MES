package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 异常处理建议生成入参
 */
@Getter
@Setter
public class ExceptionSuggestRequest {

    @NotNull(message = "exceptionId 不能为空")
    private Long exceptionId;
}
