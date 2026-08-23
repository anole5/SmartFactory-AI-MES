package com.smartfactory.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.ai.enums.AiIntent;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 问答记录（每次提问一条，只增不改；反馈 useful 覆盖回填）
 */
@Getter
@Setter
@TableName("mes_ai_qa_record")
public class MesAiQaRecord extends BaseEntity {

    /** 用户问题 */
    private String question;

    /** AI 回答 */
    private String answer;

    /** 意图：OVERVIEW/KNOWLEDGE/EXCEPTION/REPORT */
    private AiIntent intent;

    /** 引用文档 ID（逗号分隔，知识问答场景） */
    private String refDocIds;

    /** 反馈：1 有用 / 0 无用 / NULL 未反馈 */
    private Integer useful;
}
