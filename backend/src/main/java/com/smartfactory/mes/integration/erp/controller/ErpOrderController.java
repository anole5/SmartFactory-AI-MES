package com.smartfactory.mes.integration.erp.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.integration.erp.dto.ErpOrderCreateRequest;
import com.smartfactory.mes.integration.erp.dto.ErpOrderQueryDTO;
import com.smartfactory.mes.integration.erp.dto.ErpOrderVO;
import com.smartfactory.mes.integration.erp.service.ErpOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 外部订单接口（第 5 周：模拟外部 ERP 系统推单 → 一键转工单 → 完工回传）
 */
@RestController
@RequestMapping("/integration/erp/orders")
public class ErpOrderController {

    private final ErpOrderService erpOrderService;

    public ErpOrderController(ErpOrderService erpOrderService) {
        this.erpOrderService = erpOrderService;
    }

    /** 外部订单分页 */
    @RequirePermission("erp:order:query")
    @GetMapping("/page")
    public ApiResult<PageResult<ErpOrderVO>> page(@Valid ErpOrderQueryDTO query) {
        return ApiResult.success(erpOrderService.page(query));
    }

    /** 外部订单详情 */
    @RequirePermission("erp:order:query")
    @GetMapping("/{id}")
    public ApiResult<ErpOrderVO> get(@PathVariable Long id) {
        return ApiResult.success(erpOrderService.getDetail(id));
    }

    /** 模拟下单（模拟外部 ERP 系统推来生产订单） */
    @RequirePermission("erp:order:create")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody ErpOrderCreateRequest request) {
        return ApiResult.success(erpOrderService.create(request));
    }

    /** 一键转工单（PENDING → SYNCED，同事务创建工单并回填） */
    @RequirePermission("erp:order:to-work-order")
    @PutMapping("/{id}/to-work-order")
    public ApiResult<Void> toWorkOrder(@PathVariable Long id) {
        erpOrderService.toWorkOrder(id);
        return ApiResult.success();
    }
}
