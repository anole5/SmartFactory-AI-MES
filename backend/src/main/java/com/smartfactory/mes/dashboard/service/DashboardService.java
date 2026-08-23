package com.smartfactory.mes.dashboard.service;

import com.smartfactory.mes.dashboard.dto.DashboardEquipmentVO;
import com.smartfactory.mes.dashboard.dto.DashboardQualityVO;
import com.smartfactory.mes.dashboard.dto.DashboardSummaryVO;
import com.smartfactory.mes.dashboard.dto.DashboardWorkOrderVO;

import java.util.List;

/**
 * 看板聚合服务（只读聚合：产量/进度/良率/异常/设备，不写任何表）
 */
public interface DashboardService {

    /** 看板汇总：今日产量/报工数/良率 + 进行中工单 + 未关闭异常 + 设备状态分布 */
    DashboardSummaryVO summary();

    /** 进行中/已下发工单进度列表 */
    List<DashboardWorkOrderVO> workOrders();

    /** 质量聚合：整体良率 + 工序良率 + 不良分布 */
    DashboardQualityVO quality();

    /** 设备列表 + 状态分布 */
    DashboardEquipmentVO equipment();
}
