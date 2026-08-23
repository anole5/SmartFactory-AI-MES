package com.smartfactory.mes.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.ai.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量库轻客户端（手写 HTTP，零新依赖——与 DeepSeekClient 同款决策）
 *
 * <p>复用本机尚硅谷课程栈 Qdrant 容器（v1.16.3，REST 无鉴权）。自建集合
 * {@code mes-knowledge-sections}（1024 维 Cosine，与 TEI bge-large-zh-v1.5 对齐）；
 * 容器内既有 data-agent-* 集合属尚硅谷课程数据，绝不触碰。
 * 服务不可用时抛 {@link AiServiceException}，由召回层自动退化关键词通道。</p>
 */
@Component
public class QdrantClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String collection;

    public QdrantClient(ObjectMapper objectMapper,
                        @Value("${ai.qdrant.base-url:http://localhost:6333}") String baseUrl,
                        @Value("${ai.qdrant.collection:mes-knowledge-sections}") String collection,
                        @Value("${ai.qdrant.timeout-seconds:15}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.collection = collection;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(timeoutSeconds * 1000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** 确保目标集合存在（1024 维 Cosine）；已存在则跳过 */
    public void ensureCollection() {
        try {
            int status = restClient.get().uri("/collections/{name}", collection)
                    .exchange((request, response) -> response.getStatusCode().value());
            if (status == 200) {
                return;
            }
            restClient.put().uri("/collections/{name}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vectors", Map.of("size", 1024, "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new AiServiceException("Qdrant 集合初始化失败: " + e.getMessage(), e);
        }
    }

    /** 批量写入点（wait=true 保证返回时可检索；内部按 100 分批） */
    public void upsert(List<Point> points) {
        if (points.isEmpty()) {
            return;
        }
        try {
            for (int from = 0; from < points.size(); from += 100) {
                List<Map<String, Object>> batch = new ArrayList<>();
                for (Point p : points.subList(from, Math.min(from + 100, points.size()))) {
                    batch.add(Map.of("id", p.id, "vector", p.vector, "payload", p.payload));
                }
                restClient.put().uri("/collections/{name}/points?wait=true", collection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("points", batch))
                        .retrieve()
                        .toBodilessEntity();
            }
        } catch (Exception e) {
            throw new AiServiceException("Qdrant 写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 向量检索（Cosine 相似度）
     *
     * @param scoreThreshold 低分过滤阈值（0.30：太低的相关度进上下文只会干扰 LLM）
     * @return 按相似度降序的结果（payload 原样返回）
     */
    public List<ScoredPoint> search(float[] vector, int limit, double scoreThreshold) {
        try {
            String response = restClient.post()
                    .uri("/collections/{name}/points/search", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vector", vector, "limit", limit,
                            "score_threshold", scoreThreshold, "with_payload", true))
                    .retrieve()
                    .body(String.class);
            JsonNode result = objectMapper.readTree(response).path("result");
            List<ScoredPoint> points = new ArrayList<>();
            for (JsonNode node : result) {
                points.add(new ScoredPoint(node.path("id").asText(), node.path("score").asDouble(),
                        objectMapper.convertValue(node.path("payload"), Map.class)));
            }
            return points;
        } catch (Exception e) {
            throw new AiServiceException("Qdrant 检索失败: " + e.getMessage(), e);
        }
    }

    /** 按 doc_id 删除文档的全部向量点（文档停用/内容更新重建时先删后写） */
    public void deleteByDocId(long docId) {
        try {
            restClient.post().uri("/collections/{name}/points/delete?wait=true", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("filter", Map.of("must", List.of(
                            Map.of("key", "doc_id", "match", Map.of("value", docId))))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new AiServiceException("Qdrant 删除失败: " + e.getMessage(), e);
        }
    }

    /** 删除整个集合（reindex 全量重建入口；随后调用 ensureCollection 重建）；集合不存在视为成功（幂等） */
    public void deleteCollection() {
        try {
            int status = restClient.delete().uri("/collections/{name}", collection)
                    .exchange((request, response) -> response.getStatusCode().value());
            if (status == 404) {
                return;
            }
            if (status >= 400) {
                throw new AiServiceException("Qdrant 删除集合失败: HTTP " + status);
            }
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Qdrant 删除集合失败: " + e.getMessage(), e);
        }
    }

    /** 集合内点数（reindex 后核对用） */
    public long pointsCount() {
        try {
            String response = restClient.get().uri("/collections/{name}", collection)
                    .retrieve().body(String.class);
            return objectMapper.readTree(response).path("result").path("points_count").asLong();
        } catch (Exception e) {
            throw new AiServiceException("Qdrant 查询集合信息失败: " + e.getMessage(), e);
        }
    }

    /** 待写入的向量点 */
    public static class Point {
        public final String id;          // UUID 字符串（点唯一标识）
        public final float[] vector;     // 1024 维
        public final Map<String, Object> payload;

        public Point(String id, float[] vector, Map<String, Object> payload) {
            this.id = id;
            this.vector = vector;
            this.payload = payload;
        }
    }

    /** 检索命中结果 */
    public static class ScoredPoint {
        public final String id;
        public final double score;
        public final Map<String, Object> payload;

        public ScoredPoint(String id, double score, Map<String, Object> payload) {
            this.id = id;
            this.score = score;
            this.payload = payload;
        }
    }
}
