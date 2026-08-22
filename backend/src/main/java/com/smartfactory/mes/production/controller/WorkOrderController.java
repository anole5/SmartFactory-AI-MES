package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.WorkOrderQueryDTO;
import com.smartfactory.mes.production.dto.WorkOrderSaveDTO;
import com.smartfactory.mes.production.dto.WorkOrderVO;
import com.smartfactory.mes.production.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产工单接口（Controller 只做参数接收与返回，业务规则全在 Service）
 */
@RestController
@RequestMapping("/production/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    /** 工单分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<WorkOrderVO>> page(@Valid WorkOrderQueryDTO query) {
        return ApiResult.success(workOrderService.page(query));
    }

    /** 工单详情 */
    @GetMapping("/{id}")
    public ApiResult<WorkOrderVO> get(@PathVariable Long id) {
        return ApiResult.success(workOrderService.getDetail(id));
    }

    /** 创建工单（草稿） */
    @RequirePermission("production:work-order:create")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody WorkOrderSaveDTO dto) {
        return ApiResult.success(workOrderService.create(dto));
    }

    /** 编辑工单（仅草稿） */
    @RequirePermission("production:work-order:update")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody WorkOrderSaveDTO dto) {
        workOrderService.update(id, dto);
        return ApiResult.success();
    }

    /** 取消工单 */
    @RequirePermission("production:work-order:cancel")
    @PutMapping("/{id}/cancel")
    public ApiResult<Void> cancel(@PathVariable Long id) {
        workOrderService.cancel(id);
        return ApiResult.success();
    }

    /** 下发工单：按工艺路线生成工序任务 */
    @RequirePermission("production:work-order:release")
    @PostMapping("/{id}/release")
    public ApiResult<Void> release(@PathVariable Long id) {
        workOrderService.release(id);
        return ApiResult.success();
    }
}
