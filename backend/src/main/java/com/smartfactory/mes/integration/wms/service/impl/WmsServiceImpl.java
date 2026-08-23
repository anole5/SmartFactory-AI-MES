package com.smartfactory.mes.integration.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.integration.erp.service.ErpOrderService;
import com.smartfactory.mes.integration.wms.dto.InventoryQueryDTO;
import com.smartfactory.mes.integration.wms.dto.InventoryVO;
import com.smartfactory.mes.integration.wms.dto.PickItemVO;
import com.smartfactory.mes.integration.wms.dto.PickRequest;
import com.smartfactory.mes.integration.wms.dto.PickResultVO;
import com.smartfactory.mes.integration.wms.dto.StockInRequest;
import com.smartfactory.mes.integration.wms.dto.StockTxQueryDTO;
import com.smartfactory.mes.integration.wms.dto.StockTxVO;
import com.smartfactory.mes.integration.wms.entity.MesInventory;
import com.smartfactory.mes.integration.wms.entity.MesStockTransaction;
import com.smartfactory.mes.integration.wms.enums.ItemType;
import com.smartfactory.mes.integration.wms.enums.StockBizType;
import com.smartfactory.mes.integration.wms.enums.StockTxType;
import com.smartfactory.mes.integration.wms.mapper.InventoryMapper;
import com.smartfactory.mes.integration.wms.mapper.StockTransactionMapper;
import com.smartfactory.mes.integration.wms.service.WmsService;
import com.smartfactory.mes.master.dto.BomItemVO;
import com.smartfactory.mes.master.dto.BomVO;
import com.smartfactory.mes.master.entity.MesMaterial;
import com.smartfactory.mes.master.entity.MesProduct;
import com.smartfactory.mes.master.service.BomService;
import com.smartfactory.mes.master.service.MaterialService;
import com.smartfactory.mes.master.service.ProductService;
import com.smartfactory.mes.production.dto.WorkOrderVO;
import com.smartfactory.mes.production.service.WorkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * WMS 库存服务实现（第 5 周：与 ERP/生产模块只通过 Service 接口交互，不碰他方 Mapper）
 *
 * <p>并发安全设计：库存累加用 ON DUPLICATE KEY（单行原子），扣减用条件 UPDATE
 * （WHERE qty >= 扣减数），均不依赖「先读后写」，无丢失更新。</p>
 */
@Service
public class WmsServiceImpl implements WmsService {

    private final InventoryMapper inventoryMapper;
    private final StockTransactionMapper stockTransactionMapper;
    private final WorkOrderService workOrderService;
    private final BomService bomService;
    private final MaterialService materialService;
    private final ProductService productService;
    private final ErpOrderService erpOrderService;
    private final OrderNoGenerator orderNoGenerator;

    public WmsServiceImpl(InventoryMapper inventoryMapper,
                          StockTransactionMapper stockTransactionMapper,
                          WorkOrderService workOrderService,
                          BomService bomService,
                          MaterialService materialService,
                          ProductService productService,
                          ErpOrderService erpOrderService,
                          OrderNoGenerator orderNoGenerator) {
        this.inventoryMapper = inventoryMapper;
        this.stockTransactionMapper = stockTransactionMapper;
        this.workOrderService = workOrderService;
        this.bomService = bomService;
        this.materialService = materialService;
        this.productService = productService;
        this.erpOrderService = erpOrderService;
        this.orderNoGenerator = orderNoGenerator;
    }

