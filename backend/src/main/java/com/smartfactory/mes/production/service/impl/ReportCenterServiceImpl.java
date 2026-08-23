package com.smartfactory.mes.production.service.impl;

import com.smartfactory.mes.common.api.ResultCode;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.production.dto.ReportRowVO;
import com.smartfactory.mes.production.dto.ReportSummaryVO;
import com.smartfactory.mes.production.dto.ReportTotalsRow;
import com.smartfactory.mes.production.mapper.ReportCenterMapper;
import com.smartfactory.mes.production.service.ReportCenterService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 报表中心 Service 实现（第 6 周）：
 * 日/周/月三种粒度的聚合窗口统一为半开区间 [rangeStart, rangeEnd)，
 * 日报按工序分组（join 任务表取工序快照），周报/月报按日期分组。
 */
@Service
public class ReportCenterServiceImpl implements ReportCenterService {

    private final ReportCenterMapper reportCenterMapper;

    public ReportCenterServiceImpl(ReportCenterMapper reportCenterMapper) {
        this.reportCenterMapper = reportCenterMapper;
    }

    @Override
    public ReportSummaryVO summary(String type, LocalDate date) {
        LocalDate base = date == null ? LocalDate.now() : date;
        LocalDateTime start;
        LocalDateTime end;
        switch (type) {
            case "day" -> {
                start = base.atStartOfDay();
                end = base.plusDays(1).atStartOfDay();
            }
            case "week" -> {
                // ISO 周：周一 00:00 起（上一/本周一），下周一结束
                LocalDate monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                start = monday.atStartOfDay();
                end = monday.plusDays(7).atStartOfDay();
            }
            case "month" -> {
                LocalDate first = base.withDayOfMonth(1);
                start = first.atStartOfDay();
                end = first.plusMonths(1).atStartOfDay();
            }
            default -> throw new BusinessException(ResultCode.PARAM_ERROR,
                    "报表类型仅支持 day/week/month: " + type);
        }
        ReportTotalsRow totals = reportCenterMapper.totals(start, end);
        List<ReportRowVO> rows = "day".equals(type)
                ? reportCenterMapper.sumByProcess(start, end)
                : reportCenterMapper.sumByDay(start, end);

        ReportSummaryVO vo = new ReportSummaryVO();
        vo.setType(type);
        vo.setDate(base);
        vo.setRangeStart(start);
        vo.setRangeEnd(end);
        vo.setTotalGoodQty(totals.getGoodQty());
        vo.setTotalDefectQty(totals.getDefectQty());
        vo.setYieldRate(calcYield(totals.getGoodQty(), totals.getDefectQty()));
        vo.setReportCount(totals.getReportCount());
        vo.setWorkOrderCount(totals.getWorkOrderCount());
        vo.setRows(rows);
        return vo;
    }

    /** 良率 = 合格 / (合格+不良) × 100，保留 2 位小数；总产出 0 时为 0 */
    private BigDecimal calcYield(Integer good, Integer defect) {
        int total = good + defect;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(good).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
