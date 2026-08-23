package com.smartfactory.mes.integration.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.integration.wms.entity.MesStockTransaction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存流水 Mapper
 */
public interface StockTransactionMapper extends BaseMapper<MesStockTransaction> {

    /** 某工单某物料累计领料数量（领料幂等/足额判定用） */
    @Select("SELECT COALESCE(SUM(qty), 0) FROM mes_stock_transaction "
            + "WHERE work_order_id = #{workOrderId} AND item_ref_id = #{itemRefId} "
            + "AND biz_type = 'PICK_OUT' AND deleted = 0")
    int sumPickedQty(@Param("workOrderId") Long workOrderId, @Param("itemRefId") Long itemRefId);
}
