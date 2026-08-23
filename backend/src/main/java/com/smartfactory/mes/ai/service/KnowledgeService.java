package com.smartfactory.mes.ai.service;

import com.smartfactory.mes.ai.dto.AiAskVO;
import com.smartfactory.mes.ai.dto.KnowledgeDocQueryDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocSaveDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocVO;
import com.smartfactory.mes.ai.dto.ReindexVO;
import com.smartfactory.mes.ai.sse.StreamSink;
import com.smartfactory.mes.common.api.PageResult;

/**
 * 知识库文档管理 + SOP 问答服务（RAG 管线：关键词召回 → 段落切分 → LLM 生成 → 引用）
 */
public interface KnowledgeService {

    /** 文档分页 */
    PageResult<KnowledgeDocVO> page(KnowledgeDocQueryDTO query);

    /** 文档详情 */
    KnowledgeDocVO getDetail(Long id);

    /** 新增文档（默认 ENABLED） */
    Long create(KnowledgeDocSaveDTO dto);

    /** 编辑文档 */
    void update(Long id, KnowledgeDocSaveDTO dto);

    /** 知识库问答：检索命中走 LLM 生成，未命中/LLM 故障走模板兜底，全程落问答记录 */
    AiAskVO ask(String question);

    /** 流式问答：管线同 {@link #ask}，生成调用流式化，delta 逐块推 sink；客户端停止返回 null 不落记录 */
    AiAskVO askStream(String question, StreamSink sink);

    /** 问答反馈（1 有用 / 0 无用，覆盖回填） */
    void feedback(Long recordId, boolean useful);

    /** 向量索引全量重建（幂等）：删集合重建 + 全部 ENABLED 文档重新切块入库；qdrant/TEI 故障直接抛异常不降级 */
    ReindexVO reindex();
}
