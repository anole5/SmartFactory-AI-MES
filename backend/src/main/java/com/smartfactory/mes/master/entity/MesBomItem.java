package com.smartfactory.mes.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * BOM 明细（快照字段在保存时从物料主数据回填，物料改名不影响历史 BOM）
 */
@Getter
@Setter
@TableName("mes_bom_item")
public class MesBomItem extends BaseEntity {

    /** BOM 头 ID */
    private Long bomId;

    /** 明细行号（按明细数组顺序 1..n） */
    private Integer lineNo;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码快照 */
    private String materialCodeSnapshot;

    /** 物料名称快照 */
    private String materialNameSnapshot;

    /** 单位快照 */
    private String unitSnapshot;

    /** 单位用量 */
    private BigDecimal requiredQty;

    /** 损耗率（%） */
    private BigDecimal lossRate;

    /** 备注 */
    private String remark;
}
