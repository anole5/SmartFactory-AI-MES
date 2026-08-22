package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesMaterial;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物料出参
 */
@Getter
@Setter
public class MaterialVO {

    private Long id;
    private String materialCode;
    private String materialName;
    private String materialType;
    private String unit;
    private Boolean traceRequired;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    public static MaterialVO of(MesMaterial entity) {
        MaterialVO vo = new MaterialVO();
        vo.setId(entity.getId());
        vo.setMaterialCode(entity.getMaterialCode());
        vo.setMaterialName(entity.getMaterialName());
        vo.setMaterialType(entity.getMaterialType());
        vo.setUnit(entity.getUnit());
        vo.setTraceRequired(entity.getTraceRequired());
        vo.setStatus(entity.getStatus().getCode());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
