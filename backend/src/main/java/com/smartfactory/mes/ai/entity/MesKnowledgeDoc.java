package com.smartfactory.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.ai.enums.KnowledgeDocStatus;
import com.smartfactory.mes.ai.enums.KnowledgeDocType;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 知识库文档（SOP/质量标准/设备手册/故障手册，Markdown 内容）
 *
 * <p>检索策略：keywords 关键词召回 + 按 ## 段落切分，命中段落作为 LLM 上下文；
 * status=DISABLED 不参与检索（业务启停用与逻辑删除分层）。</p>
 */
@Getter
@Setter
@TableName("mes_knowledge_doc")
public class MesKnowledgeDoc extends BaseEntity {

    /** 文档名称 */
    private String docName;

    /** 文档类型：SOP/QUALITY_STANDARD/EQUIPMENT_MANUAL/FAULT_GUIDE */
    private KnowledgeDocType docType;

    /** 检索关键词（逗号分隔，中文/代码/枚举值混排） */
    private String keywords;

    /** 文档内容（Markdown，## 段落为检索粒度） */
    private String content;

    /** 状态：ENABLED/DISABLED */
    private KnowledgeDocStatus status;

    /** 备注 */
    private String remark;
}
