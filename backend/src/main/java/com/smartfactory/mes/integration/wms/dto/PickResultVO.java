package com.smartfactory.mes.integration.wms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 领料结果出参
 */
@Getter
@Setter
public class PickResultVO {

    private Long workOrderId;
    private String workOrderNo;

    /** 关键物料领料明细（只含本次实际扣减的物料） */
    private List<PickItemVO> items;
}
