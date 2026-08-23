package com.smartfactory.mes.production.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 物料批次（第 6 周）：关键件 trace_required=1 的来料批次台账。
 * 批次号 MB+日期+流水由 mes_sequence 生成、永不重复；报工绑定时校验存在性 + 物料匹配。
 */
@Getter
@Setter
@TableName("mes_material_batch")
public class MesMaterialBatch extends BaseEntity {

    /** 批次号（生成器生成：MB+日期+4 位流水） */
    private String batchNo;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码快照 */
    private String materialCodeSnapshot;

    /** 物料名称快照 */
    private String materialNameSnapshot;

    /** 批次入库数量 */
    private Integer batchQty;

    /** 已绑定消耗数量（绑定时按台数累加，展示口径） */
    private Integer usedQty;

    /** 入库日期 */
    private LocalDate inDate;

    /** 供应商 */
    private String supplier;

    /** 备注 */
    private String remark;
}
