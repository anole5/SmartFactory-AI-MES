package com.smartfactory.mes.master.entity;

import com.smartfactory.mes.common.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.master.enums.ProductStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 产品（基础资料：BOM 与工艺路线都挂在产品下）
 */
@Getter
@Setter
@TableName("mes_product")
public class MesProduct extends BaseEntity {

    /** 产品编码（Service 层唯一校验，DB 不建唯一索引——逻辑删除行物理保留，唯一索引会卡编码复用） */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 产品类型（如：智能电视、显示器） */
    private String productType;

    /** 规格型号 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 状态：启用才能维护 BOM / 工艺路线 */
    private ProductStatus status;
}
