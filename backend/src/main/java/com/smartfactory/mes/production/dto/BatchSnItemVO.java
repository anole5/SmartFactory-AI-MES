package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 批次反查出的整机 SN 摘要（第 6 周）
 */
@Getter
@Setter
public class BatchSnItemVO {

    private Long id;
    private String sn;
    private Long workOrderId;
    private String workOrderNo;
    private String productNameSnapshot;
    private LocalDateTime createdAt;
}
