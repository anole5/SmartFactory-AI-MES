package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.production.dto.TraceRecordVO;
import com.smartfactory.mes.production.service.TraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产追溯接口（工单详情抽屉的时间线数据源）
 */
@RestController
@RequestMapping("/production/traces")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    /** 某工单的追溯时间线（按动作时间升序） */
    @GetMapping
    public ApiResult<List<TraceRecordVO>> listByWorkOrder(@RequestParam Long workOrderId) {
        return ApiResult.success(traceService.listByWorkOrder(workOrderId));
    }
}
