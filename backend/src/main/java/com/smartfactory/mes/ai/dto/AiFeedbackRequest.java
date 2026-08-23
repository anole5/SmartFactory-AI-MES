package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 回答反馈入参（有用/无用）
 */
@Getter
@Setter
public class AiFeedbackRequest {

    @NotNull(message = "useful 不能为空（1 有用 / 0 无用）")
    private Boolean useful;
}