    @Override
    public PageResult<InventoryVO> inventoryPage(InventoryQueryDTO query) {
        LambdaQueryWrapper<MesInventory> wrapper = new LambdaQueryWrapper<MesInventory>()
                .eq(StringUtils.hasText(query.getItemType()), MesInventory::getItemType, query.getItemType())
                .orderByAsc(MesInventory::getItemType)
                .orderByAsc(MesInventory::getItemRefId);
        // 关键词：先按物料/产品编码名称解析出 ID 集合，再 IN 过滤（名称在别表，联表分页复杂，两步查询更直白）
        if (StringUtils.hasText(query.getKeyword())) {
            List<Long> refIds = new ArrayList<>();
            materialService.list(new LambdaQueryWrapper<MesMaterial>()
                            .like(MesMaterial::getMaterialCode, query.getKeyword())
                            .or().like(MesMaterial::getMaterialName, query.getKeyword()))
                    .forEach(m -> refIds.add(m.getId()));
            productService.list(new LambdaQueryWrapper<MesProduct>()
                            .like(MesProduct::getProductCode, query.getKeyword())
                            .or().like(MesProduct::getProductName, query.getKeyword()))
                    .forEach(p -> refIds.add(p.getId()));
            if (refIds.isEmpty()) {
                return PageResult.of(new Page<>(query.getPageNum(), query.getPageSize()));
            }
            wrapper.in(MesInventory::getItemRefId, refIds);
        }
        Page<MesInventory> page = inventoryMapper.selectPage(
                Page.of(query.getPageNum(), query.getPageSize()), wrapper);
        // 名称回填：物料/产品各查一次，避免逐行 N+1
        Map<Long, MesMaterial> materials = materialMap(page.getRecords(), ItemType.MATERIAL);
        Map<Long, MesProduct> products = productMap(page.getRecords());
        return PageResult.of(page.convert(v -> {
            InventoryVO vo = InventoryVO.of(v);
            if (v.getItemType() == ItemType.MATERIAL) {
                MesMaterial m = materials.get(v.getItemRefId());
                if (m != null) {
                    vo.setItemCode(m.getMaterialCode());
                    vo.setItemName(m.getMaterialName());
                    vo.setUnit(m.getUnit());
                }
            } else {
                MesProduct p = products.get(v.getItemRefId());
                if (p != null) {
                    vo.setItemCode(p.getProductCode());
                    vo.setItemName(p.getProductName());
                    vo.setUnit(p.getUnit());
                }
            }
            return vo;
        }));
    }

