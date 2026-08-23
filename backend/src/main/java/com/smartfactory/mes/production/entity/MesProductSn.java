package com.smartfactory.mes.production.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 成品 SN（第 3 周：最后一道工序报工完成时按合格数量批量生成，整机唯一标识）
 *
 * <p>sn 由 mes_sequence 批量取号（SN+yyyyMMdd+4 位连续流水），生成即唯一；
 * 产品快照固化出生时产品信息，report_id 指向出生报工记录。</p>
 */
@Getter
@Setter
@TableName("mes_product_sn")
public class MesProductSn extends BaseEntity {

    /** 整机序列号（SN+yyyyMMdd+4 位流水，如 SN202608230001） */
    private String sn;

    /** 工单 ID（出生工单） */
    private Long workOrderId;

    /** 产品 ID */
    private Long productId;

    /** 产品编码快照 */
    private String productCodeSnapshot;

    /** 产品名称快照 */
    private String productNameSnapshot;

    /** 出生报工记录 ID（最后一道工序报工） */
    private Long reportId;

    /** 备注 */
    private String remark;
}
