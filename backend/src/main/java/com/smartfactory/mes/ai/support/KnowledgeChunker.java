package com.smartfactory.mes.ai.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识文档规范切块器（第 7 周向量 RAG）
 *
 * <p>索引与召回共用同一套切法：点 id 由 (docId, idx) 确定性推导，
 * 召回时对候选文档重新切块即能与索引点对齐（坑 2：两侧切法不一致会导致 id 对不上）。
 * 切分策略：① 按 {@code ## } 段落切分（与关键词召回原粒度一致）；
 * ② 超长段落硬切 maxChars/40 重叠（防 TEI bge-large-zh-v1.5 512 token 上限 422）。</p>
 */
public final class KnowledgeChunker {

    /** 硬切重叠字数（配合 ai.embedding.max-chunk-chars=400：TEI 512 token ≈ 400 汉字） */
    public static final int OVERLAP_CHARS = 40;

    private KnowledgeChunker() {
    }

    /** 全文切块：段落切分 + 超长硬切，idx 为文档内全局下标（从 0 递增） */
    public static List<Chunk> chunk(long docId, String docName, String docType, String keywords,
                                    String content, int maxChars) {
        List<Chunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        int idx = 0;
        for (String section : content.split("(?=## )")) {
            String text = section.trim();
            if (text.isEmpty()) {
                continue;
            }
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + maxChars, text.length());
                String piece = text.substring(start, end).trim();
                if (!piece.isEmpty()) {
                    chunks.add(new Chunk(idx, docId, docName, docType, keywords, piece));
                    idx++;
                }
                if (end >= text.length()) {
                    break;
                }
                start = end - OVERLAP_CHARS;
            }
        }
        return chunks;
    }

    /** 点 id：UUID.nameUUIDFromBytes("docId#idx")——同文档同下标必得同一点，重复 upsert 天然幂等 */
    public static String pointId(long docId, int idx) {
        return UUID.nameUUIDFromBytes((docId + "#" + idx).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 切块结果：text 为入库原文，payload 已组装（文档元信息 + 段落原文），索引与召回共用 */
    public static class Chunk {
        public final int idx;
        public final String text;
        public final Map<String, Object> payload;

        Chunk(int idx, long docId, String docName, String docType, String keywords, String text) {
            this.idx = idx;
            this.text = text;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("doc_id", docId);
            p.put("doc_name", docName);
            p.put("doc_type", docType == null ? "" : docType);
            p.put("section_idx", idx);
            p.put("section_text", text);
            p.put("keywords", keywords == null ? "" : keywords);
            this.payload = p;
        }
    }
}