    @Override
    public PageResult<StockTxVO> txPage(StockTxQueryDTO query) {
        LambdaQueryWrapper<MesStockTransaction> wrapper = new LambdaQueryWrapper<MesStockTransaction>()
                .eq(query.getWorkOrderId() != null, MesStockTransaction::getWorkOrderId, query.getWorkOrderId())
                .eq(StringUtils.hasText(query.getItemType()), MesStockTransaction::getItemType, query.getItemType())
                .eq(StringUtils.hasText(query.getBizType()), MesStockTransaction::getBizType, query.getBizType())
                .orderByDesc(MesStockTransaction::getId);
        Page<MesStockTransaction> page = stockTransactionMapper.selectPage(
                Page.of(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, MesMaterial> materials = materialMapOfTx(page.getRecords());
        Map<Long, MesProduct> products = productMapOfTx(page.getRecords());
        return PageResult.of(page.convert(tx -> {
            StockTxVO vo = StockTxVO.of(tx);
            if (tx.getItemType() == ItemType.MATERIAL) {
                MesMaterial m = materials.get(tx.getItemRefId());
                if (m != null) {
                    vo.setItemCode(m.getMaterialCode());
                    vo.setItemName(m.getMaterialName());
                }
            } else {
                MesProduct p = products.get(tx.getItemRefId());
                if (p != null) {
                    vo.setItemCode(p.getProductCode());
                    vo.setItemName(p.getProductName());
                }
            }
            return vo;
        }));
    }

    @Override
    @Transactional
    public void stockIn(StockInRequest request) {
        MesMaterial material = materialService.getById(request.getMaterialId());
        if (material == null) {
            throw new BusinessException("物料不存在: id=" + request.getMaterialId());
        }
        inventoryMapper.upsert(ItemType.MATERIAL.getCode(), request.getMaterialId(),
                request.getQty(), StringUtils.hasText(request.getRemark()) ? request.getRemark() : "采购入库");
        insertTx(StockTxType.IN, ItemType.MATERIAL, request.getMaterialId(), request.getQty(),
                StockBizType.PURCHASE_IN, null, request.getRemark());
    }

    @Override
    @Transactional
    public PickResultVO pick(PickRequest request) {
        WorkOrderVO wo = workOrderService.getDetail(request.getWorkOrderId());
        List<KeyMaterialNeed> needs = resolveKeyMaterialNeeds(wo);
        if (needs.isEmpty()) {
            throw new BusinessException("该工单 BOM 无关键物料（trace_required=1），无需领料");
        }
        // 幂等：全部物料已足额领用 → 409（「已足额领用」语义，比「重复领料」更贴近业务）
        List<PickItemVO> items = new ArrayList<>();
        boolean anyPicked = false;
        for (KeyMaterialNeed need : needs) {
            int picked = stockTransactionMapper.sumPickedQty(wo.getId(), need.materialId);
            if (picked >= need.needQty) {
                continue;
            }
            int lack = need.needQty - picked;
            // 条件扣减：影响 0 行 = 库存不足。多物料循环中任一失败抛异常，整单事务回滚
            // （已扣减的物料一并回滚，不留半领状态）
            int updated = inventoryMapper.deduct(ItemType.MATERIAL.getCode(), need.materialId, lack);
            if (updated == 0) {
                throw new BusinessException("关键物料库存不足：" + need.materialCode + " " + need.materialName
                        + "（需补领 " + lack + "）");
            }
            insertTx(StockTxType.OUT, ItemType.MATERIAL, need.materialId, lack,
                    StockBizType.PICK_OUT, wo.getId(), "工单领料 " + wo.getWorkOrderNo());
            items.add(PickItemVO.of(need.materialId, need.materialCode, need.materialName, need.needQty, lack));
            anyPicked = true;
        }
        if (!anyPicked) {
            throw new BusinessException("关键物料已足额领用，无需重复领料");
        }
        PickResultVO result = new PickResultVO();
        result.setWorkOrderId(wo.getId());
        result.setWorkOrderNo(wo.getWorkOrderNo());
        result.setItems(items);
        return result;
    }

    @Override
    public void assertPickReady(Long workOrderId) {
        // 手建工单（无外部订单记录）直接放行——仅 ERP 推单工单要求先领料
        if (!erpOrderService.isExternalWorkOrder(workOrderId)) {
            return;
        }
        WorkOrderVO wo = workOrderService.getDetail(workOrderId);
        List<KeyMaterialNeed> needs = resolveKeyMaterialNeeds(wo);
        if (needs.isEmpty()) {
            return; // 空 BOM 直接通过
        }
        for (KeyMaterialNeed need : needs) {
            if (stockTransactionMapper.sumPickedQty(workOrderId, need.materialId) < need.needQty) {
                throw new BusinessException("ERP 订单工单开工前须完成关键物料领料："
                        + need.materialCode + " " + need.materialName);
            }
        }
    }

    @Override
    // REQUIRES_NEW：完工钩子在报工事务内调用，独立事务提交——钩子失败只回滚自己，
    // 不把报工主事务毒化成 rollback-only（「集成失败不阻断生产」的落点）
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishedIn(Long workOrderId, int goodQty, String txNo) {
        if (goodQty <= 0) {
            return;
        }
        // 幂等：同工单只入一次库（完工钩子重放/补偿调用不会重复累加）
        Long exists = stockTransactionMapper.selectCount(new LambdaQueryWrapper<MesStockTransaction>()
                .eq(MesStockTransaction::getWorkOrderId, workOrderId)
                .eq(MesStockTransaction::getBizType, StockBizType.FINISHED_IN));
        if (exists != null && exists > 0) {
            return;
        }
        WorkOrderVO wo = workOrderService.getDetail(workOrderId);
        String remark = "工单完工入库 " + wo.getWorkOrderNo();
        inventoryMapper.upsert(ItemType.FINISHED.getCode(), wo.getProductId(), goodQty, remark);
        // 流水号优先用调用方传入（完工钩子：主事务内先取号，规避跨事务序列行锁竞争）
        insertTxWithNo(StringUtils.hasText(txNo) ? txNo : orderNoGenerator.next("STK"),
                StockTxType.IN, ItemType.FINISHED, wo.getProductId(), goodQty,
                StockBizType.FINISHED_IN, workOrderId, remark);
    }

    // ------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------

    /** 关键物料需求：BOM 明细中 trace_required=1 的物料 × 工单计划数量（向上取整） */
    private List<KeyMaterialNeed> resolveKeyMaterialNeeds(WorkOrderVO wo) {
        if (wo.getBomId() == null) {
            return Collections.emptyList();
        }
        BomVO bom = bomService.getDetail(wo.getBomId());
        if (bom == null || bom.getItems() == null || bom.getItems().isEmpty()) {
            return Collections.emptyList();
        }
        List<KeyMaterialNeed> needs = new ArrayList<>();
        for (BomItemVO item : bom.getItems()) {
            MesMaterial material = materialService.getById(item.getMaterialId());
            if (material == null || !Boolean.TRUE.equals(material.getTraceRequired())) {
                continue;
            }
            int needQty = item.getRequiredQty()
                    .multiply(BigDecimal.valueOf(wo.getPlanQty()))
                    .setScale(0, RoundingMode.CEILING)
                    .intValue();
            needs.add(new KeyMaterialNeed(material.getId(), material.getMaterialCode(),
                    material.getMaterialName(), needQty));
        }
        return needs;
    }

    private void insertTx(StockTxType txType, ItemType itemType, Long itemRefId, int qty,
                          StockBizType bizType, Long workOrderId, String remark) {
        insertTxWithNo(orderNoGenerator.next("STK"), txType, itemType, itemRefId, qty,
                bizType, workOrderId, remark);
    }

    private void insertTxWithNo(String txNo, StockTxType txType, ItemType itemType, Long itemRefId,
                                int qty, StockBizType bizType, Long workOrderId, String remark) {
        MesStockTransaction tx = new MesStockTransaction();
        tx.setTxNo(txNo);
        tx.setTxType(txType);
        tx.setItemType(itemType);
        tx.setItemRefId(itemRefId);
        tx.setQty(qty);
        tx.setBizType(bizType);
        tx.setWorkOrderId(workOrderId);
        tx.setRemark(remark);
        stockTransactionMapper.insert(tx);
    }

    private Map<Long, MesMaterial> materialMap(List<MesInventory> records, ItemType itemType) {
        Set<Long> ids = records.stream()
                .filter(v -> v.getItemType() == itemType)
                .map(MesInventory::getItemRefId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Collections.emptyMap()
                : materialService.listByIds(ids).stream()
                .collect(Collectors.toMap(MesMaterial::getId, Function.identity()));
    }

    private Map<Long, MesProduct> productMap(List<MesInventory> records) {
        Set<Long> ids = records.stream()
                .filter(v -> v.getItemType() == ItemType.FINISHED)
                .map(MesInventory::getItemRefId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Collections.emptyMap()
                : productService.listByIds(ids).stream()
                .collect(Collectors.toMap(MesProduct::getId, Function.identity()));
    }

    private Map<Long, MesMaterial> materialMapOfTx(List<MesStockTransaction> records) {
        Set<Long> ids = records.stream()
                .filter(tx -> tx.getItemType() == ItemType.MATERIAL)
                .map(MesStockTransaction::getItemRefId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Collections.emptyMap()
                : materialService.listByIds(ids).stream()
                .collect(Collectors.toMap(MesMaterial::getId, Function.identity()));
    }

    private Map<Long, MesProduct> productMapOfTx(List<MesStockTransaction> records) {
        Set<Long> ids = records.stream()
                .filter(tx -> tx.getItemType() == ItemType.FINISHED)
                .map(MesStockTransaction::getItemRefId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Collections.emptyMap()
                : productService.listByIds(ids).stream()
                .collect(Collectors.toMap(MesProduct::getId, Function.identity()));
    }

    /** 关键物料需求内部结构 */
    private static class KeyMaterialNeed {
        final Long materialId;
        final String materialCode;
        final String materialName;
        final int needQty;

        KeyMaterialNeed(Long materialId, String materialCode, String materialName, int needQty) {
            this.materialId = materialId;
            this.materialCode = materialCode;
            this.materialName = materialName;
            this.needQty = needQty;
        }
    }
}
