package com.smartfactory.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 不良记录（质检录入不合格时生成，一次检验每种不良码一条）
 */
@Getter
@Setter
@TableName("mes_defect_record")
public class MesDefectRecord extends BaseEntity {

    /** 不良单号（生成器生成：DEF+日期+流水） */
    private String defectNo;

    /** 质检记录 ID（归属） */
    private Long inspectionRecordId;

    /** 质检任务 ID */
    private Long inspectionTaskId;

    /** 工单 ID */
    private Long workOrderId;

    /** 工序任务 ID */
    private Long operationTaskId;

    /** 不良代码：BLACK_SCREEN/FLOWER_SCREEN/NO_SOUND/HDMI_ABNORMAL/BURN_FAIL/AGING_RESTART/ACCESSORY_MISSING */
    private String defectCode;

    /** 不良数量 */
    private Integer defectQty;

    /** 备注 */
    private String remark;
}
