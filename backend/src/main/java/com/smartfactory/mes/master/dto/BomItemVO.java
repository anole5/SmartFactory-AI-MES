package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesBomItem;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * BOM 明细出参（快照字段直接给前端展示，不依赖物料主数据当前值）
 */
@Getter
@Setter
public class BomItemVO {

    private Long id;
    private Integer lineNo;
    private Long materialId;
    private String materialCodeSnapshot;
    private String materialNameSnapshot;
    private String unitSnapshot;
    private BigDecimal requiredQty;
    private BigDecimal lossRate;
    private String remark;

    public static BomItemVO of(MesBomItem entity) {
        BomItemVO vo = new BomItemVO();
        vo.setId(entity.getId());
        vo.setLineNo(entity.getLineNo());
        vo.setMaterialId(entity.getMaterialId());
        vo.setMaterialCodeSnapshot(entity.getMaterialCodeSnapshot());
        vo.setMaterialNameSnapshot(entity.getMaterialNameSnapshot());
        vo.setUnitSnapshot(entity.getUnitSnapshot());
        vo.setRequiredQty(entity.getRequiredQty());
        vo.setLossRate(entity.getLossRate());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
