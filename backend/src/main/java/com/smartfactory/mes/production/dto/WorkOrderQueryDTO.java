package com.smartfactory.mes.production.dto;

import com.smartfactory.mes.common.api.PageQuery;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 生产工单分页查询入参
 */
@Getter
@Setter
public class WorkOrderQueryDTO extends PageQuery {

    /** 关键字：工单号/外部订单号/产品名称快照模糊匹配 */
    private String keyword;

    /** 工单号精确匹配 */
    private String workOrderNo;

    /** 产品 ID 过滤 */
    private Long productId;

    /** 状态过滤（GET 参数按枚举 name 绑定，枚举 code==name） */
    private WorkOrderStatus status;

    /** 计划开始时间范围（对齐 JacksonConfig 全局格式 yyyy-MM-dd HH:mm:ss） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planStartFrom;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTo;
}
