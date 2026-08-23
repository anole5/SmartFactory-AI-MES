package com.smartfactory.mes.ai.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

/**
 * SSE 流式端点统一装配（第 7 周）
 *
 * <p>控制器一行接入：{@code SseSupport.start(aiExecutor, objectMapper, sink -> service.xxxStream(...))}。
 * 要点：
 * ① SseEmitter(120s) 超时窗口覆盖慢推理（pro 档数秒到数十秒）；
 * ② 任务丢 aiExecutor 异步执行，不占 Tomcat 请求线程；
 * ③ 在发起线程捕获 LoginUser，worker 线程 try{set}finally{clear}——AuditMetaObjectHandler
 *    在 INSERT 时读 CurrentUserContext 填 created_by，异步线程不恢复会记成 0（审计字段错账）。</p>
 */
public final class SseSupport {

    private static final Logger log = LoggerFactory.getLogger(SseSupport.class);

    /** SSE 会话超时（客户端 120s 内无事件即断开；LLM 流式逐 token 推送不会触发） */
    public static final long TIMEOUT_MS = 120_000;

    private SseSupport() {
    }

    /** 流式任务：在 worker 线程执行，接收 Sink 写事件 */
    public interface StreamTask {
        void run(StreamSink sink);
    }

    public static SseEmitter start(Executor executor, ObjectMapper objectMapper, StreamTask task) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        StreamSink sink = new SseEmitterSink(emitter, objectMapper);
        LoginUser user = CurrentUserContext.get();
        executor.execute(() -> {
            try {
                CurrentUserContext.set(user);
                task.run(sink);
            } catch (StreamCancelledException e) {
                log.debug("SSE 流式任务被客户端停止");
            } catch (Exception e) {
                log.error("SSE 流式任务异常", e);
                sink.sendError(e.getMessage() == null ? "AI 服务异常" : e.getMessage());
            } finally {
                CurrentUserContext.clear();
            }
        });
        return emitter;
    }
}
