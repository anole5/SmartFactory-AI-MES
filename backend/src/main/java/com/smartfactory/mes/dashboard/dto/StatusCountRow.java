package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 状态计数行（设备状态分布 SQL 结果行）
 */
@Getter
@Setter
public class StatusCountRow {

    private String status;
    private Long cnt;
}
