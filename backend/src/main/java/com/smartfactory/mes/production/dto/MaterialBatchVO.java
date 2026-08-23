package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.production.entity.MesMaterialBatch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料批次出参（第 6 周）：remainingQty = batchQty - usedQty（下拉展示口径）
 */
@Getter
@Setter
public class MaterialBatchVO {

    private Long id;
    private String batchNo;
    private Long materialId;
    private String materialCodeSnapshot;
    private String materialNameSnapshot;
    private Integer batchQty;
    private Integer usedQty;
    private Integer remainingQty;
    private LocalDate inDate;
    private String supplier;
    private String remark;
    private LocalDateTime createdAt;

    public static MaterialBatchVO of(MesMaterialBatch entity) {
        MaterialBatchVO vo = new MaterialBatchVO();
        vo.setId(entity.getId());
        vo.setBatchNo(entity.getBatchNo());
        vo.setMaterialId(entity.getMaterialId());
        vo.setMaterialCodeSnapshot(entity.getMaterialCodeSnapshot());
        vo.setMaterialNameSnapshot(entity.getMaterialNameSnapshot());
        vo.setBatchQty(entity.getBatchQty());
        vo.setUsedQty(entity.getUsedQty());
        vo.setRemainingQty(entity.getBatchQty() - entity.getUsedQty());
        vo.setInDate(entity.getInDate());
        vo.setSupplier(entity.getSupplier());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
