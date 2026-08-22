package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesRouteStep;
import com.smartfactory.mes.master.entity.MesWorkstation;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工艺步骤出参（快照字段直接给前端展示）
 */
@Getter
@Setter
public class RouteStepVO {

    private Long id;
    private Integer sequenceNo;
    private Long processId;
    private String processCodeSnapshot;
    private String processNameSnapshot;
    private Long workstationId;
    private String workstationCode;
    private String workstationName;
    private Boolean needInspection;
    private BigDecimal standardMinutes;
    private String remark;

    public static RouteStepVO of(MesRouteStep entity) {
        RouteStepVO vo = new RouteStepVO();
        vo.setId(entity.getId());
        vo.setSequenceNo(entity.getSequenceNo());
        vo.setProcessId(entity.getProcessId());
        vo.setProcessCodeSnapshot(entity.getProcessCodeSnapshot());
        vo.setProcessNameSnapshot(entity.getProcessNameSnapshot());
        vo.setWorkstationId(entity.getWorkstationId());
        vo.setNeedInspection(entity.getNeedInspection());
        vo.setStandardMinutes(entity.getStandardMinutes());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    /** 补工位编码/名称（VO 组装时由 Service 查工位主数据填充） */
    public void fillWorkstation(MesWorkstation workstation) {
        if (workstation != null) {
            this.workstationCode = workstation.getWorkstationCode();
            this.workstationName = workstation.getWorkstationName();
        }
    }
}
