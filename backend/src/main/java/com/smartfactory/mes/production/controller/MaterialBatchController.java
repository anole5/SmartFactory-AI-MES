package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.MaterialBatchQueryDTO;
import com.smartfactory.mes.production.dto.MaterialBatchSaveDTO;
import com.smartfactory.mes.production.dto.MaterialBatchVO;
import com.smartfactory.mes.production.service.MaterialBatchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物料批次接口（第 6 周）：关键件来料批次台账。
 * 列表复用 production:trace:query（报工弹窗/追溯页全角色可用），创建仅 admin/计划员。
 */
@RestController
@RequestMapping("/production/material-batches")
public class MaterialBatchController {

    private final MaterialBatchService materialBatchService;

    public MaterialBatchController(MaterialBatchService materialBatchService) {
        this.materialBatchService = materialBatchService;
    }

    /** 批次分页列表（报工弹窗按物料拉批次下拉） */
    @RequirePermission("production:trace:query")
    @GetMapping("/page")
    public ApiResult<PageResult<MaterialBatchVO>> page(@Valid MaterialBatchQueryDTO query) {
        return ApiResult.success(materialBatchService.page(query));
    }

    /** 创建批次（批次号 MB+日期+流水由生成器生成） */
    @RequirePermission("production:material-batch:create")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody MaterialBatchSaveDTO dto) {
        return ApiResult.success(materialBatchService.create(dto));
    }
}
