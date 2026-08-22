package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesBom;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BOM 出参（列表不带 items，详情带）
 */
@Getter
@Setter
public class BomVO {

    private Long id;
    private String bomNo;
    private Long productId;
    private String productCode;
    private String productName;
    private String version;
    private String status;
    private LocalDate effectiveDate;
    private String remark;
    private List<BomItemVO> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BomVO of(MesBom entity) {
        BomVO vo = new BomVO();
        vo.setId(entity.getId());
        vo.setBomNo(entity.getBomNo());
        vo.setProductId(entity.getProductId());
        vo.setVersion(entity.getVersion());
        vo.setStatus(entity.getStatus().getCode());
        vo.setEffectiveDate(entity.getEffectiveDate());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
