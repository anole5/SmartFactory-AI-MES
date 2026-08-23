package com.smartfactory.mes.integration.wms.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.integration.wms.dto.InventoryQueryDTO;
import com.smartfactory.mes.integration.wms.dto.InventoryVO;
import com.smartfactory.mes.integration.wms.dto.PickRequest;
import com.smartfactory.mes.integration.wms.dto.PickResultVO;
import com.smartfactory.mes.integration.wms.dto.StockInRequest;
import com.smartfactory.mes.integration.wms.dto.StockTxQueryDTO;
import com.smartfactory.mes.integration.wms.dto.StockTxVO;
import com.smartfactory.mes.integration.wms.service.WmsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WMS 库存接口（第 5 周：模拟仓储系统——采购入库 / 工单领料 / 库存流水）
 */
@RestController
@RequestMapping("/integration/wms")
public class WmsController {

    private final WmsService wmsService;

    public WmsController(WmsService wmsService) {
        this.wmsService = wmsService;
    }

    /** 库存分页 */
    @RequirePermission("wms:inventory:query")
    @GetMapping("/inventory/page")
    public ApiResult<PageResult<InventoryVO>> inventoryPage(@Valid InventoryQueryDTO query) {
        return ApiResult.success(wmsService.inventoryPage(query));
    }

    /** 库存流水分页 */
    @RequirePermission("wms:inventory:query")
    @GetMapping("/transactions/page")
    public ApiResult<PageResult<StockTxVO>> txPage(@Valid StockTxQueryDTO query) {
        return ApiResult.success(wmsService.txPage(query));
    }

    /** 采购入库 */
    @RequirePermission("wms:inventory:in")
    @PostMapping("/stock-in")
    public ApiResult<Void> stockIn(@Valid @RequestBody StockInRequest request) {
        wmsService.stockIn(request);
        return ApiResult.success();
    }

    /** 工单领料（按 BOM 关键物料自动领） */
    @RequirePermission("wms:pick")
    @PostMapping("/pick")
    public ApiResult<PickResultVO> pick(@Valid @RequestBody PickRequest request) {
        return ApiResult.success(wmsService.pick(request));
    }
}
