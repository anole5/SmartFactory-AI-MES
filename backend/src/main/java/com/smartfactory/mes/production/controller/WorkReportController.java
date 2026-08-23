package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.MaterialBatchBindDTO;
import com.smartfactory.mes.production.dto.WorkReportQueryDTO;
import com.smartfactory.mes.production.dto.WorkReportSaveDTO;
import com.smartfactory.mes.production.dto.WorkReportVO;
import com.smartfactory.mes.production.service.WorkReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报工接口（数量校验/进度回写/追溯记录全在 Service 事务内）
 */
@RestController
@RequestMapping("/production/reports")
public class WorkReportController {

    private final WorkReportService workReportService;

    public WorkReportController(WorkReportService workReportService) {
        this.workReportService = workReportService;
    }

    /** 报工记录分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<WorkReportVO>> page(@Valid WorkReportQueryDTO query) {
        return ApiResult.success(workReportService.page(query));
    }

    /** 报工 */
    @RequirePermission("production:report:create")
    @PostMapping
    public ApiResult<Void> report(@Valid @RequestBody WorkReportSaveDTO dto) {
        workReportService.report(dto);
        return ApiResult.success();
    }

    /** 补录关键件批次绑定（第 6 周：报工后漏绑可补录，校验与报工内嵌绑定共用） */
    @RequirePermission("production:report:create")
    @PostMapping("/{id}/bind-batch")
    public ApiResult<Void> bindBatches(@PathVariable Long id,
                                       @RequestBody List<@Valid MaterialBatchBindDTO> items) {
        workReportService.bindBatches(id, items);
        return ApiResult.success();
    }
}
