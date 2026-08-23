package com.smartfactory.mes.quality.dto;

import com.smartfactory.mes.quality.entity.MesInspectionRecord;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质检记录出参（质检员名称由 Service 批量回填）
 */
@Getter
@Setter
public class InspectionRecordVO {

    private Long id;
    private String inspectionRecordNo;
    private Long inspectionTaskId;
    private Long workOrderId;
    private Long operationTaskId;
    private Integer goodQty;
    private Integer defectQty;
    private LocalDateTime inspectTime;
    private Long inspectorId;
    private String inspectorName;
    private String remark;

    public static InspectionRecordVO of(MesInspectionRecord entity) {
        InspectionRecordVO vo = new InspectionRecordVO();
        vo.setId(entity.getId());
        vo.setInspectionRecordNo(entity.getInspectionRecordNo());
        vo.setInspectionTaskId(entity.getInspectionTaskId());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setOperationTaskId(entity.getOperationTaskId());
        vo.setGoodQty(entity.getGoodQty());
        vo.setDefectQty(entity.getDefectQty());
        vo.setInspectTime(entity.getInspectTime());
        vo.setInspectorId(entity.getInspectorId());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
