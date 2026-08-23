package com.smartfactory.mes.integration.wms.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.integration.wms.dto.InventoryQueryDTO;
import com.smartfactory.mes.integration.wms.dto.InventoryVO;
import com.smartfactory.mes.integration.wms.dto.PickRequest;
import com.smartfactory.mes.integration.wms.dto.PickResultVO;
import com.smartfactory.mes.integration.wms.dto.StockInRequest;
import com.smartfactory.mes.integration.wms.dto.StockTxQueryDTO;
import com.smartfactory.mes.integration.wms.dto.StockTxVO;

/**
 * WMS 库存服务（第 5 周：采购入库 / 工单领料 / 成品完工入库）
 */
public interface WmsService {

    /** 库存分页（含物料/产品名称回填） */
    PageResult<InventoryVO> inventoryPage(InventoryQueryDTO query);

    /** 库存流水分页（含物料/产品名称回填） */
    PageResult<StockTxVO> txPage(StockTxQueryDTO query);

    /** 采购入库：ON DUPLICATE KEY 累加 + 流水（整单事务） */
    void stockIn(StockInRequest request);

    /**
     * 工单领料：按工单 BOM 关键物料（trace_required=1）领 requiredQty × planQty。
     * 条件 UPDATE 原子扣减，任一物料不足整单回滚；已足额领用 → 409 幂等拒绝。
     */
    PickResultVO pick(PickRequest request);

    /**
     * 开工前校验（生产钩子）：ERP 推单工单须关键物料足额领用，不足抛 409；
     * 手建工单（无外部订单记录）与空 BOM 直接放行。
     */
    void assertPickReady(Long workOrderId);

    /**
     * 完工钩子：合格品成品入库（同工单幂等：已有 FINISHED_IN 流水跳过；qty&lt;=0 跳过）。
     *
     * @param txNo 流水号：完工钩子场景必须由调用方在报工主事务内先取号传入——
     *             本方法以 REQUIRES_NEW 独立事务执行，若在事务内再取号会与主事务
     *             竞争 mes_sequence 行锁（锁等待超时，钩子必失败）；
     *             独立调用（无外层事务）可传 null，内部自行取号
     */
    void finishedIn(Long workOrderId, int goodQty, String txNo);
}
