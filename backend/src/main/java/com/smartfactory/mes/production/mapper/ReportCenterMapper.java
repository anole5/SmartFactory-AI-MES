package com.smartfactory.mes.production.mapper;

import com.smartfactory.mes.production.dto.ReportRowVO;
import com.smartfactory.mes.production.dto.ReportTotalsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表中心聚合 Mapper（第 6 周，注解 SQL）
 *
 * <p>【第 3 周坑】自定义 SQL 不经过 MyBatis-Plus wrapper，逻辑删除过滤不会自动附加，
 * 所有 SQL 必须显式写 deleted = 0（与 DashboardMapper 同口径）。</p>
 *
 * <p>聚合口径：created_at >= start AND created_at &lt; end（半开区间，LocalDateTime 绑定，
 * 与 DashboardMapper DATE(created_at)=CURDATE() 的今天口径兼容）。</p>
 */
@Mapper
public interface ReportCenterMapper {

    /** 窗口汇总：合格/不良/报工数/工单数（工单数跨天去重，单独一条 SQL 避免按行求和重复计数） */
    @Select("SELECT COALESCE(SUM(good_qty), 0) AS goodQty, COALESCE(SUM(defect_qty), 0) AS defectQty, " +
            "COUNT(*) AS reportCount, COUNT(DISTINCT work_order_id) AS workOrderCount " +
            "FROM mes_work_report WHERE deleted = 0 AND created_at >= #{start} AND created_at < #{end}")
    ReportTotalsRow totals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 日报明细：按工序分组（join 任务表取工序快照，按工序序排序） */
    @Select("SELECT t.process_name_snapshot AS groupKey, t.process_code_snapshot AS processCode, " +
            "t.process_name_snapshot AS processName, " +
            "SUM(r.good_qty) AS goodQty, SUM(r.defect_qty) AS defectQty, " +
            "COUNT(*) AS reportCount, COUNT(DISTINCT r.work_order_id) AS workOrderCount " +
            "FROM mes_work_report r " +
            "LEFT JOIN mes_operation_task t ON t.id = r.task_id AND t.deleted = 0 " +
            "WHERE r.deleted = 0 AND r.created_at >= #{start} AND r.created_at < #{end} " +
            "GROUP BY t.process_code_snapshot, t.process_name_snapshot " +
            "ORDER BY MIN(t.sequence_no) ASC")
    List<ReportRowVO> sumByProcess(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 周报/月报明细：按日期分组（yyyy-MM-dd 字符串排序即日期序） */
    @Select("SELECT DATE_FORMAT(r.created_at, '%Y-%m-%d') AS groupKey, " +
            "SUM(r.good_qty) AS goodQty, SUM(r.defect_qty) AS defectQty, " +
            "COUNT(*) AS reportCount, COUNT(DISTINCT r.work_order_id) AS workOrderCount " +
            "FROM mes_work_report r " +
            "WHERE r.deleted = 0 AND r.created_at >= #{start} AND r.created_at < #{end} " +
            "GROUP BY DATE_FORMAT(r.created_at, '%Y-%m-%d') " +
            "ORDER BY groupKey ASC")
    List<ReportRowVO> sumByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
