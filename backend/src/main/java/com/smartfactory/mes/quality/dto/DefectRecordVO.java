package com.smartfactory.mes.quality.dto;

import com.smartfactory.mes.quality.entity.MesDefectRecord;
import lombok.Getter;
import lombok.Setter;

/**
 * 不良记录出参（工单号/工序快照由 Service 批量回填）
 */
@Getter
@Setter
public class DefectRecordVO {

    private Long id;
    private String defectNo;
    private Long inspectionRecordId;
    private Long inspectionTaskId;
    private Long workOrderId;
    private String workOrderNo;
    private Long operationTaskId;
    private String processCodeSnapshot;
    private String processNameSnapshot;
    private String defectCode;
    private Integer defectQty;
    private String remark;

    public static DefectRecordVO of(MesDefectRecord entity) {
        DefectRecordVO vo = new DefectRecordVO();
        vo.setId(entity.getId());
        vo.setDefectNo(entity.getDefectNo());
        vo.setInspectionRecordId(entity.getInspectionRecordId());
        vo.setInspectionTaskId(entity.getInspectionTaskId());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setOperationTaskId(entity.getOperationTaskId());
        vo.setDefectCode(entity.getDefectCode());
        vo.setDefectQty(entity.getDefectQty());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
