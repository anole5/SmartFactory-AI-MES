package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesProcess;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工序出参
 */
@Getter
@Setter
public class ProcessVO {

    private Long id;
    private String processCode;
    private String processName;
    private Boolean needInspection;
    private BigDecimal standardMinutes;
    private String description;

    public static ProcessVO of(MesProcess entity) {
        ProcessVO vo = new ProcessVO();
        vo.setId(entity.getId());
        vo.setProcessCode(entity.getProcessCode());
        vo.setProcessName(entity.getProcessName());
        vo.setNeedInspection(entity.getNeedInspection());
        vo.setStandardMinutes(entity.getStandardMinutes());
        vo.setDescription(entity.getDescription());
        return vo;
    }
}
