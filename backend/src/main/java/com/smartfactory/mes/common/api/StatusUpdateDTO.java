package com.smartfactory.mes.common.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 启停用/状态变更通用入参（status 传枚举 code，如 ENABLED / ACTIVE）
 */
@Getter
@Setter
public class StatusUpdateDTO {

    @NotBlank(message = "状态不能为空")
    private String status;
}
