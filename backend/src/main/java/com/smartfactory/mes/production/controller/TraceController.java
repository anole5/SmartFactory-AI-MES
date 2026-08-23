package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.production.dto.BatchTraceVO;
import com.smartfactory.mes.production.dto.SnTraceVO;
import com.smartfactory.mes.production.dto.TraceRecordVO;
import com.smartfactory.mes.production.service.TraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产追溯接口（工单详情抽屉的时间线数据源 + SN/批次追溯查询入口）
 */
@RestController
@RequestMapping("/production/traces")
@RequirePermission("production:trace:query")
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

    /** 按整机 SN 追溯：SN 出生信息 + 出生工单摘要 + 全时间线（未知 SN 404） */
    @GetMapping("/sn")
    public ApiResult<SnTraceVO> bySn(@RequestParam String sn) {
        return ApiResult.success(traceService.snTrace(sn));
    }

    /** 按批次号追溯：批次全部报工记录 + 涉及的工单去重列表 */
    @GetMapping("/batch")
    public ApiResult<BatchTraceVO> byBatch(@RequestParam String batchNo) {
        return ApiResult.success(traceService.batchTrace(batchNo));
    }
}
