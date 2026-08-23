package com.smartfactory.mes.ai.dto;

import com.smartfactory.mes.ai.enums.KnowledgeDocStatus;
import com.smartfactory.mes.ai.enums.KnowledgeDocType;
import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库文档分页查询入参
 */
@Getter
@Setter
public class KnowledgeDocQueryDTO extends PageQuery {

    /** 文档名称关键字 */
    private String keyword;

    /** 文档类型过滤 */
    private KnowledgeDocType docType;

    /** 状态过滤 */
    private KnowledgeDocStatus status;
}
