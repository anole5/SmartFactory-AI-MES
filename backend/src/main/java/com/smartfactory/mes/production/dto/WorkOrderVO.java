package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.production.entity.MesWorkOrder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生产工单出参（列表/详情同构；详情在 T6 补任务列表、T8 补报工统计）
 */
@Getter
@Setter
public class WorkOrderVO {

    private Long id;
    private String workOrderNo;
    private String externalOrderNo;
    private Long productId;
    private String productCodeSnapshot;
    private String productNameSnapshot;
    private Long bomId;
    private Long routeId;
    private Integer planQty;
    private Integer completedQty;
    private Integer goodQty;
    private Integer defectQty;
    private String status;
    private String priority;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkOrderVO of(MesWorkOrder entity) {
        WorkOrderVO vo = new WorkOrderVO();
        vo.setId(entity.getId());
        vo.setWorkOrderNo(entity.getWorkOrderNo());
        vo.setExternalOrderNo(entity.getExternalOrderNo());
        vo.setProductId(entity.getProductId());
        vo.setProductCodeSnapshot(entity.getProductCodeSnapshot());
        vo.setProductNameSnapshot(entity.getProductNameSnapshot());
        vo.setBomId(entity.getBomId());
        vo.setRouteId(entity.getRouteId());
        vo.setPlanQty(entity.getPlanQty());
        vo.setCompletedQty(entity.getCompletedQty());
        vo.setGoodQty(entity.getGoodQty());
        vo.setDefectQty(entity.getDefectQty());
        vo.setStatus(entity.getStatus().getCode());
        vo.setPriority(entity.getPriority().getCode());
        vo.setPlanStartTime(entity.getPlanStartTime());
        vo.setPlanEndTime(entity.getPlanEndTime());
        vo.setActualStartTime(entity.getActualStartTime());
        vo.setActualEndTime(entity.getActualEndTime());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
