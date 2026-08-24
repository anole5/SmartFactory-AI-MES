package com.smartfactory.mes.ai.service.impl;

import com.smartfactory.mes.ai.client.DeepSeekClient;
import com.smartfactory.mes.ai.client.EmbeddingClient;
import com.smartfactory.mes.ai.client.QdrantClient;
import com.smartfactory.mes.ai.entity.MesKnowledgeDoc;
import com.smartfactory.mes.ai.enums.KnowledgeDocStatus;
import com.smartfactory.mes.ai.mapper.AiQaRecordMapper;
import com.smartfactory.mes.ai.mapper.KnowledgeDocMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 知识库双路召回单测（第 8 周）：手动 new 9 参构造器（@Value 参数直接传值），
 * baseMapper 反射注入供 enabledDocs() 使用；private 方法经 ReflectionTestUtils 直测。
 * 钉死：关键词打分规则（关键词 +1 / 文档名 +2）、大小写不敏感、RRF 同键合并。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImplTest {

    @Mock
    private AiQaRecordMapper qaRecordMapper;
    @Mock
    private DeepSeekClient deepSeekClient;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private QdrantClient qdrantClient;
    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;

    private KnowledgeServiceImpl service() {
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(qaRecordMapper, deepSeekClient,
                embeddingClient, qdrantClient, 3, 8, 0.30, 60, 400);
        ReflectionTestUtils.setField(service, "baseMapper", knowledgeDocMapper);
        return service;
    }

    private static MesKnowledgeDoc doc(long id, String name, String keywords, String content) {
        MesKnowledgeDoc doc = new MesKnowledgeDoc();
        doc.setId(id);
        doc.setDocName(name);
        doc.setKeywords(keywords);
        doc.setContent(content);
        doc.setStatus(KnowledgeDocStatus.ENABLED);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> recall(KnowledgeServiceImpl service, String question) {
        return (List<Object>) ReflectionTestUtils.invokeMethod(service, "recall", question);
    }

    private static String key(Object hit) throws Exception {
        Method m = hit.getClass().getDeclaredMethod("key");
        m.setAccessible(true);
        return (String) m.invoke(hit);
    }

    @Test
    void scoreDocKeywordHit() {
        int score = (int) ReflectionTestUtils.invokeMethod(service(), "scoreDoc",
                doc(1L, "烧录作业指导书", "烧录,不良", "x"), "烧录时报 BURN_FAIL");
        assertEquals(1, score);
    }

    @Test
    void scoreDocDocNameHitAddsTwo() {
        int score = (int) ReflectionTestUtils.invokeMethod(service(), "scoreDoc",
                doc(1L, "烧录作业指导书", "烧录,不良", "x"), "烧录作业指导书在哪里");
        assertEquals(3, score); // 关键词命中 1 + 文档名命中 2
    }

    @Test
    void scoreDocIsCaseInsensitive() {
        int score = (int) ReflectionTestUtils.invokeMethod(service(), "scoreDoc",
                doc(1L, "故障手册", "burn_fail", "x"), "遇到 BURN_FAIL 怎么办");
        assertEquals(1, score);
    }

    @Test
    void scoreDocNoHit() {
        int score = (int) ReflectionTestUtils.invokeMethod(service(), "scoreDoc",
                doc(1L, "烧录作业指导书", "烧录,不良", "x"), "电视生产日期是什么");
        assertEquals(0, score);
    }

    @Test
    void scoreChunkCountsKeywordHits() {
        KnowledgeServiceImpl service = service();
        int score = (int) ReflectionTestUtils.invokeMethod(service, "scoreChunk", "烧录,不良", "这段讲烧录步骤");
        assertEquals(1, score);
        int empty = (int) ReflectionTestUtils.invokeMethod(service, "scoreChunk", "", "任意文本");
        assertEquals(0, empty);
    }

    @Test
    void recallKeywordOnlyChannel() throws Exception {
        KnowledgeServiceImpl service = service();
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of(
                doc(1L, "烧录作业指导书", "烧录", "## 烧录步骤\n固件写码")));
        when(embeddingClient.embed("烧录步骤")).thenReturn(new float[]{0.1f});
        when(qdrantClient.search(any(), anyInt(), anyDouble())).thenReturn(List.of());

        List<Object> hits = recall(service, "烧录步骤");

        assertEquals(1, hits.size());
        assertEquals("1#0", key(hits.get(0)));
    }

    @Test
    void recallVectorOnlyChannel() throws Exception {
        KnowledgeServiceImpl service = service();
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of()); // 无启用文档 → 关键词通道空
        when(embeddingClient.embed("黑屏")).thenReturn(new float[]{0.1f});
        when(qdrantClient.search(any(), anyInt(), anyDouble())).thenReturn(List.of(
                new QdrantClient.ScoredPoint("p1", 0.9, Map.of(
                        "doc_id", 7L, "section_idx", 0, "doc_name", "黑屏手册", "section_text", "黑屏排查"))));

        List<Object> hits = recall(service, "黑屏");

        assertEquals(1, hits.size());
        assertEquals("7#0", key(hits.get(0)));
    }

    @Test
    void recallMergesSameKeyAcrossChannels() throws Exception {
        KnowledgeServiceImpl service = service();
        // 关键词通道命中两个块（idx0/idx1），向量通道只命中 idx0
        // → 同键合并后剩 2 条，idx0 融合分 = 1/61 + 1/61 排第一
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of(
                doc(1L, "烧录作业指导书", "烧录", "## 烧录准备\n烧录前检查\n\n## 烧录执行\n烧录过程")));
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        when(qdrantClient.search(any(), anyInt(), anyDouble())).thenReturn(List.of(
                new QdrantClient.ScoredPoint("p1", 0.9, Map.of(
                        "doc_id", 1L, "section_idx", 0, "doc_name", "烧录作业指导书", "section_text", "烧录前检查"))));

        List<Object> hits = recall(service, "烧录");

        assertEquals(2, hits.size());
        assertEquals("1#0", key(hits.get(0)));
        assertEquals("1#1", key(hits.get(1)));
    }
}
