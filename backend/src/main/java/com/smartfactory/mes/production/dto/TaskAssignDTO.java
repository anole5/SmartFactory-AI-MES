package com.smartfactory.mes.production.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 工序任务派工入参
 */
@Getter
@Setter
public class TaskAssignDTO {

    @NotNull(message = "操作员不能为空")
    private Long operatorId;

    /** 工位 ID（可空：不传沿用下发时的默认工位；传了校验启用后覆盖，并同步刷新设备快照） */
    private Long workstationId;
}
