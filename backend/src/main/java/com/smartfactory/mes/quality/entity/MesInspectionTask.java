package com.smartfactory.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.quality.enums.InspectionTaskStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质检任务（第 3 周：需要质检的工序任务报工完成后，由报工事务自动生成）
 *
 * <p>plan_qty = 触发任务报工完成时的累计完成数（送检数量）；
 * 工序编码/名称快照服务端回填，主数据改名不影响历史单据展示。</p>
 */
@Getter
@Setter
@TableName("mes_inspection_task")
public class MesInspectionTask extends BaseEntity {

    /** 质检任务号（生成器生成：INP+日期+流水） */
    private String inspectionTaskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工序任务 ID（触发来源） */
    private Long operationTaskId;

    /** 工序编码快照 */
    private String processCodeSnapshot;

    /** 工序名称快照 */
    private String processNameSnapshot;

    /** 工位 ID（来自工序任务） */
    private Long workstationId;

    /** 送检数量（= 工序任务累计完成数） */
    private Integer planQty;

    /** 已检数量（合格+不良） */
    private Integer inspectedQty;

    /** 检验合格数量 */
    private Integer goodQty;

    /** 检验不良数量 */
    private Integer defectQty;

    /** 状态：PENDING/INSPECTING/COMPLETED/CANCELLED */
    private InspectionTaskStatus status;

    /** 质检员 ID（开始检验时回填） */
    private Long inspectorId;

    /** 开始检验时间 */
    private LocalDateTime startTime;

    /** 检验完成时间（任务 COMPLETED 时回填） */
    private LocalDateTime endTime;

    /** 备注 */
    private String remark;
}
