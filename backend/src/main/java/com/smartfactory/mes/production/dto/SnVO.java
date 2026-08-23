package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.production.entity.MesProductSn;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 整机 SN 出参（工单号/出生报工单号由 Service 批量回填）
 */
@Getter
@Setter
public class SnVO {

    private Long id;
    private String sn;
    private Long workOrderId;
    private String workOrderNo;
    private Long productId;
    private String productCodeSnapshot;
    private String productNameSnapshot;
    private Long reportId;
    private String reportNo;
    private LocalDateTime createdAt;

    public static SnVO of(MesProductSn entity) {
        SnVO vo = new SnVO();
        vo.setId(entity.getId());
        vo.setSn(entity.getSn());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setProductId(entity.getProductId());
        vo.setProductCodeSnapshot(entity.getProductCodeSnapshot());
        vo.setProductNameSnapshot(entity.getProductNameSnapshot());
        vo.setReportId(entity.getReportId());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
