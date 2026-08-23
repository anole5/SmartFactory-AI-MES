package com.smartfactory.mes.quality.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.quality.dto.DefectQueryDTO;
import com.smartfactory.mes.quality.dto.DefectRecordVO;
import com.smartfactory.mes.quality.service.DefectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 不良记录接口（生成异常单：source=DEFECT，同不良重复生成 409）
 */
@RestController
@RequestMapping("/quality/defects")
public class DefectController {

    private final DefectService defectService;

    public DefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    /** 不良记录分页列表 */
    @RequirePermission("quality:defect:query")
    @GetMapping("/page")
    public ApiResult<PageResult<DefectRecordVO>> page(@Valid DefectQueryDTO query) {
        return ApiResult.success(defectService.page(query));
    }

    /** 不良生成异常单 */
    @RequirePermission("quality:defect:to-exception")
    @PutMapping("/{id}/to-exception")
    public ApiResult<Long> toException(@PathVariable Long id) {
        return ApiResult.success(defectService.toException(id));
    }
}
