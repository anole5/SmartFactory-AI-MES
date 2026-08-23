package com.smartfactory.mes.integration.erp.dto;

import com.smartfactory.mes.integration.erp.entity.MesExternalOrder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERP 外部订单出参
 */
@Getter
@Setter
public class ErpOrderVO {

    private Long id;
    private String externalOrderNo;
    private Long productId;
    private String productCodeSnapshot;
    private String productNameSnapshot;
    private Integer planQty;
    private String priority;
    private LocalDate planStartTime;
    private LocalDate planEndTime;
    private String status;
    private Long workOrderId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ErpOrderVO of(MesExternalOrder entity) {
        ErpOrderVO vo = new ErpOrderVO();
        vo.setId(entity.getId());
        vo.setExternalOrderNo(entity.getExternalOrderNo());
        vo.setProductId(entity.getProductId());
        vo.setProductCodeSnapshot(entity.getProductCodeSnapshot());
        vo.setProductNameSnapshot(entity.getProductNameSnapshot());
        vo.setPlanQty(entity.getPlanQty());
        vo.setPriority(entity.getPriority());
        vo.setPlanStartTime(entity.getPlanStartTime());
        vo.setPlanEndTime(entity.getPlanEndTime());
        vo.setStatus(entity.getStatus().getCode());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
