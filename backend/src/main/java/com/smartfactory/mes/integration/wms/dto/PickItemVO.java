package com.smartfactory.mes.integration.wms.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 领料明细出参（单条物料的应领/实领数量）
 */
@Getter
@Setter
public class PickItemVO {

    /** 物料 ID */
    private Long materialId;

    /** 物料编码快照 */
    private String materialCode;

    /** 物料名称快照 */
    private String materialName;

    /** 应领数量 = BOM 用量 × 工单计划数量（向上取整） */
    private Integer needQty;

    /** 本次实领数量（已足额部分为 0） */
    private Integer actualPickedQty;

    public static PickItemVO of(Long materialId, String materialCode, String materialName,
                                int needQty, int actualPickedQty) {
        PickItemVO vo = new PickItemVO();
        vo.setMaterialId(materialId);
        vo.setMaterialCode(materialCode);
        vo.setMaterialName(materialName);
        vo.setNeedQty(needQty);
        vo.setActualPickedQty(actualPickedQty);
        return vo;
    }
}
