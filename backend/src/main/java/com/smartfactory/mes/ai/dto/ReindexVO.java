package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 向量索引重建结果（第 7 周：POST /ai/knowledge/reindex）
 */
@Getter
@Setter
public class ReindexVO {

    /** 参与重建的 ENABLED 文档数 */
    private int docCount;

    /** 入库向量段数（切块总数） */
    private int sectionCount;

    public ReindexVO(int docCount, int sectionCount) {
        this.docCount = docCount;
        this.sectionCount = sectionCount;
    }
}
