package com.smartfactory.mes.common.api;

import com.smartfactory.mes.common.exception.BusinessException;

import java.util.Arrays;
import java.util.function.Function;

/**
 * 枚举解析工具：前端传 String code，后端安全转枚举，非法值直接抛业务异常
 */
public final class EnumUtils {

    private EnumUtils() {
    }

    /**
     * 按 code 解析枚举，非法值抛 400 参数错误
     *
     * @param values   枚举全集
     * @param codeOf   取枚举 code 的函数
     * @param code     待解析的 code
     * @param enumName 枚举中文名（用于错误提示）
     */
    public static <E extends Enum<E>> E parse(E[] values, Function<E, String> codeOf, String code, String enumName) {
        return Arrays.stream(values)
                .filter(e -> codeOf.apply(e).equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_ERROR, "非法的" + enumName + "值: " + code));
    }
}
