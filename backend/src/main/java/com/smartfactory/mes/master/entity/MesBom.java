package com.smartfactory.mes.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.master.enums.BomStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * BOM 头（一个产品可有多个版本；第 1 周只做 V1，版本升级后续周次做）
 */
@Getter
@Setter
@TableName("mes_bom")
public class MesBom extends BaseEntity {

    /** BOM 编号（后端生成，BOM + 日期时间） */
    private String bomNo;

    /** 产品 ID */
    private Long productId;

    /** 版本号 */
    private String version;

    /** 状态：DRAFT 草稿 / ACTIVE 生效 / OBSOLETE 作废 */
    private BomStatus status;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 备注 */
    private String remark;
}
