package com.smartfactory.mes.common.api;

import lombok.Getter;
import org.slf4j.MDC;

/**
 * 统一接口返回结构
 *
 * <pre>{@code
 * {
 *   "code": 0,          // 0 成功；400 参数错误；404 不存在；409 业务冲突；500 系统错误
 *   "message": "success",
 *   "data": {},
 *   "requestId": "req-xxx"  // 每次请求唯一，用于日志串联和线上问题定位
 * }
 * }</pre>
 */
@Getter
public class ApiResult<T> {

    private final int code;
    private final String message;
    private final T data;
    private final String requestId;

    private ApiResult(int code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, currentRequestId());
    }

    public static <T> ApiResult<T> success() {
        return success(null);
    }

    public static <T> ApiResult<T> error(ResultCode resultCode, String message) {
        return new ApiResult<>(resultCode.getCode(), message, null, currentRequestId());
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null, currentRequestId());
    }

    /** 从 MDC 取当前请求 ID（RequestIdFilter 写入），无则返回 null */
    private static String currentRequestId() {
        return MDC.get("requestId");
    }
}
