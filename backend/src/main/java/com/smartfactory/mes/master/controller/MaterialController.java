package com.smartfactory.mes.master.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.api.StatusUpdateDTO;
import com.smartfactory.mes.master.dto.MaterialQueryDTO;
import com.smartfactory.mes.master.dto.MaterialSaveDTO;
import com.smartfactory.mes.master.dto.MaterialVO;
import com.smartfactory.mes.master.service.MaterialService;
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
 * 物料管理接口
 */
@RestController
@RequestMapping("/master/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /** 物料分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<MaterialVO>> page(@Valid MaterialQueryDTO query) {
        return ApiResult.success(materialService.page(query));
    }

    /** 物料详情 */
    @GetMapping("/{id}")
    public ApiResult<MaterialVO> get(@PathVariable Long id) {
        return ApiResult.success(materialService.getDetail(id));
    }

    /** 创建物料 */
    @RequirePermission("master:material:create")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody MaterialSaveDTO dto) {
        return ApiResult.success(materialService.create(dto));
    }

    /** 更新物料 */
    @RequirePermission("master:material:update")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody MaterialSaveDTO dto) {
        materialService.update(id, dto);
        return ApiResult.success();
    }

    /** 启停用物料 */
    @RequirePermission("master:material:status")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        materialService.changeStatus(id, dto.getStatus());
        return ApiResult.success();
    }

    /** 删除物料（逻辑删除） */
    @RequirePermission("master:material:delete")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return ApiResult.success();
    }
}
