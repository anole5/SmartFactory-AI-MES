package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 看板设备出参（列表 + 状态分布）
 */
@Getter
@Setter
public class DashboardEquipmentVO {

    private List<DashboardEquipmentRow> equipment;
    private List<StatusCountVO> statusCounts;
}
