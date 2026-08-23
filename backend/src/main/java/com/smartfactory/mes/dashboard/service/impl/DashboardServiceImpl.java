package com.smartfactory.mes.dashboard.service.impl;

import com.smartfactory.mes.dashboard.dto.DashboardEquipmentRow;
import com.smartfactory.mes.dashboard.dto.DashboardEquipmentVO;
import com.smartfactory.mes.dashboard.dto.DashboardQualityVO;
import com.smartfactory.mes.dashboard.dto.DashboardSummaryVO;
import com.smartfactory.mes.dashboard.dto.DashboardWorkOrderRow;
import com.smartfactory.mes.dashboard.dto.DashboardWorkOrderVO;
import com.smartfactory.mes.dashboard.dto.DefectCountRow;
import com.smartfactory.mes.dashboard.dto.DefectCountVO;
import com.smartfactory.mes.dashboard.dto.ProcessYieldRow;
import com.smartfactory.mes.dashboard.dto.ProcessYieldVO;
import com.smartfactory.mes.dashboard.dto.StatusCountRow;
import com.smartfactory.mes.dashboard.dto.StatusCountVO;
import com.smartfactory.mes.dashboard.dto.YieldRow;
import com.smartfactory.mes.dashboard.mapper.DashboardMapper;
import com.smartfactory.mes.dashboard.service.DashboardService;
import com.smartfactory.mes.master.enums.EquipmentStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 看板聚合服务实现（只读聚合，注解 SQL 见 DashboardMapper，全部显式 deleted = 0）
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    public DashboardServiceImpl(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    @Override
    public DashboardSummaryVO summary() {
        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setTodayOutputQty(dashboardMapper.todayOutputQty());
        vo.setTodayReportCount(dashboardMapper.todayReportCount());
        vo.setTodayDefectQty(dashboardMapper.todayDefectQty());
        YieldRow yield = dashboardMapper.todayYield();
        vo.setTodayYieldRate(rate(yield.getGood(), yield.getDefect()));
        vo.setInProgressWorkOrderCount(dashboardMapper.inProgressWorkOrderCount());
        vo.setOpenExceptionCount(dashboardMapper.openExceptionCount());
        vo.setEquipmentStatusCounts(buildStatusCounts());
        return vo;
    }

    @Override
    public List<DashboardWorkOrderVO> workOrders() {
        return dashboardMapper.activeWorkOrders().stream().map(this::toWorkOrderVO).collect(Collectors.toList());
    }

    @Override
    public DashboardQualityVO quality() {
        DashboardQualityVO vo = new DashboardQualityVO();
        YieldRow yield = dashboardMapper.todayYield();
        vo.setOverallYieldRate(rate(yield.getGood(), yield.getDefect()));
        List<ProcessYieldVO> processYields = dashboardMapper.processYield().stream().map(r -> {
            ProcessYieldVO p = new ProcessYieldVO();
            p.setProcessName(r.getProcessName());
            p.setGoodQty(r.getGood());
            p.setDefectQty(r.getDefect());
            p.setYieldRate(rate(r.getGood(), r.getDefect()));
            return p;
        }).collect(Collectors.toList());
        vo.setProcessYields(processYields);
        List<DefectCountVO> defectDistribution = dashboardMapper.defectDistribution().stream().map(r -> {
            DefectCountVO d = new DefectCountVO();
            d.setDefectCode(r.getDefectCode());
            d.setCount(r.getCnt());
            return d;
        }).collect(Collectors.toList());
        vo.setDefectDistribution(defectDistribution);
        return vo;
    }

    @Override
    public DashboardEquipmentVO equipment() {
        DashboardEquipmentVO vo = new DashboardEquipmentVO();
        List<DashboardEquipmentRow> equipment = dashboardMapper.equipmentList();
        vo.setEquipment(equipment);
        vo.setStatusCounts(buildStatusCounts());
        return vo;
    }

    private DashboardWorkOrderVO toWorkOrderVO(DashboardWorkOrderRow r) {
        DashboardWorkOrderVO vo = new DashboardWorkOrderVO();
        vo.setId(r.getId());
        vo.setWorkOrderNo(r.getWorkOrderNo());
        vo.setProductCodeSnapshot(r.getProductCodeSnapshot());
        vo.setProductNameSnapshot(r.getProductNameSnapshot());
        vo.setPlanQty(r.getPlanQty());
        vo.setCompletedQty(r.getCompletedQty());
        vo.setStatus(r.getStatus());
        // 进度百分比：整数向下取整；计划 0 兜底 0（数据异常时避免除零）
        int pct = r.getPlanQty() != null && r.getPlanQty() > 0
                ? (int) (r.getCompletedQty() * 100.0 / r.getPlanQty()) : 0;
        vo.setProgressPercent(pct);
        return vo;
    }

    /** 设备状态分布：四状态全量填充（无数据状态补 0，前端环形图稳定四扇区） */
    private List<StatusCountVO> buildStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EquipmentStatus s : EquipmentStatus.values()) {
            counts.put(s.getCode(), 0L);
        }
        for (StatusCountRow row : dashboardMapper.equipmentStatusCount()) {
            counts.put(row.getStatus(), row.getCnt());
        }
        return counts.entrySet().stream()
                .map(e -> new StatusCountVO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** 良率 = 合格 / (合格+不良) × 100，保留 1 位小数；无数据返回 null（前端显示 --） */
    private BigDecimal rate(Long good, Long defect) {
        long g = good == null ? 0 : good;
        long d = defect == null ? 0 : defect;
        if (g + d == 0) {
            return null;
        }
        return BigDecimal.valueOf(g).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(g + d), 1, RoundingMode.HALF_UP);
    }
}
