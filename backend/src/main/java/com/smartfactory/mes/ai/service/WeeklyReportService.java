package com.smartfactory.mes.ai.service;

import com.smartfactory.mes.ai.dto.WeeklyPreviewVO;

import java.time.LocalDate;

/**
 * AI 周报服务：近两周逐日报工聚合 → 趋势摘要（环比）→ pro 档生成周报正文
 */
public interface WeeklyReportService {

    /** 生成周报预览（不落库）；endDate = 截止日期，本周窗口 E-7..E-1，上周 E-14..E-8 */
    WeeklyPreviewVO preview(LocalDate endDate);

    /** 保存周报（同 (endDate, WEEK) 幂等覆盖） */
    void save(LocalDate endDate, String content);
}
