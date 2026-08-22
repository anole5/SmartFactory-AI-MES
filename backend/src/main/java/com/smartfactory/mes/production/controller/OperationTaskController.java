package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.OperationTaskVO;
import com.smartfactory.mes.production.dto.TaskAssignDTO;
import com.smartfactory.mes.production.dto.TaskQueryDTO;
import com.smartfactory.mes.production.service.OperationTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工序任务接口（报工在 T8 加入）
 */
@RestController
@RequestMapping("/production/tasks")
public class OperationTaskController {

    private final OperationTaskService operationTaskService;

    public OperationTaskController(OperationTaskService operationTaskService) {
        this.operationTaskService = operationTaskService;
    }

    /** 任务分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<OperationTaskVO>> page(@Valid TaskQueryDTO query) {
        return ApiResult.success(operationTaskService.page(query));
    }

    /** 某工单的任务列表（工单详情/报工选择用） */
    @GetMapping("/for-work-order/{workOrderId}")
    public ApiResult<List<OperationTaskVO>> listByWorkOrder(@PathVariable Long workOrderId) {
        return ApiResult.success(operationTaskService.listByWorkOrder(workOrderId));
    }

    /** 派工 */
    @RequirePermission("production:task:assign")
    @PutMapping("/{id}/assign")
    public ApiResult<Void> assign(@PathVariable Long id, @Valid @RequestBody TaskAssignDTO dto) {
        operationTaskService.assign(id, dto);
        return ApiResult.success();
    }

    /** 开工 */
    @RequirePermission("production:task:start")
    @PutMapping("/{id}/start")
    public ApiResult<Void> start(@PathVariable Long id) {
        operationTaskService.start(id);
        return ApiResult.success();
    }

    /** 暂停 */
    @RequirePermission("production:task:pause")
    @PutMapping("/{id}/pause")
    public ApiResult<Void> pause(@PathVariable Long id) {
        operationTaskService.pause(id);
        return ApiResult.success();
    }

    /** 继续 */
    @RequirePermission("production:task:resume")
    @PutMapping("/{id}/resume")
    public ApiResult<Void> resume(@PathVariable Long id) {
        operationTaskService.resume(id);
        return ApiResult.success();
    }
}
