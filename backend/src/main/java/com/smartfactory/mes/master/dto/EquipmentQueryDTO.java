package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.master.enums.EquipmentStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备分页查询入参
 */
@Getter
@Setter
public class EquipmentQueryDTO extends PageQuery {

    /** 编码或名称关键字 */
    private String keyword;

    /** 所属工位过滤 */
    private Long workstationId;

    /** 状态过滤 */
    private EquipmentStatus status;
}
