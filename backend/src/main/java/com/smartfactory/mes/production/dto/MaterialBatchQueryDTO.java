package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 物料批次分页查询入参（第 6 周）
 */
@Getter
@Setter
public class MaterialBatchQueryDTO extends PageQuery {

    /** 物料过滤（报工弹窗按关键物料拉批次下拉） */
    private Long materialId;

    /** 批次号/供应商关键字 */
    private String keyword;
}
