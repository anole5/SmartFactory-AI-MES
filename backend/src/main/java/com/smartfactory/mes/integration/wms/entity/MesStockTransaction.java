package com.smartfactory.mes.integration.wms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.integration.wms.enums.ItemType;
import com.smartfactory.mes.integration.wms.enums.StockBizType;
import com.smartfactory.mes.integration.wms.enums.StockTxType;
import lombok.Getter;
import lombok.Setter;

/**
 * WMS 库存流水（第 5 周：每次出入库一条，只增不改，审计数据）
 */
@Getter
@Setter
@TableName("mes_stock_transaction")
public class MesStockTransaction extends BaseEntity {

    /** 流水号（STK+日期+流水，mes_sequence 生成） */
    private String txNo;

    /** 方向：IN 入库 / OUT 出库 */
    private StockTxType txType;

    /** 库存对象类型：MATERIAL/FINISHED */
    private ItemType itemType;

    /** 物料 ID 或产品 ID */
    private Long itemRefId;

    /** 数量（正数） */
    private Integer qty;

    /** 业务类型：PURCHASE_IN/PICK_OUT/FINISHED_IN */
    private StockBizType bizType;

    /** 关联工单 ID（领料/成品入库场景） */
    private Long workOrderId;

    /** 备注 */
    private String remark;
}
