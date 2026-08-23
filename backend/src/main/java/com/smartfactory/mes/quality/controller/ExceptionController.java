package com.smartfactory.mes.quality.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.quality.dto.ExceptionCloseDTO;
import com.smartfactory.mes.quality.dto.ExceptionOrderVO;
import com.smartfactory.mes.quality.dto.ExceptionQueryDTO;
import com.smartfactory.mes.quality.dto.ExceptionSaveDTO;
import com.smartfactory.mes.quality.service.ExceptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异常单接口（手工创建/处理/关闭；不良生成异常单在 DefectController）
 */
@RestController
@RequestMapping("/quality/exceptions")
public class ExceptionController {

    private final ExceptionService exceptionService;

    public ExceptionController(ExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    /** 异常单分页列表 */
    @RequirePermission("quality:exception:query")
    @GetMapping("/page")
    public ApiResult<PageResult<ExceptionOrderVO>> page(@Valid ExceptionQueryDTO query) {
        return ApiResult.success(exceptionService.page(query));
    }

    /** 手工创建异常单 */
    @RequirePermission("quality:exception:create")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody ExceptionSaveDTO dto) {
        return ApiResult.success(exceptionService.createManual(dto));
    }

    /** 开始处理：OPEN -> PROCESSING */
    @RequirePermission("quality:exception:process")
    @PutMapping("/{id}/process")
    public ApiResult<Void> process(@PathVariable Long id) {
        exceptionService.process(id);
        return ApiResult.success();
    }

    /** 关闭异常：PROCESSING -> CLOSED（处理结论必填） */
    @RequirePermission("quality:exception:close")
    @PutMapping("/{id}/close")
    public ApiResult<Void> close(@PathVariable Long id, @Valid @RequestBody ExceptionCloseDTO dto) {
        exceptionService.close(id, dto);
        return ApiResult.success();
    }
}
