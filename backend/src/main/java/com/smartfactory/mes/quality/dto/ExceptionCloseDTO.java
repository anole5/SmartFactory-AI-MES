package com.smartfactory.mes.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 异常关闭入参（处理结论必填，落 resolve_remark）
 */
@Getter
@Setter
public class ExceptionCloseDTO {

    @NotBlank(message = "处理结论不能为空")
    @Size(max = 255, message = "处理结论最长 255 位")
    private String resolveRemark;
}
