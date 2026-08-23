package com.smartfactory.mes.quality.dto;

import com.smartfactory.mes.quality.entity.MesExceptionOrder;
import com.smartfactory.mes.quality.enums.ExceptionSourceType;
import com.smartfactory.mes.quality.enums.ExceptionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 异常单出参（工单号/处理人名称/不良单号由 Service 批量回填）
 */
@Getter
@Setter
public class ExceptionOrderVO {

    private Long id;
    private String exceptionNo;
    private ExceptionSourceType sourceType;
    private Long defectRecordId;
    private String defectNo;
    private Long workOrderId;
    private String workOrderNo;
    private Long operationTaskId;
    private Long inspectionTaskId;
    private String defectCode;
    private String description;
    private ExceptionStatus status;
    private Long handlerId;
    private String handlerName;
    private String resolveRemark;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static ExceptionOrderVO of(MesExceptionOrder entity) {
        ExceptionOrderVO vo = new ExceptionOrderVO();
        vo.setId(entity.getId());
        vo.setExceptionNo(entity.getExceptionNo());
        vo.setSourceType(entity.getSourceType());
        vo.setDefectRecordId(entity.getDefectRecordId());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setOperationTaskId(entity.getOperationTaskId());
        vo.setInspectionTaskId(entity.getInspectionTaskId());
        vo.setDefectCode(entity.getDefectCode());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setHandlerId(entity.getHandlerId());
        vo.setResolveRemark(entity.getResolveRemark());
        vo.setResolvedAt(entity.getResolvedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
