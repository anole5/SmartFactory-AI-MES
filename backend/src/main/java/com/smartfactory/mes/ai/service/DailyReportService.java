package com.smartfactory.mes.ai.service;

import com.smartfactory.mes.ai.dto.DailyPreviewVO;
import com.smartfactory.mes.ai.dto.DailyReportQueryDTO;
import com.smartfactory.mes.ai.dto.DailyReportVO;
import com.smartfactory.mes.common.api.PageResult;

import java.time.LocalDate;

/**
 * 生产日报服务：当日数据聚合 → flash 档润色 → 草稿编辑保存（同一日期幂等覆盖）
 */
public interface DailyReportService {

    /** 生成日报预览（不落库） */
    DailyPreviewVO preview(LocalDate reportDate);

    /** 保存日报（同一 report_date 幂等覆盖） */
    void save(LocalDate reportDate, String content);

    /** 日报分页 */
    PageResult<DailyReportVO> page(DailyReportQueryDTO query);
}
