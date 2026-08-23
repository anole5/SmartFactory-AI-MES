package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesEquipment;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 设备出参（工位名称由 Service 批量回填）
 */
@Getter
@Setter
public class EquipmentVO {

    private Long id;
    private String equipmentCode;
    private String equipmentName;
    private String model;
    private Long workstationId;
    private String workstationName;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    public static EquipmentVO of(MesEquipment entity) {
        EquipmentVO vo = new EquipmentVO();
        vo.setId(entity.getId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setEquipmentName(entity.getEquipmentName());
        vo.setModel(entity.getModel());
        vo.setWorkstationId(entity.getWorkstationId());
        vo.setStatus(entity.getStatus().getCode());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
