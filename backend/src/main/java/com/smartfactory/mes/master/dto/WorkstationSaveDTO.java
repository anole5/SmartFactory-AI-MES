package com.smartfactory.mes.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 工位新增/编辑入参
 */
@Getter
@Setter
public class WorkstationSaveDTO {

    @NotBlank(message = "工位编码不能为空")
    @Size(max = 64, message = "工位编码最长 64 位")
    private String workstationCode;

    @NotBlank(message = "工位名称不能为空")
    @Size(max = 128, message = "工位名称最长 128 位")
    private String workstationName;

    @Size(max = 64, message = "设备编码最长 64 位")
    private String equipmentCode;

    @Size(max = 128, message = "设备名称最长 128 位")
    private String equipmentName;
}
