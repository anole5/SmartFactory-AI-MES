package com.smartfactory.mes.ai.dto;

import com.smartfactory.mes.ai.entity.MesKnowledgeDoc;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库文档出参
 */
@Getter
@Setter
public class KnowledgeDocVO {

    private Long id;
    private String docName;
    private String docType;
    private String keywords;
    private String content;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeDocVO of(MesKnowledgeDoc entity) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        vo.setId(entity.getId());
        vo.setDocName(entity.getDocName());
        vo.setDocType(entity.getDocType().getCode());
        vo.setKeywords(entity.getKeywords());
        vo.setContent(entity.getContent());
        vo.setStatus(entity.getStatus().getCode());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
