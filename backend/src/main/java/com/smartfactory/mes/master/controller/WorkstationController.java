package com.smartfactory.mes.master.controller;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.api.StatusUpdateDTO;
import com.smartfactory.mes.master.dto.WorkstationQueryDTO;
import com.smartfactory.mes.master.dto.WorkstationSaveDTO;
import com.smartfactory.mes.master.dto.WorkstationVO;
import com.smartfactory.mes.master.service.WorkstationService;
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
 * 工位管理接口
 */
@RestController
@RequestMapping("/master/workstations")
public class WorkstationController {

    private final WorkstationService workstationService;

    public WorkstationController(WorkstationService workstationService) {
        this.workstationService = workstationService;
    }

    /** 工位分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<WorkstationVO>> page(@Valid WorkstationQueryDTO query) {
        return ApiResult.success(workstationService.page(query));
    }

    /** 工位详情 */
    @GetMapping("/{id}")
    public ApiResult<WorkstationVO> get(@PathVariable Long id) {
        return ApiResult.success(workstationService.getDetail(id));
    }

    /** 创建工位 */
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody WorkstationSaveDTO dto) {
        return ApiResult.success(workstationService.create(dto));
    }

    /** 更新工位 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody WorkstationSaveDTO dto) {
        workstationService.update(id, dto);
        return ApiResult.success();
    }

    /** 启停用工位 */
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        workstationService.changeStatus(id, dto.getStatus());
        return ApiResult.success();
    }

    /** 删除工位（逻辑删除） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        workstationService.delete(id);
        return ApiResult.success();
    }
}
