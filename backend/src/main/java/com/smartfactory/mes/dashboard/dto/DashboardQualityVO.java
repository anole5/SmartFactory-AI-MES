package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 看板质量出参（整体良率 + 工序良率 + 不良分布）
 */
@Getter
@Setter
public class DashboardQualityVO {

    private BigDecimal overallYieldRate;
    private List<ProcessYieldVO> processYields;
    private List<DefectCountVO> defectDistribution;
}
