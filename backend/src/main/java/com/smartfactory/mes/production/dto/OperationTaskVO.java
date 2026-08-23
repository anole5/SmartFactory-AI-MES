package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.production.entity.MesOperationTask;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序任务出参（工单号/工位/操作员名称由 Service 批量回填，避免 N+1）
 */
@Getter
@Setter
public class OperationTaskVO {

    private Long id;
    private String taskNo;
    private Long workOrderId;
    private String workOrderNo;
    private Long processId;
    private String processCodeSnapshot;
    private String processNameSnapshot;
    private Integer sequenceNo;
    private Long workstationId;
    private String workstationCode;
    private String workstationName;
    private Long operatorId;
    private String operatorName;
    private String equipmentCodeSnapshot;
    private String equipmentNameSnapshot;
    private Integer planQty;
    private Integer completedQty;
    private Integer goodQty;
    private Integer defectQty;
    private String status;
    private Boolean needInspection;
    private BigDecimal standardMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime createdAt;

    public static OperationTaskVO of(MesOperationTask entity) {
        OperationTaskVO vo = new OperationTaskVO();
        vo.setId(entity.getId());
        vo.setTaskNo(entity.getTaskNo());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setProcessId(entity.getProcessId());
        vo.setProcessCodeSnapshot(entity.getProcessCodeSnapshot());
        vo.setProcessNameSnapshot(entity.getProcessNameSnapshot());
        vo.setSequenceNo(entity.getSequenceNo());
        vo.setWorkstationId(entity.getWorkstationId());
        vo.setOperatorId(entity.getOperatorId());
        vo.setEquipmentCodeSnapshot(entity.getEquipmentCodeSnapshot());
        vo.setEquipmentNameSnapshot(entity.getEquipmentNameSnapshot());
        vo.setPlanQty(entity.getPlanQty());
        vo.setCompletedQty(entity.getCompletedQty());
        vo.setGoodQty(entity.getGoodQty());
        vo.setDefectQty(entity.getDefectQty());
        vo.setStatus(entity.getStatus().getCode());
        vo.setNeedInspection(entity.getNeedInspection());
        vo.setStandardMinutes(entity.getStandardMinutes());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setPlanStartTime(entity.getPlanStartTime());
        vo.setPlanEndTime(entity.getPlanEndTime());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
