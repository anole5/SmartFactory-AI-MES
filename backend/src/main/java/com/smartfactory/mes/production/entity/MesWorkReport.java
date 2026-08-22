package com.smartfactory.mes.production.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报工记录：一次报工一条记录，只增不改（审计数据不允许编辑/删除）
 *
 * <p>面试可讲：报工明细与任务累计分离——任务表维护累计值（CAS 累加，读快），
 * 报工表存每次明细（审计追溯用），两者同事务写入保证一致。</p>
 */
@Getter
@Setter
@TableName("mes_work_report")
public class MesWorkReport extends BaseEntity {

    /** 报工单号（生成器生成：RPT+日期+4 位流水） */
    private String reportNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工序任务 ID */
    private Long taskId;

    /** 报工人 ID（当前登录用户，拦截器放入 CurrentUserContext） */
    private Long operatorId;

    /** 生产批次号（第 2 周不做 SN 绑定，第 3 周接） */
    private String productBatchNo;

    /** 报工数量（= 合格 + 不良） */
    private Integer reportQty;

    /** 合格数量 */
    private Integer goodQty;

    /** 不良数量 */
    private Integer defectQty;

    /** 本批次开始时间（缺省当前时间） */
    private LocalDateTime startTime;

    /** 本批次结束时间（缺省当前时间） */
    private LocalDateTime endTime;

    /** 备注 */
    private String remark;
}
