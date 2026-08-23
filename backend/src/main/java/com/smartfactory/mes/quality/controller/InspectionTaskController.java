package com.smartfactory.mes.quality.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.quality.dto.InspectionRecordVO;
import com.smartfactory.mes.quality.dto.InspectionTaskQueryDTO;
import com.smartfactory.mes.quality.dto.InspectionTaskVO;
import com.smartfactory.mes.quality.service.InspectionTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 质检任务接口（开始检验/记录查询；检验录入在 InspectionRecordController）
 */
@RestController
@RequestMapping("/quality/inspection-tasks")
public class InspectionTaskController {

    private final InspectionTaskService inspectionTaskService;

    public InspectionTaskController(InspectionTaskService inspectionTaskService) {
        this.inspectionTaskService = inspectionTaskService;
    }

    /** 质检任务分页列表 */
    @RequirePermission("quality:inspection-task:query")
    @GetMapping("/page")
    public ApiResult<PageResult<InspectionTaskVO>> page(@Valid InspectionTaskQueryDTO query) {
        return ApiResult.success(inspectionTaskService.page(query));
    }

    /** 质检任务详情 */
    @RequirePermission("quality:inspection-task:query")
    @GetMapping("/{id}")
    public ApiResult<InspectionTaskVO> get(@PathVariable Long id) {
        return ApiResult.success(inspectionTaskService.getDetail(id));
    }

    /** 某质检任务的质检记录列表 */
    @RequirePermission("quality:inspection-task:query")
    @GetMapping("/{id}/records")
    public ApiResult<List<InspectionRecordVO>> records(@PathVariable Long id) {
        return ApiResult.success(inspectionTaskService.listRecords(id));
    }

    /** 开始检验：PENDING -> INSPECTING（同状态幂等） */
    @RequirePermission("quality:inspection-task:start")
    @PutMapping("/{id}/start")
    public ApiResult<Void> start(@PathVariable Long id) {
        inspectionTaskService.start(id);
        return ApiResult.success();
    }
}
