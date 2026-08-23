package com.smartfactory.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质检记录（一次检验录入一条，允许同一质检任务分次录入，只增不改）
 */
@Getter
@Setter
@TableName("mes_inspection_record")
public class MesInspectionRecord extends BaseEntity {

    /** 质检记录号（生成器生成：INS+日期+流水） */
    private String inspectionRecordNo;

    /** 质检任务 ID */
    private Long inspectionTaskId;

    /** 工单 ID */
    private Long workOrderId;

    /** 工序任务 ID */
    private Long operationTaskId;

    /** 本次检验合格数量 */
    private Integer goodQty;

    /** 本次检验不良数量 */
    private Integer defectQty;

    /** 检验时间 */
    private LocalDateTime inspectTime;

    /** 质检员 ID（当前登录用户） */
    private Long inspectorId;

    /** 检验说明 */
    private String remark;
}
