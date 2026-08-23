package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 合格/不良合计行（良率计算分子分母）
 */
@Getter
@Setter
public class YieldRow {

    private Long good;
    private Long defect;
}
