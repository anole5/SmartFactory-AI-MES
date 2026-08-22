package com.smartfactory.mes.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.master.enums.WorkstationStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 工位（含默认设备信息；独立设备表第 2 周再拆）
 */
@Getter
@Setter
@TableName("mes_workstation")
public class MesWorkstation extends BaseEntity {

    /** 工位编码（Service 层唯一校验） */
    private String workstationCode;

    /** 工位名称 */
    private String workstationName;

    /** 绑定设备编码 */
    private String equipmentCode;

    /** 绑定设备名称 */
    private String equipmentName;

    /** 状态 */
    private WorkstationStatus status;
}
