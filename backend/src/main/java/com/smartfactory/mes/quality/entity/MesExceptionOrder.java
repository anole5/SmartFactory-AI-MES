package com.smartfactory.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.quality.enums.ExceptionSourceType;
import com.smartfactory.mes.quality.enums.ExceptionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 异常单（不良记录可生成异常单，也可手工创建）
 *
 * <p>状态机：OPEN → PROCESSING → CLOSED（显式流转，不可跳转）。</p>
 */
@Getter
@Setter
@TableName("mes_exception_order")
public class MesExceptionOrder extends BaseEntity {

    /** 异常单号（生成器生成：EXP+日期+流水） */
    private String exceptionNo;

    /** 来源：DEFECT 不良生成 / MANUAL 手工创建 */
    private ExceptionSourceType sourceType;

    /** 不良记录 ID（source_type=DEFECT 时关联） */
    private Long defectRecordId;

    /** 工单 ID（可空） */
    private Long workOrderId;

    /** 工序任务 ID（可空） */
    private Long operationTaskId;

    /** 质检任务 ID（可空） */
    private Long inspectionTaskId;

    /** 不良代码（不良生成时快照，手工创建可空） */
    private String defectCode;

    /** 异常描述 */
    private String description;

    /** 状态：OPEN/PROCESSING/CLOSED */
    private ExceptionStatus status;

    /** 处理人 ID（开始处理时回填） */
    private Long handlerId;

    /** 处理结论（关闭时必填） */
    private String resolveRemark;

    /** 关闭时间 */
    private LocalDateTime resolvedAt;
}
