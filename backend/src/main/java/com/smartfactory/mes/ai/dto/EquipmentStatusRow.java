package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 设备状态分布行（日报聚合 SQL 出参）
 */
@Getter
@Setter
public class EquipmentStatusRow {

    private String status;

    private Long cnt;
}
