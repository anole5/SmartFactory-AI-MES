package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.master.enums.MaterialStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 物料分页查询入参
 */
@Getter
@Setter
public class MaterialQueryDTO extends PageQuery {

    /** 编码或名称关键字 */
    private String keyword;

    /** 物料类型过滤 */
    private String materialType;

    /** 状态过滤 */
    private MaterialStatus status;
}
