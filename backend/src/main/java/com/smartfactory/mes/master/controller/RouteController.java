package com.smartfactory.mes.master.controller;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.api.StatusUpdateDTO;
import com.smartfactory.mes.master.dto.RouteQueryDTO;
import com.smartfactory.mes.master.dto.RouteSaveDTO;
import com.smartfactory.mes.master.dto.RouteVO;
import com.smartfactory.mes.master.service.RouteService;
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
 * 工艺路线管理接口（头 + 步骤整单提交，状态机流转）
 */
@RestController
@RequestMapping("/master/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /** 工艺路线分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<RouteVO>> page(@Valid RouteQueryDTO query) {
        return ApiResult.success(routeService.page(query));
    }

    /** 工艺路线详情（含步骤） */
    @GetMapping("/{id}")
    public ApiResult<RouteVO> get(@PathVariable Long id) {
        return ApiResult.success(routeService.getDetail(id));
    }

    /** 创建工艺路线（头 + 步骤） */
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody RouteSaveDTO dto) {
        return ApiResult.success(routeService.create(dto));
    }

    /** 更新工艺路线（仅草稿状态） */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody RouteSaveDTO dto) {
        routeService.update(id, dto);
        return ApiResult.success();
    }

    /** 工艺路线状态流转（DRAFT -> ACTIVE -> OBSOLETE） */
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        routeService.changeStatus(id, dto.getStatus());
        return ApiResult.success();
    }

    /** 删除工艺路线（仅草稿状态，逻辑删除含步骤） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        routeService.delete(id);
        return ApiResult.success();
    }
}
