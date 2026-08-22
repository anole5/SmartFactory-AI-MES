package com.smartfactory.mes.master.entity;

import com.smartfactory.mes.common.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.master.enums.RouteStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 工艺路线头（定义产品生产经过哪些工序）
 */
@Getter
@Setter
@TableName("mes_route")
public class MesRoute extends BaseEntity {

    /** 工艺路线编号（后端生成，RT + 日期时间） */
    private String routeNo;

    /** 产品 ID */
    private Long productId;

    /** 版本号 */
    private String version;

    /** 状态：DRAFT 草稿 / ACTIVE 生效 / OBSOLETE 作废 */
    private RouteStatus status;

    /** 备注 */
    private String remark;
}
