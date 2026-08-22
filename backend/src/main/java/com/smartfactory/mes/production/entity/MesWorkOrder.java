package com.smartfactory.mes.production.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import com.smartfactory.mes.production.enums.OrderPriority;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生产工单（第 2 周核心主表）
 *
 * <p>快照设计：创建时固化产品编码/名称快照，产品主数据后续改名不影响历史工单展示；
 * bom_id/route_id 在创建时自动解析产品下 ACTIVE 的 BOM/工艺路线（第 1 周基础资料由此进入生产执行）。</p>
 */
@Getter
@Setter
@TableName("mes_work_order")
public class MesWorkOrder extends BaseEntity {

    /** 工单号（生成器生成：WO+日期+4 位流水，如 WO202608230001） */
    private String workOrderNo;

    /** 外部订单号（手填；第 2 周不做 ERP/WMS 集成模拟） */
    private String externalOrderNo;

    /** 产品 ID */
    private Long productId;

    /** 产品编码快照（创建时固化） */
    private String productCodeSnapshot;

    /** 产品名称快照（创建时固化） */
    private String productNameSnapshot;

    /** BOM 头 ID（创建时自动解析产品下生效 BOM） */
    private Long bomId;

    /** 工艺路线 ID（创建时自动解析产品下生效路线） */
    private Long routeId;

    /** 计划数量（台，电视机无小数） */
    private Integer planQty;

    /** 已完成数量（= 最后一道工序累计合格+不良，报工时回写） */
    private Integer completedQty;

    /** 合格数量（= 最后一道工序累计合格） */
    private Integer goodQty;

    /** 不良数量 */
    private Integer defectQty;

    /** 状态：DRAFT/RELEASED/IN_PROGRESS/COMPLETED/CLOSED/CANCELLED */
    private WorkOrderStatus status;

    /** 优先级：HIGH/NORMAL/LOW */
    private OrderPriority priority;

    /** 计划开始时间 */
    private LocalDateTime planStartTime;

    /** 计划结束时间 */
    private LocalDateTime planEndTime;

    /** 实际开工时间（首个任务开工时回填，T7） */
    private LocalDateTime actualStartTime;

    /** 实际完工时间（工单 COMPLETED 时回填，T8） */
    private LocalDateTime actualEndTime;

    /** 备注 */
    private String remark;
}
