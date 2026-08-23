package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 问答入参（知识库场景）
 */
@Getter
@Setter
public class AiAskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题最长 500 字")
    private String question;
}
