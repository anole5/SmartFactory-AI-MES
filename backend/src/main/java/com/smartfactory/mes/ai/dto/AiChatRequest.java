package com.smartfactory.mes.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一 AI 助手对话入参
 */
@Getter
@Setter
public class AiChatRequest {

    @NotBlank(message = "question 不能为空")
    @Size(max = 500, message = "question 最长 500 字")
    private String question;
}
