package com.smartfactory.mes.ai.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 生产日报分页查询入参
 */
@Getter
@Setter
public class DailyReportQueryDTO extends PageQuery {

    /** 报告日期（可空，不传查全部） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate reportDate;
}
