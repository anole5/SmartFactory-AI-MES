package com.smartfactory.mes.dashboard.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.dashboard.dto.DashboardEquipmentVO;
import com.smartfactory.mes.dashboard.dto.DashboardQualityVO;
import com.smartfactory.mes.dashboard.dto.DashboardSummaryVO;
import com.smartfactory.mes.dashboard.dto.DashboardWorkOrderVO;
import com.smartfactory.mes.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产看板接口（第 3 周：只读聚合，10s 轮询数据源）
 */
@RestController
@RequestMapping("/dashboard")
@RequirePermission("production:dashboard:query")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 看板汇总：今日产量/报工数/良率 + 进行中工单 + 未关闭异常 + 设备状态分布 */
    @GetMapping("/summary")
    public ApiResult<DashboardSummaryVO> summary() {
        return ApiResult.success(dashboardService.summary());
    }

    /** 进行中/已下发工单进度 */
    @GetMapping("/work-orders")
    public ApiResult<List<DashboardWorkOrderVO>> workOrders() {
        return ApiResult.success(dashboardService.workOrders());
    }

    /** 质量聚合：整体良率 + 工序良率 + 不良分布 */
    @GetMapping("/quality")
    public ApiResult<DashboardQualityVO> quality() {
        return ApiResult.success(dashboardService.quality());
    }

    /** 设备列表 + 状态分布 */
    @GetMapping("/equipment")
    public ApiResult<DashboardEquipmentVO> equipment() {
        return ApiResult.success(dashboardService.equipment());
    }
}
