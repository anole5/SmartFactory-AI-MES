package com.smartfactory.mes.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.master.enums.EquipmentStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备主数据（第 3 周从工位拆分独立设备表）
 *
 * <p>状态 RUNNING/IDLE/STOPPED/MAINTENANCE 非严格状态机，允许任意切换，
 * 由 EquipmentSimulator 定时随机漂移 + 人工切换；看板按状态/工位展示。</p>
 */
@Getter
@Setter
@TableName("mes_equipment")
public class MesEquipment extends BaseEntity {

    /** 设备编码（Service 层唯一校验） */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 设备型号 */
    private String model;

    /** 所属工位 ID（可空，看板按工位展示） */
    private Long workstationId;

    /** 状态：RUNNING/IDLE/STOPPED/MAINTENANCE */
    private EquipmentStatus status;

    /** 备注 */
    private String remark;
}
