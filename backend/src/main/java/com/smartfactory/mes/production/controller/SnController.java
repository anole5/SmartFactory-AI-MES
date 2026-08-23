package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.SnQueryDTO;
import com.smartfactory.mes.production.dto.SnVO;
import com.smartfactory.mes.production.service.ProductSnService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 整机 SN 接口（最后一道工序报工完成时按合格数量批量生成，按工单分页查询）
 */
@RestController
@RequestMapping("/production/sns")
public class SnController {

    private final ProductSnService productSnService;

    public SnController(ProductSnService productSnService) {
        this.productSnService = productSnService;
    }

    /** 整机 SN 分页列表（工单号/出生报工单号回填） */
    @RequirePermission("production:trace:query")
    @GetMapping("/page")
    public ApiResult<PageResult<SnVO>> page(@Valid SnQueryDTO query) {
        return ApiResult.success(productSnService.page(query));
    }
}
