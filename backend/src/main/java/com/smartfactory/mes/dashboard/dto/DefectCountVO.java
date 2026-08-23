package com.smartfactory.mes.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 不良分布出参
 */
@Getter
@Setter
public class DefectCountVO {

    private String defectCode;
    private Long count;
}
