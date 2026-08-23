package com.smartfactory.mes.ai.mapper;

import com.smartfactory.mes.ai.dto.EquipmentStatusRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产日报聚合 Mapper（注解 SQL，显式 deleted = 0 —— 自定义 SQL 不吃 MP 逻辑删除）
 */
@Mapper
public interface DailyReportMapper {

    /** 指定日期区间报工合格数 */
    @Select("SELECT COALESCE(SUM(good_qty), 0) FROM mes_work_report WHERE deleted = 0 "
            + "AND created_at >= #{start} AND created_at < #{end}")
    Long sumGood(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 指定日期区间报工不良数 */
    @Select("SELECT COALESCE(SUM(defect_qty), 0) FROM mes_work_report WHERE deleted = 0 "
            + "AND created_at >= #{start} AND created_at < #{end}")
    Long sumDefect(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 指定日期区间报工笔数 */
    @Select("SELECT COUNT(*) FROM mes_work_report WHERE deleted = 0 "
            + "AND created_at >= #{start} AND created_at < #{end}")
    Long countReport(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 指定日期区间新建异常单数 */
    @Select("SELECT COUNT(*) FROM mes_exception_order WHERE deleted = 0 "
            + "AND created_at >= #{start} AND created_at < #{end}")
    Long countException(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 指定日期区间完成质检任务数 */
    @Select("SELECT COUNT(*) FROM mes_inspection_task WHERE deleted = 0 AND status = 'COMPLETED' "
            + "AND created_at >= #{start} AND created_at < #{end}")
    Long countInspectionCompleted(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 未关闭异常数（OPEN/PROCESSING，全量） */
    @Select("SELECT COUNT(*) FROM mes_exception_order WHERE deleted = 0 AND status IN ('OPEN', 'PROCESSING')")
    Long openExceptionCount();

    /** 进行中工单数（全量） */
    @Select("SELECT COUNT(*) FROM mes_work_order WHERE deleted = 0 AND status IN ('RELEASED', 'IN_PROGRESS')")
    Long activeWorkOrderCount();

    /** 设备状态分布（全量） */
    @Select("SELECT status, COUNT(*) AS cnt FROM mes_equipment WHERE deleted = 0 GROUP BY status")
    List<EquipmentStatusRow> equipmentStatusCount();
}
