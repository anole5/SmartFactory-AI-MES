package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 不良分布行
 */
@Getter
@Setter
public class DefectCountRow {

    private String defectCode;
    private Long cnt;
}
