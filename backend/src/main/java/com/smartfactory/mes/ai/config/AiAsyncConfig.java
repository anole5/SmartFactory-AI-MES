package com.smartfactory.mes.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 异步线程池配置（第 7 周 SSE 流式）
 *
 * <p>SSE 端点在 aiExecutor 上跑 LLM 流式调用，避免长时间推理占用 Tomcat 请求线程；
 * 队列满时 CallerRunsPolicy 降级为同步执行（拒绝丢请求，演示规模足够）。</p>
 */
@Configuration
public class AiAsyncConfig {

    @Bean(name = "aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("ai-stream-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
