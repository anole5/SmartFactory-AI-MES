package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.OperationTaskVO;
import com.smartfactory.mes.production.dto.TaskQueryDTO;
import com.smartfactory.mes.production.service.OperationTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工序任务接口（派工/开工/暂停/继续/报工等写操作在 T7/T8 加入）
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
}
