package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesProduct;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 产品出参（status 用 code 字符串，前端字典负责中文映射）
 */
@Getter
@Setter
public class ProductVO {

    private Long id;
    private String productCode;
    private String productName;
    private String productType;
    private String specification;
    private String unit;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductVO of(MesProduct entity) {
        ProductVO vo = new ProductVO();
        vo.setId(entity.getId());
        vo.setProductCode(entity.getProductCode());
        vo.setProductName(entity.getProductName());
        vo.setProductType(entity.getProductType());
        vo.setSpecification(entity.getSpecification());
        vo.setUnit(entity.getUnit());
        vo.setStatus(entity.getStatus().getCode());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
