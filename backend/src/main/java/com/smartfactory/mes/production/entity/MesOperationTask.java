package com.smartfactory.mes.production.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.production.enums.TaskStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序任务：工单下发时按工艺路线步骤生成（一工单 N 任务，sequence_no 1..n）
 *
 * <p>快照设计：工序编码/名称、设备、标准工时、是否质检全部在下发瞬间固化，
 * 后续主数据变化不影响已下发任务的执行口径。</p>
 */
@Getter
@Setter
@TableName("mes_operation_task")
public class MesOperationTask extends BaseEntity {

    /** 任务号（生成器生成：TASK+日期+4 位流水） */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工序 ID */
    private Long processId;

    /** 工序编码快照 */
    private String processCodeSnapshot;

    /** 工序名称快照 */
    private String processNameSnapshot;

    /** 工序顺序号（1..n，照工艺路线步骤） */
    private Integer sequenceNo;

    /** 工位 ID（默认取路线步骤工位，派工可覆盖） */
    private Long workstationId;

    /** 操作员 ID（sys_user.id，派工时分配） */
    private Long operatorId;

    /** 设备编码快照（来自工位绑定设备） */
    private String equipmentCodeSnapshot;

    /** 设备名称快照 */
    private String equipmentNameSnapshot;

    /** 计划数量（= 工单计划数量） */
    private Integer planQty;

    /** 已报工数量（合格+不良） */
    private Integer completedQty;

    /** 累计合格数量 */
    private Integer goodQty;

    /** 累计不良数量 */
    private Integer defectQty;

    /** 状态：PENDING/ASSIGNED/RUNNING/PAUSED/COMPLETED/CANCELLED */
    private TaskStatus status;

    /** 本工序是否需质检（快照自路线步骤，第 3 周质检任务用） */
    private Boolean needInspection;

    /** 标准工时快照（分钟） */
    private BigDecimal standardMinutes;

    /** 实际开工时间（开工时回填） */
    private LocalDateTime startTime;

    /** 实际完工时间（任务 COMPLETED 时回填） */
    private LocalDateTime endTime;

    /** 计划开始时间（第 6 周排程结果，重跑覆盖即幂等） */
    private LocalDateTime planStartTime;

    /** 计划结束时间（第 6 周排程结果） */
    private LocalDateTime planEndTime;
}
