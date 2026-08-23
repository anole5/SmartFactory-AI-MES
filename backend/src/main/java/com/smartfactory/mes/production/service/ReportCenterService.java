package com.smartfactory.mes.production.service;

import com.smartfactory.mes.production.dto.ReportSummaryVO;

import java.time.LocalDate;

/**
 * 报表中心服务（第 6 周）：日/周/月三粒度生产报表聚合
 */
public interface ReportCenterService {

    /**
     * 汇总报表：type=day（当天，按工序分组）/ week（ISO 周一为始，按日期分组）/
     * month（自然月，按日期分组）；date 缺省今天；type 非法抛 400
     */
    ReportSummaryVO summary(String type, LocalDate date);
}
