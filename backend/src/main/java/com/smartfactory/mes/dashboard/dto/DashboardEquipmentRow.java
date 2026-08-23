package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 设备行（列表 + 工位名称回填）
 */
@Getter
@Setter
public class DashboardEquipmentRow {

    private String equipmentCode;
    private String equipmentName;
    private String status;
    private String workstationName;
}
