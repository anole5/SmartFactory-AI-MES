package com.smartfactory.mes.master.entity;

import com.smartfactory.mes.common.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工艺路线步骤（sequenceNo 即工艺顺序；快照字段从工序主数据回填）
 */
@Getter
@Setter
@TableName("mes_route_step")
public class MesRouteStep extends BaseEntity {

    /** 工艺路线 ID */
    private Long routeId;

    /** 工序顺序号（1..n） */
    private Integer sequenceNo;

    /** 工序 ID */
    private Long processId;

    /** 工序编码快照 */
    private String processCodeSnapshot;

    /** 工序名称快照 */
    private String processNameSnapshot;

    /** 默认工位 ID（可空） */
    private Long workstationId;

    /** 本步是否质检（0 否 / 1 是） */
    private Boolean needInspection;

    /** 标准工时快照（分钟） */
    private BigDecimal standardMinutes;

    /** 备注 */
    private String remark;
}
