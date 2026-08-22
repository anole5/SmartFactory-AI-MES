package com.smartfactory.mes.master.controller;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.api.StatusUpdateDTO;
import com.smartfactory.mes.master.dto.BomQueryDTO;
import com.smartfactory.mes.master.dto.BomSaveDTO;
import com.smartfactory.mes.master.dto.BomVO;
import com.smartfactory.mes.master.service.BomService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BOM 管理接口（头 + 明细整单提交，状态机流转）
 */
@RestController
@RequestMapping("/master/boms")
public class BomController {

    private final BomService bomService;

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    /** BOM 分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<BomVO>> page(@Valid BomQueryDTO query) {
        return ApiResult.success(bomService.page(query));
    }

    /** BOM 详情（含明细） */
    @GetMapping("/{id}")
    public ApiResult<BomVO> get(@PathVariable Long id) {
        return ApiResult.success(bomService.getDetail(id));
    }

    /** 创建 BOM（头 + 明细） */
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody BomSaveDTO dto) {
        return ApiResult.success(bomService.create(dto));
    }

    /** 更新 BOM（仅草稿状态） */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody BomSaveDTO dto) {
        bomService.update(id, dto);
        return ApiResult.success();
    }

    /** BOM 状态流转（DRAFT -> ACTIVE -> OBSOLETE） */
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        bomService.changeStatus(id, dto.getStatus());
        return ApiResult.success();
    }

    /** 删除 BOM（仅草稿状态，逻辑删除含明细） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        bomService.delete(id);
        return ApiResult.success();
    }
}
