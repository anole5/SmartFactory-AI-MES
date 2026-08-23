package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 工序良率行（质检任务按工序快照分组汇总）
 */
@Getter
@Setter
public class ProcessYieldRow {

    private String processName;
    private Long good;
    private Long defect;
}
