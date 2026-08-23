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
 * 文本向量化客户端（Text Embeddings Inference /embed 接口，零新依赖）
 *
 * <p>复用本机尚硅谷课程栈 TEI 容器（huggingface/text-embeddings-inference cpu-1.8，
 * 模型 BAAI/bge-large-zh-v1.5：1024 维 Cosine）。服务不可用时抛
 * {@link AiServiceException}，由召回层自动退化关键词通道。</p>
 */
@Component
public class EmbeddingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public EmbeddingClient(ObjectMapper objectMapper,
                           @Value("${ai.embedding.base-url:http://localhost:8081}") String baseUrl,
                           @Value("${ai.embedding.timeout-seconds:30}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(timeoutSeconds * 1000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** 单条文本向量化（调用方保证长度不超 512 token，超长会 422） */
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    /**
     * 批量向量化（TEI 并发上限 batch<=4，调用方按批提交）
     *
     * @return 与入参顺序一致的向量列表，每个 1024 维
     */
    public List<float[]> embed(List<String> texts) {
        try {
            String response = restClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("inputs", texts))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            // 新版 TEI 直接返回数组数组；旧版包一层 {"embeddings": [...]}，两种都兼容
            JsonNode array = root.isArray() ? root : root.path("embeddings");
            if (!array.isArray() || array.size() != texts.size()) {
                throw new AiServiceException("Embedding 返回形状异常: 期望 " + texts.size() + " 条向量");
            }
            List<float[]> vectors = new ArrayList<>(array.size());
            for (JsonNode node : array) {
                float[] vector = new float[node.size()];
                for (int i = 0; i < node.size(); i++) {
                    vector[i] = (float) node.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Embedding 服务调用失败: " + e.getMessage(), e);
        }
    }
}
