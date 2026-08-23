package com.smartfactory.mes.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.ai.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 大模型客户端（OpenAI 兼容 chat/completions 协议，参考尚硅谷掌柜问数接入方式）
 *
 * <p>双档模型路由：flash 快档（deepseek-v4-flash，意图识别/SOP 问答/日报润色）
 * + pro 强档（deepseek-v4-pro，异常原因推理/生产概况综合）。
 * 调用失败统一抛 {@link AiServiceException}，由调用方降级模板回答。</p>
 *
 * <p>接入选择：自研 RestClient 轻客户端而非 Spring AI 全家桶——
 * 与第 2 周 JWT 自研同款决策：学习项目要看清每一步 HTTP 交互。</p>
 */
@Component
public class DeepSeekClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String fastModel;
    private final String proModel;
    private final int maxTokens;

    public DeepSeekClient(ObjectMapper objectMapper,
                          @Value("${ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
                          @Value("${ai.deepseek.api-key:}") String apiKey,
                          @Value("${ai.deepseek.fast-model:deepseek-v4-flash}") String fastModel,
                          @Value("${ai.deepseek.pro-model:deepseek-v4-pro}") String proModel,
                          @Value("${ai.deepseek.timeout-seconds:60}") int timeoutSeconds,
                          @Value("${ai.deepseek.max-tokens:1500}") int maxTokens) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.fastModel = fastModel;
        this.proModel = proModel;
        this.maxTokens = maxTokens;
        // JDK HttpURLConnection 请求工厂：显式连接/读取超时，LLM 推理可能较慢（pro 档数秒到数十秒）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(timeoutSeconds * 1000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** 快档调用（flash）：高频轻任务 */
    public String chatFast(String systemPrompt, String userPrompt) {
        return chat(fastModel, systemPrompt, userPrompt, maxTokens);
    }

    /** 强档调用（pro）：重推理任务 */
    public String chatPro(String systemPrompt, String userPrompt) {
        return chat(proModel, systemPrompt, userPrompt, maxTokens);
    }

    private String chat(String model, String systemPrompt, String userPrompt, int tokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException("DeepSeek API Key 未配置（application-local.yml / DEEPSEEK_API_KEY）");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "max_tokens", tokens,
                    "stream", false);
            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new AiServiceException("DeepSeek 返回内容为空: " + root.path("model").asText());
            }
            return content.asText().trim();
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("DeepSeek 调用失败: " + e.getMessage(), e);
        }
    }
}
