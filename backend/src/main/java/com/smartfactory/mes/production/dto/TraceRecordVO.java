package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.production.entity.MesTraceRecord;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 追溯记录出参（操作人名称由 Service 批量回填）
 */
@Getter
@Setter
public class TraceRecordVO {

    private Long id;
    private String traceNo;
    private Long workOrderId;
    private Long taskId;
    private String actionType;
    private LocalDateTime actionTime;
    private Long operatorId;
    private String operatorName;
    private String actionDetail;

    public static TraceRecordVO of(MesTraceRecord entity) {
        TraceRecordVO vo = new TraceRecordVO();
        vo.setId(entity.getId());
        vo.setTraceNo(entity.getTraceNo());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setTaskId(entity.getTaskId());
        vo.setActionType(entity.getActionType().getCode());
        vo.setActionTime(entity.getActionTime());
        vo.setOperatorId(entity.getOperatorId());
        vo.setActionDetail(entity.getActionDetail());
        return vo;
    }
}
