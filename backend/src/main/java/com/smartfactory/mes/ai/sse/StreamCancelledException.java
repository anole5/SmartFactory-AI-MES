package com.smartfactory.mes.ai.sse;

/**
 * SSE 流被客户端主动停止（停止按钮/断连）
 *
 * <p>由 {@link StreamSink#sendDelta} 抛出，沿 DeepSeekClient 流式读取回调向上传播——
 * 客户端读取循环捕获后静默结束，不落问答记录、不打异常日志（正常用户行为不是故障）。</p>
 */
public class StreamCancelledException extends RuntimeException {

    public StreamCancelledException() {
        super("SSE 流被客户端停止");
    }
}
