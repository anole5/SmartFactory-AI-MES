package com.smartfactory.mes.ai.mapper;

import com.smartfactory.mes.ai.dto.WeeklyReportRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 周报聚合 Mapper（注解 SQL，显式 deleted = 0 —— 自定义 SQL 不吃 MP 逻辑删除）
 */
@Mapper
public interface WeeklyReportMapper {

    /**
     * 近 14 天逐日报工聚合（good/defect/笔数，按报工 created_at 口径）。
     * 窗口 [start, end) 由服务层传入：start = E-14 00:00，end = E 00:00，
     * 覆盖本周（E-7..E-1）与上周（E-14..E-8）两个自然周窗口。
     */
    @Select("SELECT DATE(created_at) AS report_date, "
            + "COALESCE(SUM(good_qty), 0) AS good_qty, "
            + "COALESCE(SUM(defect_qty), 0) AS defect_qty, "
            + "COUNT(*) AS report_count "
            + "FROM mes_work_report WHERE deleted = 0 "
            + "AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY DATE(created_at) ORDER BY report_date")
    List<WeeklyReportRow> dailyAgg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
