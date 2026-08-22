package com.smartfactory.mes.master.controller;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.ProcessQueryDTO;
import com.smartfactory.mes.master.dto.ProcessSaveDTO;
import com.smartfactory.mes.master.dto.ProcessVO;
import com.smartfactory.mes.master.service.ProcessService;
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
 * 工序管理接口（无启停用状态）
 */
@RestController
@RequestMapping("/master/processes")
public class ProcessController {

    private final ProcessService processService;

    public ProcessController(ProcessService processService) {
        this.processService = processService;
    }

    /** 工序分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<ProcessVO>> page(@Valid ProcessQueryDTO query) {
        return ApiResult.success(processService.page(query));
    }

    /** 工序详情 */
    @GetMapping("/{id}")
    public ApiResult<ProcessVO> get(@PathVariable Long id) {
        return ApiResult.success(processService.getDetail(id));
    }

    /** 创建工序 */
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody ProcessSaveDTO dto) {
        return ApiResult.success(processService.create(dto));
    }

    /** 更新工序 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody ProcessSaveDTO dto) {
        processService.update(id, dto);
        return ApiResult.success();
    }

    /** 删除工序（逻辑删除） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        processService.delete(id);
        return ApiResult.success();
    }
}
