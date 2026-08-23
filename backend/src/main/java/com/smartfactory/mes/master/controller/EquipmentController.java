package com.smartfactory.mes.master.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.api.StatusUpdateDTO;
import com.smartfactory.mes.master.dto.EquipmentQueryDTO;
import com.smartfactory.mes.master.dto.EquipmentSaveDTO;
import com.smartfactory.mes.master.dto.EquipmentVO;
import com.smartfactory.mes.master.service.EquipmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备管理接口（第 3 周：独立设备主数据，状态 RUNNING/IDLE/STOPPED/MAINTENANCE）
 */
@RestController
@RequestMapping("/master/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    /** 设备分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<EquipmentVO>> page(@Valid EquipmentQueryDTO query) {
        return ApiResult.success(equipmentService.page(query));
    }

    /** 设备详情 */
    @GetMapping("/{id}")
    public ApiResult<EquipmentVO> get(@PathVariable Long id) {
        return ApiResult.success(equipmentService.getDetail(id));
    }

    /** 创建设备 */
    @RequirePermission("master:equipment:create")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody EquipmentSaveDTO dto) {
        return ApiResult.success(equipmentService.create(dto));
    }

    /** 更新设备 */
    @RequirePermission("master:equipment:update")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody EquipmentSaveDTO dto) {
        equipmentService.update(id, dto);
        return ApiResult.success();
    }

    /** 设备状态切换（RUNNING/IDLE/STOPPED/MAINTENANCE） */
    @RequirePermission("master:equipment:status")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        equipmentService.changeStatus(id, dto.getStatus());
        return ApiResult.success();
    }
}
