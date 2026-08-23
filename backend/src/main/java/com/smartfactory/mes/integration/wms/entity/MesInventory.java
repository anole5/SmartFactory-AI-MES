package com.smartfactory.mes.integration.wms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.integration.wms.enums.ItemType;
import lombok.Getter;
import lombok.Setter;

/**
 * WMS 库存（第 5 周：唯一键 uk_inventory_item(item_type, item_ref_id)，只改数量不删行）
 *
 * <p>item_ref_id 单列双语义：item_type=MATERIAL 时是物料 ID，FINISHED 时是产品 ID——
 * 双可空列方案会踩 MySQL 唯一索引多 NULL 共存陷阱（NULL != NULL，插出重复行），
 * CAS 扣减随之失效。</p>
 */
@Getter
@Setter
@TableName("mes_inventory")
public class MesInventory extends BaseEntity {

    /** 库存对象类型：MATERIAL 物料 / FINISHED 成品 */
    private ItemType itemType;

    /** 物料 ID（MATERIAL）或产品 ID（FINISHED） */
    private Long itemRefId;

    /** 库存数量 */
    private Integer qty;

    /** 备注 */
    private String remark;
}
