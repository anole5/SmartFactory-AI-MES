package com.smartfactory.mes.ai.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * {@link StreamSink} 的 SseEmitter 实现（Spring MVC 无 WebFlux，SseEmitter 是 SSE 正解）
 *
 * <p>事件序列化：ObjectMapper 写 JSON 后塞进 SseEmitter.event()；
 * send 抛 IOException（客户端断开）或 IllegalStateException（流已结束）→ 置取消标志静默停止，
 * 后续 delta 抛 {@link StreamCancelledException} 传导给 LLM 读取循环。</p>
 */
public class SseEmitterSink implements StreamSink {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterSink.class);

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private volatile boolean cancelled;

    public SseEmitterSink(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        emitter.onCompletion(() -> cancelled = true);
        emitter.onTimeout(() -> cancelled = true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void sendMeta(Map<String, Object> meta) {
        if (cancelled) {
            return;
        }
        doSend("meta", meta);
    }

    @Override
    public void sendDelta(String content) {
        if (cancelled) {
            throw new StreamCancelledException();
        }
        doSend("delta", Map.of("content", content));
    }

    @Override
    public void sendDone(Map<String, Object> done) {
        if (cancelled) {
            return;
        }
        doSend("done", done);
        emitter.complete();
    }

    @Override
    public void sendError(String message) {
        if (cancelled) {
            return;
        }
        try {
            doSend("error", Map.of("message", message));
        } finally {
            emitter.complete();
        }
    }

    private void doSend(String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            // 客户端断开（停止按钮/关页面）或 JSON 序列化失败（JsonProcessingException 是其子类）：
            // 静默停止，正常用户行为不打异常日志
            cancelled = true;
            log.debug("SSE 发送失败: {}", e.getMessage());
        } catch (IllegalStateException e) {
            cancelled = true;
            log.warn("SSE 发送失败: {}", e.getMessage());
        }
    }
}
