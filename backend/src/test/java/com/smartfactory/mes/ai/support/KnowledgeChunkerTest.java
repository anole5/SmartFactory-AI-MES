package com.smartfactory.mes.ai.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KnowledgeChunker 纯逻辑单测（第 8 周）：
 * 切块器是索引与召回共用的契约层——两侧切法不一致会导致向量点 id 对不上（第 7 周坑 2），
 * 所以段落切分 / 超长硬切重叠 / 点 id 推导 / payload 组装全部钉死。
 */
class KnowledgeChunkerTest {

    @Test
    void nullOrBlankContentReturnsEmpty() {
        assertTrue(KnowledgeChunker.chunk(1L, "d", null, null, null, 400).isEmpty());
        assertTrue(KnowledgeChunker.chunk(1L, "d", null, null, "   ", 400).isEmpty());
    }

    @Test
    void shortTextWithoutSectionHeadingIsSingleTrimmedChunk() {
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.chunk(1L, "d", null, null, "  纯文本内容  ", 400);
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).idx);
        assertEquals("纯文本内容", chunks.get(0).text);
    }

    @Test
    void twoSectionsProduceGlobalIncrementalIdx() {
        String content = "## 步骤一\n先烧录\n\n## 步骤二\n再老化\n";
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.chunk(1L, "d", null, null, content, 400);
        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).idx);
        assertEquals(1, chunks.get(1).idx);
    }

    @Test
    void leadingEmptySectionIsSkipped() {
        // "(?=## )" 切分后第一个元素是空串，必须被跳过
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.chunk(1L, "d", null, null, "## 唯一段\n正文", 400);
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).idx);
    }

    @Test
    void overlongSectionHardCutWithOverlap() {
        // 120 字 / maxChars=50 → 起点 0,10,20,30,40,50,60,70 共 8 块，相邻重叠恰 40 字
        String text = "测".repeat(120);
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.chunk(1L, "d", null, null, text, 50);
        assertEquals(8, chunks.size());
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1).text;
            assertTrue(chunks.get(i).text.startsWith(prev.substring(prev.length() - KnowledgeChunker.OVERLAP_CHARS)),
                    "第 " + i + " 块应与前一块重叠 " + KnowledgeChunker.OVERLAP_CHARS + " 字");
        }
    }

    @Test
    void exactlyMaxCharsIsSingleChunkWithoutOverlap() {
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.chunk(1L, "d", null, null, "测".repeat(50), 50);
        assertEquals(1, chunks.size());
        assertEquals(50, chunks.get(0).text.length());
    }

    @Test
    void pointIdIsDeterministicPerDocAndIdx() {
        String a = KnowledgeChunker.pointId(1L, 0);
        assertEquals(a, KnowledgeChunker.pointId(1L, 0));
        assertNotEquals(a, KnowledgeChunker.pointId(1L, 1));
        assertNotEquals(a, KnowledgeChunker.pointId(2L, 0));
    }

    @Test
    void payloadCarriesFiveMetadataFields() {
        String text = "## 烧录\n固件写码";
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.chunk(5L, "烧录手册", "SOP", "烧录,不良", text, 400);
        Map<String, Object> p = chunks.get(0).payload;
        assertEquals(5L, p.get("doc_id"));
        assertEquals("烧录手册", p.get("doc_name"));
        assertEquals("SOP", p.get("doc_type"));
        assertEquals(0, p.get("section_idx"));
        assertEquals(text, p.get("section_text"));
        assertEquals("烧录,不良", p.get("keywords"));
    }

    @Test
    void nullDocTypeAndKeywordsFallBackToEmptyString() {
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.chunk(1L, "d", null, null, "正文", 400);
        assertEquals("", chunks.get(0).payload.get("doc_type"));
        assertEquals("", chunks.get(0).payload.get("keywords"));
    }
}
