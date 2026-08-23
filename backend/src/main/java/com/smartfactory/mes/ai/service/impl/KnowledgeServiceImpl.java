package com.smartfactory.mes.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.ai.client.DeepSeekClient;
import com.smartfactory.mes.ai.client.EmbeddingClient;
import com.smartfactory.mes.ai.client.QdrantClient;
import com.smartfactory.mes.ai.dto.AiAskVO;
import com.smartfactory.mes.ai.dto.AiReferenceVO;
import com.smartfactory.mes.ai.dto.KnowledgeDocQueryDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocSaveDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocVO;
import com.smartfactory.mes.ai.dto.ReindexVO;
import com.smartfactory.mes.ai.entity.MesAiQaRecord;
import com.smartfactory.mes.ai.entity.MesKnowledgeDoc;
import com.smartfactory.mes.ai.enums.AiIntent;
import com.smartfactory.mes.ai.enums.KnowledgeDocStatus;
import com.smartfactory.mes.ai.exception.AiServiceException;
import com.smartfactory.mes.ai.mapper.AiQaRecordMapper;
import com.smartfactory.mes.ai.mapper.KnowledgeDocMapper;
import com.smartfactory.mes.ai.service.KnowledgeService;
import com.smartfactory.mes.ai.sse.StreamSink;
import com.smartfactory.mes.ai.support.KnowledgeChunker;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 知识库 Service 实现
 *
 * <p>问答 RAG 管线（参考尚硅谷掌柜问数"先召回再生成"思想）：
 * ① 双路召回——关键词通道（文档打分 → 规范切块 → 块打分）+ 向量通道（TEI embedding → Qdrant 检索），
 * RRF(k) 融合取 top3 合并段落（语义近义词问法也能命中，如"烧录失败"召回"烧录不良"文档）；
 * ② 拼上下文调 flash 档 LLM 生成 → ③ 回答带引用（引用从合并段落派生）落问答记录。
 * LLM 故障降级：命中段落原文直出（fallback=true），演示永不白屏。
 * 向量通道故障（qdrant/TEI 宕机）自动退化纯关键词通道。</p>
 */
