package com.smartfactory.mes.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备新增/编辑入参
 */
@Getter
@Setter
public class EquipmentSaveDTO {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码最长 64 位")
    private String equipmentCode;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称最长 128 位")
    private String equipmentName;

    @Size(max = 64, message = "设备型号最长 64 位")
    private String model;

    /** 所属工位 ID（可空） */
    private Long workstationId;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}
