package com.smartfactory.mes.quality.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.quality.dto.DefectQueryDTO;
import com.smartfactory.mes.quality.dto.DefectRecordVO;
import com.smartfactory.mes.quality.service.DefectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 不良记录接口（生成异常单在 T5 加入）
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
}
