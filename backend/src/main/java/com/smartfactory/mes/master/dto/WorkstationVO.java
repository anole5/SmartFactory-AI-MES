package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesWorkstation;
import lombok.Getter;
import lombok.Setter;

/**
 * 工位出参
 */
@Getter
@Setter
public class WorkstationVO {

    private Long id;
    private String workstationCode;
    private String workstationName;
    private String equipmentCode;
    private String equipmentName;
    private String status;

    public static WorkstationVO of(MesWorkstation entity) {
        WorkstationVO vo = new WorkstationVO();
        vo.setId(entity.getId());
        vo.setWorkstationCode(entity.getWorkstationCode());
        vo.setWorkstationName(entity.getWorkstationName());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setEquipmentName(entity.getEquipmentName());
        vo.setStatus(entity.getStatus().getCode());
        return vo;
    }
}
