package com.smartfactory.mes.production.controller;

import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.production.dto.GanttTaskVO;
import com.smartfactory.mes.production.dto.ScheduleRunVO;
import com.smartfactory.mes.production.service.ScheduleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 生产排程接口（第 6 周）：执行前向排程 + 甘特图任务数据。
 * 执行仅 admin/计划员（production:schedule:run），查询全角色可看（production:schedule:query）。
 */
@RestController
@RequestMapping("/production/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 执行排程：活跃工单前向排布，结果落任务计划时间列（重跑覆盖即幂等） */
    @RequirePermission("production:schedule:run")
    @PostMapping("/run")
    public ApiResult<ScheduleRunVO> run() {
        return ApiResult.success(scheduleService.run());
    }

    /** 某日甘特图任务列表（date 缺省今天；跨日任务两天各返回一次） */
    @RequirePermission("production:schedule:query")
    @GetMapping("/gantt")
    public ApiResult<List<GanttTaskVO>> gantt(@RequestParam(required = false) LocalDate date) {
        return ApiResult.success(scheduleService.gantt(date == null ? LocalDate.now() : date));
    }
}
