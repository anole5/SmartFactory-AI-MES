package com.smartfactory.mes.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.master.enums.MaterialStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 物料（被 BOM 明细引用）
 */
@Getter
@Setter
@TableName("mes_material")
public class MesMaterial extends BaseEntity {

    /** 物料编码（Service 层唯一校验） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 物料类型（核心件/板卡/结构件/线材/音频件/附件/包材） */
    private String materialType;

    /** 单位 */
    private String unit;

    /** 是否批次追溯（电视关键件：面板/主板/电源板为是） */
    private Boolean traceRequired;

    /** 状态 */
    private MaterialStatus status;

    /** 备注 */
    private String remark;
}
