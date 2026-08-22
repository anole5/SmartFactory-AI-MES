package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.production.entity.MesWorkReport;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报工记录出参（工单号/任务号/工序名/报工人名称由 Service 批量回填，避免 N+1）
 */
@Getter
@Setter
public class WorkReportVO {

    private Long id;
    private String reportNo;
    private Long workOrderId;
    private String workOrderNo;
    private Long taskId;
    private String taskNo;
    private String processNameSnapshot;
    private Long operatorId;
    private String operatorName;
    private String productBatchNo;
    private Integer reportQty;
    private Integer goodQty;
    private Integer defectQty;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    private LocalDateTime createdAt;

    public static WorkReportVO of(MesWorkReport entity) {
        WorkReportVO vo = new WorkReportVO();
        vo.setId(entity.getId());
        vo.setReportNo(entity.getReportNo());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setTaskId(entity.getTaskId());
        vo.setOperatorId(entity.getOperatorId());
        vo.setProductBatchNo(entity.getProductBatchNo());
        vo.setReportQty(entity.getReportQty());
        vo.setGoodQty(entity.getGoodQty());
        vo.setDefectQty(entity.getDefectQty());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
