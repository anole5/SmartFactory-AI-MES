package com.smartfactory.mes.ai.exception;

/**
 * AI 服务调用异常（DeepSeek 网络失败/超时/返回异常）
 *
 * <p>调用方捕获后自动降级模板回答——AI 是增强不是依赖，演示永不白屏。</p>
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
