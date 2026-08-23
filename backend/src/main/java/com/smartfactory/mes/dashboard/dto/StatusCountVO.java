package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 状态计数出参（四状态全量填充，无数据补 0，前端环形图稳定四扇区）
 */
@Getter
@Setter
public class StatusCountVO {

    private String status;
    private Long count;

    public StatusCountVO(String status, Long count) {
        this.status = status;
        this.count = count;
    }
}
