package com.smartfactory.mes.integration.wms.dto;

import com.smartfactory.mes.integration.wms.entity.MesStockTransaction;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库存流水出参（itemCode/itemName 由物料或产品主数据批量回填）
 */
@Getter
@Setter
public class StockTxVO {

    private Long id;
    private String txNo;
    private String txType;
    private String itemType;
    private Long itemRefId;
    private String itemCode;
    private String itemName;
    private Integer qty;
    private String bizType;
    private Long workOrderId;
    private String remark;
    private LocalDateTime createdAt;

    public static StockTxVO of(MesStockTransaction entity) {
        StockTxVO vo = new StockTxVO();
        vo.setId(entity.getId());
        vo.setTxNo(entity.getTxNo());
        vo.setTxType(entity.getTxType().getCode());
        vo.setItemType(entity.getItemType().getCode());
        vo.setItemRefId(entity.getItemRefId());
        vo.setQty(entity.getQty());
        vo.setBizType(entity.getBizType().getCode());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
