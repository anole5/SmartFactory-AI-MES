package com.smartfactory.mes.ai.sse;

import java.util.Map;

/**
 * SSE 流式输出通道抽象（第 7 周）
 *
 * <p>服务层只面向 Sink 写事件，不感知 SseEmitter——事件协议：
 * meta（意图/模板提示，最先发）→ delta（增量文本）×N → done（recordId/intent/answer/引用/fallback）。
 * LLM 故障降级：模板回答整体作为一条 delta 推送（fallback=true），前端打字机体验不变。
 * 客户端停止（停止按钮 abort）→ isCancelled=true，服务层跳过落库静默结束。</p>
 */
public interface StreamSink {

    /** 发送 meta 事件（流首元信息：intent 等） */
    void sendMeta(Map<String, Object> meta);

    /**
     * 发送 delta 事件（增量文本）。已取消时抛 {@link StreamCancelledException}
     * 中止上游 LLM 读取——这是把"停止"从客户端传导到 DeepSeek 流的唯一信号。
     */
    void sendDelta(String content);

    /** 发送 done 事件（终态：完整回答/recordId/引用）并正常结束流 */
    void sendDone(Map<String, Object> done);

    /** 发送 error 事件（异常终态）并结束流 */
    void sendError(String message);

    /** 客户端是否已断开（停止按钮/超时） */
    boolean isCancelled();
}
