package com.smartfactory.mes.quality.dto;

import com.smartfactory.mes.quality.entity.MesInspectionTask;
import com.smartfactory.mes.quality.enums.InspectionTaskStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质检任务出参（工单号/质检员名称由 Service 批量回填）
 */
@Getter
@Setter
public class InspectionTaskVO {

    private Long id;
    private String inspectionTaskNo;
    private Long workOrderId;
    private String workOrderNo;
    private Long operationTaskId;
    private String processCodeSnapshot;
    private String processNameSnapshot;
    private Long workstationId;
    private Integer planQty;
    private Integer inspectedQty;
    private Integer goodQty;
    private Integer defectQty;
    private InspectionTaskStatus status;
    private Long inspectorId;
    private String inspectorName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;

    public static InspectionTaskVO of(MesInspectionTask entity) {
        InspectionTaskVO vo = new InspectionTaskVO();
        vo.setId(entity.getId());
        vo.setInspectionTaskNo(entity.getInspectionTaskNo());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setOperationTaskId(entity.getOperationTaskId());
        vo.setProcessCodeSnapshot(entity.getProcessCodeSnapshot());
        vo.setProcessNameSnapshot(entity.getProcessNameSnapshot());
        vo.setWorkstationId(entity.getWorkstationId());
        vo.setPlanQty(entity.getPlanQty());
        vo.setInspectedQty(entity.getInspectedQty());
        vo.setGoodQty(entity.getGoodQty());
        vo.setDefectQty(entity.getDefectQty());
        vo.setStatus(entity.getStatus());
        vo.setInspectorId(entity.getInspectorId());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
