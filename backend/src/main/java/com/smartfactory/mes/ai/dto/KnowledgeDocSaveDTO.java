package com.smartfactory.mes.ai.dto;

import com.smartfactory.mes.ai.enums.KnowledgeDocStatus;
import com.smartfactory.mes.ai.enums.KnowledgeDocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库文档新增/编辑入参
 */
@Getter
@Setter
public class KnowledgeDocSaveDTO {

    @NotBlank(message = "文档名称不能为空")
    @Size(max = 128, message = "文档名称最长 128 位")
    private String docName;

    @NotNull(message = "文档类型不能为空")
    private KnowledgeDocType docType;

    @Size(max = 500, message = "关键词最长 500 位")
    private String keywords;

    @NotBlank(message = "文档内容不能为空")
    private String content;

    private KnowledgeDocStatus status;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