@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeDocMapper, MesKnowledgeDoc>
        implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private static final String SYSTEM_PROMPT = "你是智能电视工厂的工艺知识助手。"
            + "只依据提供的知识库文档内容回答，文档没有的内容不要编造，"
            + "回答使用中文，简洁、分点说明。";

    private final AiQaRecordMapper qaRecordMapper;
    private final DeepSeekClient deepSeekClient;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final int topSections;
    private final int vectorLimit;
    private final double vectorThreshold;
    private final int rrfK;
    private final int maxChunkChars;

    public KnowledgeServiceImpl(AiQaRecordMapper qaRecordMapper, DeepSeekClient deepSeekClient,
                                EmbeddingClient embeddingClient, QdrantClient qdrantClient,
                                @Value("${ai.knowledge.recall.top-sections:3}") int topSections,
                                @Value("${ai.knowledge.recall.vector-limit:8}") int vectorLimit,
                                @Value("${ai.knowledge.recall.vector-threshold:0.30}") double vectorThreshold,
                                @Value("${ai.knowledge.recall.rrf-k:60}") int rrfK,
                                @Value("${ai.embedding.max-chunk-chars:400}") int maxChunkChars) {
        this.qaRecordMapper = qaRecordMapper;
        this.deepSeekClient = deepSeekClient;
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;
        this.topSections = topSections;
        this.vectorLimit = vectorLimit;
        this.vectorThreshold = vectorThreshold;
        this.rrfK = rrfK;
        this.maxChunkChars = maxChunkChars;
    }

    @Override
    public PageResult<KnowledgeDocVO> page(KnowledgeDocQueryDTO query) {
        LambdaQueryWrapper<MesKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getKeyword()), MesKnowledgeDoc::getDocName, query.getKeyword())
                .eq(query.getDocType() != null, MesKnowledgeDoc::getDocType, query.getDocType())
                .eq(query.getStatus() != null, MesKnowledgeDoc::getStatus, query.getStatus())
                .orderByDesc(MesKnowledgeDoc::getId);
        Page<MesKnowledgeDoc> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<KnowledgeDocVO> vos = page.getRecords().stream()
                .map(KnowledgeDocVO::of).collect(Collectors.toList());
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public KnowledgeDocVO getDetail(Long id) {
        return KnowledgeDocVO.of(mustExist(id));
    }

    @Override
    @Transactional
    public Long create(KnowledgeDocSaveDTO dto) {
        MesKnowledgeDoc entity = new MesKnowledgeDoc();
        entity.setDocName(dto.getDocName());
        entity.setDocType(dto.getDocType());
        entity.setKeywords(dto.getKeywords());
        entity.setContent(dto.getContent());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : KnowledgeDocStatus.ENABLED);
        entity.setRemark(dto.getRemark());
        this.save(entity);
        // 写路径同步向量索引：ENABLED 入库向量，DISABLED 无点（失败只告警不阻断 CRUD）
        syncDocVector(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, KnowledgeDocSaveDTO dto) {
        MesKnowledgeDoc entity = mustExist(id);
        entity.setDocName(dto.getDocName());
        entity.setDocType(dto.getDocType());
        entity.setKeywords(dto.getKeywords());
        entity.setContent(dto.getContent());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : entity.getStatus());
        entity.setRemark(dto.getRemark());
        this.updateById(entity);
        syncDocVector(entity);
    }

    @Override
    @Transactional
    public AiAskVO ask(String question) {
        // ① 双路召回（关键词 + 向量）RRF 合并取 top3 段落
        List<SectionHit> sections = recall(question);

        // ② 两通道均空：模板兜底话术 + 候选文档列表
        if (sections.isEmpty()) {
            String answer = fallbackNoHit(enabledDocs());
            Long recordId = saveRecord(question, answer, AiIntent.KNOWLEDGE, "");
            AiAskVO vo = new AiAskVO();
            vo.setAnswer(answer);
            vo.setReferences(Collections.emptyList());
            vo.setFallback(true);
            vo.setRecordId(recordId);
            return vo;
        }

        // ③ 拼上下文调 flash 档生成；LLM 故障降级为命中段落原文
        String answer;
        boolean fallback;
        try {
            answer = deepSeekClient.chatFast(SYSTEM_PROMPT, buildContext(question, sections));
            fallback = false;
        } catch (AiServiceException e) {
            answer = fallbackFromSections(sections);
            fallback = true;
        }

        Long recordId = saveRecord(question, answer, AiIntent.KNOWLEDGE, refDocIds(sections));
        AiAskVO vo = new AiAskVO();
        vo.setAnswer(answer);
        vo.setReferences(buildReferences(sections));
        vo.setFallback(fallback);
        vo.setRecordId(recordId);
        return vo;
    }

    @Override
    public AiAskVO askStream(String question, StreamSink sink) {
        // 管线与 ask() 一致，仅生成调用换成流式：delta 逐块推给前端（打字机）。
        // 不加 @Transactional：问答记录是流结束后单条 INSERT（自动提交），
        // 无需为一次插入把数据库连接占满整个 LLM 流式周期。
        List<SectionHit> sections = recall(question);
        if (sections.isEmpty()) {
            String answer = fallbackNoHit(enabledDocs());
            sink.sendDelta(answer);
            if (sink.isCancelled()) {
                return null;
            }
            Long recordId = saveRecord(question, answer, AiIntent.KNOWLEDGE, "");
            AiAskVO vo = new AiAskVO();
            vo.setAnswer(answer);
            vo.setReferences(Collections.emptyList());
            vo.setFallback(true);
            vo.setRecordId(recordId);
            return vo;
        }

        StringBuilder answer = new StringBuilder();
        boolean fallback;
        try {
            deepSeekClient.chatFastStream(SYSTEM_PROMPT, buildContext(question, sections),
                    chunk -> {
                        answer.append(chunk.getContent());
                        sink.sendDelta(chunk.getContent());
                    });
            fallback = false;
        } catch (AiServiceException e) {
            if (sink.isCancelled()) {
                return null;
            }
            String text = fallbackFromSections(sections);
            answer.append(text);
            sink.sendDelta(text);
            fallback = true;
        }
        if (sink.isCancelled()) {
            return null;
        }

        Long recordId = saveRecord(question, answer.toString(), AiIntent.KNOWLEDGE, refDocIds(sections));
        AiAskVO vo = new AiAskVO();
        vo.setAnswer(answer.toString());
        vo.setReferences(buildReferences(sections));
        vo.setFallback(fallback);
        vo.setRecordId(recordId);
        return vo;
    }

    @Override
    @Transactional
    public void feedback(Long recordId, boolean useful) {
        MesAiQaRecord record = qaRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("问答记录不存在: id=" + recordId);
        }
        record.setUseful(useful ? 1 : 0);
        qaRecordMapper.updateById(record);
    }

    @Override
    public ReindexVO reindex() {
        // 全量重建：删集合重建（兼作孤儿点自愈）→ 全部 ENABLED 文档重新切块入库。
        // 此方法不允许降级：qdrant/TEI 故障直接抛 AiServiceException（reindex 是真实金丝雀）。
        qdrantClient.deleteCollection();
        qdrantClient.ensureCollection();
        List<MesKnowledgeDoc> enabled = enabledDocs();
        int sectionCount = 0;
        for (MesKnowledgeDoc doc : enabled) {
            List<QdrantClient.Point> points = buildPoints(doc);
            sectionCount += points.size();
            qdrantClient.upsert(points);
        }
        return new ReindexVO(enabled.size(), sectionCount);
    }

    // ------------------------------------------------------------
    // 双路召回
    // ------------------------------------------------------------

    /** 双路召回 + RRF 合并：两通道均空返回空列表（走无命中兜底），单通道空则另一通道直出 */
    private List<SectionHit> recall(String question) {
        List<SectionHit> keyword = keywordRecall(question);
        List<SectionHit> vector = vectorRecall(question);
        if (keyword.isEmpty()) {
            return vector.stream().limit(topSections).collect(Collectors.toList());
        }
        if (vector.isEmpty()) {
            return keyword.stream().limit(topSections).collect(Collectors.toList());
        }
        return rrfMerge(keyword, vector).stream().limit(topSections).collect(Collectors.toList());
    }

    /** 关键词通道：文档打分（top3）→ 规范切块 → 块内关键词命中打分（与索引同一切法，id 对齐） */
    private List<SectionHit> keywordRecall(String question) {
        List<SectionHit> hits = new ArrayList<>();
        for (DocHit dh : recallDocs(question)) {
            for (KnowledgeChunker.Chunk c : KnowledgeChunker.chunk(
                    dh.doc.getId(), dh.doc.getDocName(), String.valueOf(dh.doc.getDocType()),
                    dh.doc.getKeywords(), dh.doc.getContent(), maxChunkChars)) {
                int score = scoreChunk(dh.doc.getKeywords(), c.text);
                if (score > 0) {
                    hits.add(new SectionHit(dh.doc.getId(), dh.doc.getDocName(), c.idx, c.text, score, 0));
                }
            }
        }
        return hits;
    }

    /** 向量通道：embed(question) → Qdrant 检索；服务不可用自动退化（返回空，上游走关键词） */
    private List<SectionHit> vectorRecall(String question) {
        try {
            float[] query = embeddingClient.embed(question);
            List<QdrantClient.ScoredPoint> points = qdrantClient.search(query, vectorLimit, vectorThreshold);
            List<SectionHit> hits = new ArrayList<>();
            for (QdrantClient.ScoredPoint p : points) {
                Map<String, Object> payload = p.payload;
                if (payload == null) {
                    continue;
                }
                long docId = ((Number) payload.getOrDefault("doc_id", 0)).longValue();
                int idx = ((Number) payload.getOrDefault("section_idx", 0)).intValue();
                String docName = String.valueOf(payload.getOrDefault("doc_name", ""));
                String text = String.valueOf(payload.getOrDefault("section_text", ""));
                if (docId <= 0 || text.isEmpty()) {
                    continue;
                }
                hits.add(new SectionHit(docId, docName, idx, text, 0, p.score));
            }
            return hits;
        } catch (AiServiceException e) {
            log.warn("向量召回通道不可用，退化纯关键词: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** RRF 融合：通道内按分数排名，得分 Σ 1/(k+rank)，同键（docId#idx）两通道分数相加 */
    private List<SectionHit> rrfMerge(List<SectionHit> keyword, List<SectionHit> vector) {
        Map<String, SectionHit> byKey = new LinkedHashMap<>();
        Map<String, Double> fused = new LinkedHashMap<>();
        accumulate(byKey, fused, keyword, Comparator.comparingInt((SectionHit h) -> h.keywordScore).reversed());
        accumulate(byKey, fused, vector, Comparator.comparingDouble((SectionHit h) -> h.vectorScore).reversed());
        List<SectionHit> merged = new ArrayList<>(byKey.values());
        merged.sort((a, b) -> Double.compare(fused.get(b.key()), fused.get(a.key())));
        return merged;
    }

    private void accumulate(Map<String, SectionHit> byKey, Map<String, Double> fused,
                            List<SectionHit> channel, Comparator<SectionHit> rankCmp) {
        List<SectionHit> ranked = new ArrayList<>(channel);
        ranked.sort(rankCmp);
        for (int i = 0; i < ranked.size(); i++) {
            SectionHit hit = ranked.get(i);
            byKey.putIfAbsent(hit.key(), hit);
            fused.merge(hit.key(), 1.0 / (rrfK + i + 1), Double::sum);
        }
    }

    /** 参与召回的全部启用文档 */
    private List<MesKnowledgeDoc> enabledDocs() {
        return this.list(new LambdaQueryWrapper<MesKnowledgeDoc>()
                .eq(MesKnowledgeDoc::getStatus, KnowledgeDocStatus.ENABLED));
    }

    /** 文档打分召回：每个关键词命中 +1、文档名命中 +2，score>0 按分数取 top 3 */
    private List<DocHit> recallDocs(String question) {
        return enabledDocs().stream()
                .map(doc -> new DocHit(doc, scoreDoc(doc, question)))
                .filter(hit -> hit.score > 0)
                .sorted(Comparator.comparingInt(DocHit::getScore).reversed())
                .limit(3)
                .collect(Collectors.toList());
    }

    /** 文档打分：每个关键词命中 +1，文档名整体命中 +2（防止关键词与文档名脱节） */
    private int scoreDoc(MesKnowledgeDoc doc, String question) {
        int score = 0;
        if (StringUtils.hasText(doc.getKeywords())) {
            for (String kw : doc.getKeywords().split("[,，]")) {
                if (StringUtils.hasText(kw) && question.toLowerCase().contains(kw.trim().toLowerCase())) {
                    score++;
                }
            }
        }
        if (question.contains(doc.getDocName())) {
            score += 2;
        }
        return score;
    }

    /** 块内关键词命中打分 */
    private int scoreChunk(String keywords, String text) {
        int score = 0;
        if (StringUtils.hasText(keywords)) {
            for (String kw : keywords.split("[,，]")) {
                if (StringUtils.hasText(kw) && text.contains(kw.trim())) {
                    score++;
                }
            }
        }
        return score;
    }

    // ------------------------------------------------------------
    // 向量索引写路径
    // ------------------------------------------------------------

    /** 文档向量同步：停用删点；启用先删后写（内容更新即重建）。失败只告警不阻断文档 CRUD（reindex 可修复） */
    private void syncDocVector(MesKnowledgeDoc doc) {
        try {
            qdrantClient.ensureCollection();
            qdrantClient.deleteByDocId(doc.getId());
            if (doc.getStatus() == KnowledgeDocStatus.ENABLED) {
                qdrantClient.upsert(buildPoints(doc));
            }
        } catch (AiServiceException e) {
            log.warn("文档向量同步失败（文档 CRUD 不受影响，可用 reindex 修复）: docId={}, err={}",
                    doc.getId(), e.getMessage());
        }
    }

    /** 文档切块 + 逐块 embedding → 向量点（reindex 与写路径共用） */
    private List<QdrantClient.Point> buildPoints(MesKnowledgeDoc doc) {
        List<QdrantClient.Point> points = new ArrayList<>();
        for (KnowledgeChunker.Chunk c : KnowledgeChunker.chunk(
                doc.getId(), doc.getDocName(), String.valueOf(doc.getDocType()),
                doc.getKeywords(), doc.getContent(), maxChunkChars)) {
            points.add(new QdrantClient.Point(KnowledgeChunker.pointId(doc.getId(), c.idx),
                    embeddingClient.embed(c.text), c.payload));
        }
        return points;
    }

    // ------------------------------------------------------------
    // 生成与落库
    // ------------------------------------------------------------

    private MesKnowledgeDoc mustExist(Long id) {
        MesKnowledgeDoc entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("文档不存在: id=" + id);
        }
        return entity;
    }

    /** 拼 LLM 上下文：用户问题 + 命中段落（标注来源文档） */
    private String buildContext(String question, List<SectionHit> sections) {
        StringBuilder context = new StringBuilder("用户问题：").append(question).append("\n\n知识库文档片段：\n");
        for (SectionHit s : sections) {
            context.append("【文档：").append(s.docName).append("】").append(s.text).append("\n\n");
        }
        return context.toString();
    }

    /** LLM 故障降级：命中段落原文直出（前端提示"模板回答"） */
    private String fallbackFromSections(List<SectionHit> sections) {
        StringBuilder sb = new StringBuilder("【模板回答】以下为知识库原文片段：\n\n");
        for (SectionHit s : sections) {
            sb.append("【文档：").append(s.docName).append("】\n").append(s.text).append("\n\n");
        }
        return sb.toString();
    }

    /** 无命中兜底话术（附候选文档列表） */
    private String fallbackNoHit(List<MesKnowledgeDoc> enabled) {
        StringBuilder sb = new StringBuilder("知识库暂无与问题直接相关的内容，建议换个说法或联系工艺工程师。");
        if (!enabled.isEmpty()) {
            sb.append("\n\n当前知识库可查询文档：");
            sb.append(enabled.stream().map(MesKnowledgeDoc::getDocName).collect(Collectors.joining("、")));
        }
        return sb.toString();
    }

    /** 引用：合并段落去重后的文档（向量命中的文档同样进引用） */
    private List<AiReferenceVO> buildReferences(List<SectionHit> sections) {
        List<AiReferenceVO> refs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (SectionHit s : sections) {
            if (seen.add(s.docId)) {
                refs.add(new AiReferenceVO(s.docId, s.docName));
            }
        }
        return refs;
    }

    /** 命中文档 id 逗号拼接（问答记录留痕召回来源） */
    private String refDocIds(List<SectionHit> sections) {
        return sections.stream()
                .map(s -> String.valueOf(s.docId))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private Long saveRecord(String question, String answer, AiIntent intent, String refDocIds) {
        MesAiQaRecord record = new MesAiQaRecord();
        record.setQuestion(question);
        record.setAnswer(answer);
        record.setIntent(intent);
        record.setRefDocIds(StringUtils.hasText(refDocIds) ? refDocIds : null);
        qaRecordMapper.insert(record);
        return record.getId();
    }

    /** 文档召回中间结果 */
    private static class DocHit {
        final MesKnowledgeDoc doc;
        final int score;

        DocHit(MesKnowledgeDoc doc, int score) {
            this.doc = doc;
            this.score = score;
        }

        int getScore() {
            return score;
        }
    }

    /** 段落召回中间结果（两通道共用；key = docId#idx 与索引点 id 对齐） */
    private static class SectionHit {
        final long docId;
        final String docName;
        final int idx;
        final String text;
        final int keywordScore;
        final double vectorScore;

        SectionHit(long docId, String docName, int idx, String text, int keywordScore, double vectorScore) {
            this.docId = docId;
            this.docName = docName;
            this.idx = idx;
            this.text = text;
            this.keywordScore = keywordScore;
            this.vectorScore = vectorScore;
        }

        String key() {
            return docId + "#" + idx;
        }
    }
}
