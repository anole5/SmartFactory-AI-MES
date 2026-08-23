package com.smartfactory.mes.dashboard.mapper;

import com.smartfactory.mes.dashboard.dto.DashboardEquipmentRow;
import com.smartfactory.mes.dashboard.dto.DashboardWorkOrderRow;
import com.smartfactory.mes.dashboard.dto.DefectCountRow;
import com.smartfactory.mes.dashboard.dto.ProcessYieldRow;
import com.smartfactory.mes.dashboard.dto.StatusCountRow;
import com.smartfactory.mes.dashboard.dto.YieldRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 看板聚合 Mapper（注解 SQL）
 *
 * <p>【第 3 周坑】自定义 SQL 不经过 MyBatis-Plus 的 wrapper，逻辑删除过滤不会自动附加，
 * 所有 SQL 必须显式写 deleted = 0。</p>
 */
@Mapper
public interface DashboardMapper {

    /** 今日产量（今日报工合格数汇总） */
    @Select("SELECT COALESCE(SUM(good_qty), 0) FROM mes_work_report WHERE deleted = 0 AND DATE(created_at) = CURDATE()")
    Long todayOutputQty();

    /** 今日报工记录数 */
    @Select("SELECT COUNT(*) FROM mes_work_report WHERE deleted = 0 AND DATE(created_at) = CURDATE()")
    Long todayReportCount();

    /** 今日不良数（报工不良合计） */
    @Select("SELECT COALESCE(SUM(defect_qty), 0) FROM mes_work_report WHERE deleted = 0 AND DATE(created_at) = CURDATE()")
    Long todayDefectQty();

    /** 进行中工单数 */
    @Select("SELECT COUNT(*) FROM mes_work_order WHERE deleted = 0 AND status = 'IN_PROGRESS'")
    Long inProgressWorkOrderCount();

    /** 未关闭异常数（OPEN/PROCESSING） */
    @Select("SELECT COUNT(*) FROM mes_exception_order WHERE deleted = 0 AND status IN ('OPEN', 'PROCESSING')")
    Long openExceptionCount();

    /** 设备状态分布 */
    @Select("SELECT status, COUNT(*) AS cnt FROM mes_equipment WHERE deleted = 0 GROUP BY status")
    List<StatusCountRow> equipmentStatusCount();

    /** 进行中/已下发工单进度（近 10 条） */
    @Select("SELECT id, work_order_no, product_code_snapshot, product_name_snapshot, plan_qty, completed_qty, status " +
            "FROM mes_work_order WHERE deleted = 0 AND status IN ('RELEASED', 'IN_PROGRESS') " +
            "ORDER BY id DESC LIMIT 10")
    List<DashboardWorkOrderRow> activeWorkOrders();

    /** 今日合格/不良合计（整体良率分子分母） */
    @Select("SELECT COALESCE(SUM(good_qty), 0) AS good, COALESCE(SUM(defect_qty), 0) AS defect " +
            "FROM mes_work_report WHERE deleted = 0 AND DATE(created_at) = CURDATE()")
    YieldRow todayYield();

    /** 工序良率：已完成质检任务按工序快照分组汇总（测试/老化等工序良率数据源） */
    @Select("SELECT process_name_snapshot AS processName, SUM(good_qty) AS good, SUM(defect_qty) AS defect " +
            "FROM mes_inspection_task WHERE deleted = 0 AND status = 'COMPLETED' " +
            "GROUP BY process_name_snapshot ORDER BY processName")
    List<ProcessYieldRow> processYield();

    /** 不良分布：按不良编码分组（数量降序） */
    @Select("SELECT defect_code AS defectCode, SUM(defect_qty) AS cnt FROM mes_defect_record " +
            "WHERE deleted = 0 GROUP BY defect_code ORDER BY cnt DESC")
    List<DefectCountRow> defectDistribution();

    /** 设备列表（工位名称回填） */
    @Select("SELECT e.equipment_code, e.equipment_name, e.status, w.workstation_name " +
            "FROM mes_equipment e LEFT JOIN mes_workstation w ON e.workstation_id = w.id AND w.deleted = 0 " +
            "WHERE e.deleted = 0 ORDER BY e.id")
    List<DashboardEquipmentRow> equipmentList();
}
