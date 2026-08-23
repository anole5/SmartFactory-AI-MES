package com.smartfactory.mes.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 回答引用来源（命中知识库文档）
 */
@Getter
@Setter
@AllArgsConstructor
public class AiReferenceVO {

    private Long docId;

    private String docName;
}
