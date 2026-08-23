package com.smartfactory.mes.integration.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.integration.wms.entity.MesInventory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存 Mapper：累加入库（ON DUPLICATE KEY）与条件扣减（qty >= 扣减数）用原生 SQL，
 * 一条 UPDATE 完成并发防护 + 库存不足校验（影响 0 行 = 库存不足或行不存在）
 */
public interface InventoryMapper extends BaseMapper<MesInventory> {

    /**
     * 累加入库：行不存在则插入，存在则 qty 累加。
     * 唯一键 uk_inventory_item(item_type, item_ref_id) 保证幂等安全，天然无并发覆盖丢更新。
     */
    @Insert("INSERT INTO mes_inventory (item_type, item_ref_id, qty, remark, tenant_id, created_by, created_at, updated_by, updated_at, deleted) "
            + "VALUES (#{itemType}, #{itemRefId}, #{qty}, #{remark}, 1, 0, NOW(), 0, NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE qty = qty + VALUES(qty), updated_at = NOW()")
    int upsert(@Param("itemType") String itemType, @Param("itemRefId") Long itemRefId,
               @Param("qty") int qty, @Param("remark") String remark);

    /**
     * 条件扣减：WHERE qty >= 扣减数 才更新（原子 CAS），影响 0 行 = 库存不足。
     * 自定义 SQL 不走 MP 逻辑删除插件，需手写 deleted = 0。
     */
    @Update("UPDATE mes_inventory SET qty = qty - #{qty}, updated_at = NOW() "
            + "WHERE item_type = #{itemType} AND item_ref_id = #{itemRefId} AND qty >= #{qty} AND deleted = 0")
    int deduct(@Param("itemType") String itemType, @Param("itemRefId") Long itemRefId, @Param("qty") int qty);
}
