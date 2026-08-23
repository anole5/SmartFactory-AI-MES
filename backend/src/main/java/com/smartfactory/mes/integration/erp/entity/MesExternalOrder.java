package com.smartfactory.mes.integration.erp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.integration.erp.enums.ExternalOrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * ERP 外部订单（模拟外部 ERP 系统下发的生产订单）
 *
 * <p>外部订单号由 mes_sequence 生成（ERP 前缀）永不重复；
 * work_order_id 在转工单后回填，工单完工钩子按 external_order_no 回写 DONE。</p>
 */
@Getter
@Setter
@TableName("mes_external_order")
public class MesExternalOrder extends BaseEntity {

    /** 外部订单号（ERP+日期+流水） */
    private String externalOrderNo;

    /** 产品 ID */
    private Long productId;

    /** 产品编码快照 */
    private String productCodeSnapshot;

    /** 产品名称快照 */
    private String productNameSnapshot;

    /** 计划数量 */
    private Integer planQty;

    /** 优先级（透传工单） */
    private String priority;

    /** 计划开始日期（透传工单） */
    private LocalDate planStartTime;

    /** 计划完成日期（透传工单） */
    private LocalDate planEndTime;

    /** 状态：PENDING/SYNCED/DONE */
    private ExternalOrderStatus status;

    /** 转工单后回填的工单 ID */
    private Long workOrderId;

    /** 备注 */
    private String remark;
}
