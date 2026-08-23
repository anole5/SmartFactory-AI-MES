package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工序良率出参
 */
@Getter
@Setter
public class ProcessYieldVO {

    private String processName;
    private Long goodQty;
    private Long defectQty;

    /** 良率百分比（保留 1 位小数），无数据为 null */
    private BigDecimal yieldRate;
}
