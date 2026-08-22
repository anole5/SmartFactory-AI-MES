package com.smartfactory.mes.master.entity;

import com.smartfactory.mes.common.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工序（工艺字典，被工艺路线步骤引用；无启停用状态）
 */
@Getter
@Setter
@TableName("mes_process")
public class MesProcess extends BaseEntity {

    /** 工序编码（Service 层唯一校验） */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 是否需要质检（0 否 / 1 是） */
    private Boolean needInspection;

    /** 标准工时（分钟） */
    private BigDecimal standardMinutes;

    /** 工序说明 */
    private String description;
}
