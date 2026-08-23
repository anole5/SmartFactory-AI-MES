package com.smartfactory.mes.integration.wms.dto;

import com.smartfactory.mes.integration.wms.entity.MesInventory;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库存出参（itemCode/itemName/unit 由物料或产品主数据批量回填）
 */
@Getter
@Setter
public class InventoryVO {

    private Long id;
    private String itemType;
    private Long itemRefId;
    private String itemCode;
    private String itemName;
    private String unit;
    private Integer qty;
    private String remark;
    private LocalDateTime updatedAt;

    public static InventoryVO of(MesInventory entity) {
        InventoryVO vo = new InventoryVO();
        vo.setId(entity.getId());
        vo.setItemType(entity.getItemType().getCode());
        vo.setItemRefId(entity.getItemRefId());
        vo.setQty(entity.getQty());
        vo.setRemark(entity.getRemark());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
