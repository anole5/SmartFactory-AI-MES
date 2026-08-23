package com.smartfactory.mes.production.service;

import com.smartfactory.mes.production.dto.GanttTaskVO;
import com.smartfactory.mes.production.dto.ScheduleRunVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 生产排程服务（第 6 周）：前向排程 + 甘特图数据
 */
public interface ScheduleService {

    /**
     * 执行前向排程：活跃工单（RELEASED/IN_PROGRESS）按优先级 → 计划完工时间排序，
     * 工位组内按工单序 → 工序序串行排布，结果写任务 plan_start_time/plan_end_time。
     * 重跑覆盖即幂等；已完成/已取消任务保留旧值不重算。
     */
    ScheduleRunVO run();

    /**
     * 某日甘特图任务列表：计划窗口 [planStart, planEnd) 与该日有交集的任务
     * （工单号/工位/优先级回填，逾期标记按当前时间计算）
     */
    List<GanttTaskVO> gantt(LocalDate date);
}
