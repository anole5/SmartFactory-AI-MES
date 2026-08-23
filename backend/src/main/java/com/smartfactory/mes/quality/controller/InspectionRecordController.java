package com.smartfactory.mes.quality.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.quality.dto.InspectionRecordSaveDTO;
import com.smartfactory.mes.quality.service.InspectionRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质检记录接口（检验录入：校验链 + CAS 累计 + 不良明细全在 Service 事务内）
 */
@RestController
@RequestMapping("/quality/inspection-records")
public class InspectionRecordController {

    private final InspectionRecordService inspectionRecordService;

    public InspectionRecordController(InspectionRecordService inspectionRecordService) {
        this.inspectionRecordService = inspectionRecordService;
    }

    /** 检验录入 */
    @RequirePermission("quality:inspection-record:create")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody InspectionRecordSaveDTO dto) {
        return ApiResult.success(inspectionRecordService.create(dto));
    }
}
