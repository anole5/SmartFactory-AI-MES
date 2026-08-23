package com.smartfactory.mes.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.ai.client.DeepSeekClient;
import com.smartfactory.mes.ai.dto.AiAskVO;
import com.smartfactory.mes.ai.dto.AiReferenceVO;
import com.smartfactory.mes.ai.dto.KnowledgeDocQueryDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocSaveDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocVO;
import com.smartfactory.mes.ai.entity.MesAiQaRecord;
import com.smartfactory.mes.ai.entity.MesKnowledgeDoc;
import com.smartfactory.mes.ai.enums.AiIntent;
import com.smartfactory.mes.ai.enums.KnowledgeDocStatus;
import com.smartfactory.mes.ai.exception.AiServiceException;
import com.smartfactory.mes.ai.mapper.AiQaRecordMapper;
import com.smartfactory.mes.ai.mapper.KnowledgeDocMapper;
import com.smartfactory.mes.ai.service.KnowledgeService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库 Service 实现
 *
 * <p>问答 RAG 管线（参考尚硅谷掌柜问数"先召回再生成"思想，召回通道简化为关键词匹配）：
 * ① 关键词打分召回文档（top 3）→ ② 按 ## 段落切分命中段落（top 3）
 * → ③ 拼上下文调 flash 档 LLM 生成 → ④ 回答带引用落问答记录。
 * LLM 故障降级：命中段落原文直出（fallback=true），演示永不白屏。</p>
 */
@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeDocMapper, MesKnowledgeDoc>
        implements KnowledgeService {

    private static final String SYSTEM_PROMPT = "你是智能电视工厂的工艺知识助手。"
            + "只依据提供的知识库文档内容回答，文档没有的内容不要编造，"
            + "回答使用中文，简洁、分点说明。";

    private final AiQaRecordMapper qaRecordMapper;
    private final DeepSeekClient deepSeekClient;

    public KnowledgeServiceImpl(AiQaRecordMapper qaRecordMapper, DeepSeekClient deepSeekClient) {
        this.qaRecordMapper = qaRecordMapper;
        this.deepSeekClient = deepSeekClient;
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
    }

    @Override
    @Transactional
    public AiAskVO ask(String question) {
        // ① 关键词打分召回：仅 ENABLED 文档参与检索（停用 = 业务下线，不参与召回）
        List<MesKnowledgeDoc> enabled = this.list(new LambdaQueryWrapper<MesKnowledgeDoc>()
                .eq(MesKnowledgeDoc::getStatus, KnowledgeDocStatus.ENABLED));
        List<DocHit> docHits = enabled.stream()
                .map(doc -> new DocHit(doc, scoreDoc(doc, question)))
                .filter(hit -> hit.score > 0)
                .sorted(Comparator.comparingInt(DocHit::getScore).reversed())
                .limit(3)
                .collect(Collectors.toList());

        // ② 无命中：模板兜底话术 + 候选文档列表
        if (docHits.isEmpty()) {
            String answer = fallbackNoHit(enabled);
            Long recordId = saveRecord(question, answer, AiIntent.KNOWLEDGE, "");
            AiAskVO vo = new AiAskVO();
            vo.setAnswer(answer);
            vo.setReferences(Collections.emptyList());
            vo.setFallback(true);
            vo.setRecordId(recordId);
            return vo;
        }

        // ③ 段落召回：命中文档按 ## 切段，段落按关键词命中数打分取 top 3
        List<SectionHit> sections = collectSections(docHits, question);

        // ④ 拼上下文调 flash 档生成；LLM 故障降级为命中段落原文
        StringBuilder context = new StringBuilder("用户问题：").append(question).append("\n\n知识库文档片段：\n");
        for (SectionHit s : sections) {
            context.append("【文档：").append(s.docName).append("】").append(s.section).append("\n\n");
        }
        String answer;
        boolean fallback;
        try {
            answer = deepSeekClient.chatFast(SYSTEM_PROMPT, context.toString());
            fallback = false;
        } catch (AiServiceException e) {
            answer = fallbackFromSections(sections);
            fallback = true;
        }

        String refDocIds = docHits.stream()
                .map(hit -> String.valueOf(hit.doc.getId()))
                .collect(Collectors.joining(","));
        Long recordId = saveRecord(question, answer, AiIntent.KNOWLEDGE, refDocIds);

        AiAskVO vo = new AiAskVO();
        vo.setAnswer(answer);
        vo.setReferences(docHits.stream()
                .map(hit -> new AiReferenceVO(hit.doc.getId(), hit.doc.getDocName()))
                .collect(Collectors.toList()));
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

    // ------------------------------------------------------------
    // 私有工具
    // ------------------------------------------------------------

    private MesKnowledgeDoc mustExist(Long id) {
        MesKnowledgeDoc entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("文档不存在: id=" + id);
        }
        return entity;
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

    /** 命中文档按 ## 段落切分并按关键词命中数打分 */
    private List<SectionHit> collectSections(List<DocHit> docHits, String question) {
        List<SectionHit> result = new ArrayList<>();
        for (DocHit hit : docHits) {
            for (String section : hit.doc.getContent().split("(?=## )")) {
                int score = 0;
                if (StringUtils.hasText(hit.doc.getKeywords())) {
                    for (String kw : hit.doc.getKeywords().split("[,，]")) {
                        if (StringUtils.hasText(kw) && section.contains(kw.trim())) {
                            score++;
                        }
                    }
                }
                if (score > 0) {
                    result.add(new SectionHit(hit.doc.getDocName(), section.trim(), score));
                }
            }
        }
        result.sort(Comparator.comparingInt(SectionHit::getScore).reversed());
        return result.stream().limit(3).collect(Collectors.toList());
    }

    /** LLM 故障降级：命中段落原文直出（前端提示"模板回答"） */
    private String fallbackFromSections(List<SectionHit> sections) {
        StringBuilder sb = new StringBuilder("【模板回答】以下为知识库原文片段：\n\n");
        for (SectionHit s : sections) {
            sb.append("【文档：").append(s.docName).append("】\n").append(s.section).append("\n\n");
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

    /** 段落召回中间结果 */
    private static class SectionHit {
        final String docName;
        final String section;
        final int score;

        SectionHit(String docName, String section, int score) {
            this.docName = docName;
            this.section = section;
            this.score = score;
        }

        int getScore() {
            return score;
        }
    }
}
