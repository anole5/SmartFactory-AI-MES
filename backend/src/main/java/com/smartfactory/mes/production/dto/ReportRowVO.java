package com.smartfactory.mes.production.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 报表中心明细行（第 6 周）：日报按工序分组（groupKey=工序名），
 * 周报/月报按日期分组（groupKey=yyyy-MM-dd，工序列为空）
 */
@Getter
@Setter
public class ReportRowVO {

    /** 分组键：日报=工序名称，周报/月报=日期字符串 */
    private String groupKey;

    /** 工序编码（仅日报） */
    private String processCode;

    /** 工序名称（仅日报） */
    private String processName;

    private Integer goodQty;
    private Integer defectQty;
    private Integer reportCount;
    private Integer workOrderCount;
}
