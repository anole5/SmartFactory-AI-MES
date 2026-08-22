package com.smartfactory.mes.common.exception;

import com.smartfactory.mes.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：业务规则校验不通过时抛出
 *
 * <p>【面试考点】必须继承 {@link RuntimeException}，而不是 Exception。
 * Spring 的 {@code @Transactional} 默认只对 RuntimeException 及其子类回滚；
 * 若继承受检异常（checked exception），保存 BOM 头 + 明细这种整单事务
 * 会在中途抛异常时出现"头写入了、明细没写入"的半提交状态。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(String message) {
        this(ResultCode.CONFLICT, message);
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
