package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartfactory.mes.master.entity.MesWorkstation;
import com.smartfactory.mes.master.mapper.WorkstationMapper;
import com.smartfactory.mes.production.dto.GanttTaskVO;
import com.smartfactory.mes.production.dto.ScheduleRunVO;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.enums.OrderPriority;
import com.smartfactory.mes.production.enums.TaskStatus;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import com.smartfactory.mes.production.mapper.MesOperationTaskMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.ScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生产排程 Service 实现（第 6 周，纯内存前向排程）：
 *
 * <ol>
 *   <li>活跃工单（RELEASED/IN_PROGRESS）排序：优先级 HIGH→LOW → 计划完工早→晚（NULL 最后）→ id</li>
 *   <li>取这些工单下未完成任务（PENDING/ASSIGNED/RUNNING/PAUSED，完成/取消保留旧值不重算）</li>
 *   <li>按工位分组（NULL 归虚拟工位），组内按工单全局序 → 工序序排布</li>
 *   <li>逐任务前向排程：cursor 初始 = 今日 08:00；start = max(工单.planStartTime, cursor)；
 *       duration = ceil(standardMinutes × planQty) 分钟；end = start + duration；
 *       结果 UPDATE 任务 plan_start_time/plan_end_time（重跑覆盖即幂等）</li>
 * </ol>
 */
@Service
public class ScheduleServiceImpl implements ScheduleService {

    private final MesWorkOrderMapper workOrderMapper;
    private final MesOperationTaskMapper operationTaskMapper;
    private final WorkstationMapper workstationMapper;

    public ScheduleServiceImpl(MesWorkOrderMapper workOrderMapper,
                               MesOperationTaskMapper operationTaskMapper,
                               WorkstationMapper workstationMapper) {
        this.workOrderMapper = workOrderMapper;
        this.operationTaskMapper = operationTaskMapper;
        this.workstationMapper = workstationMapper;
    }

    @Override
    @Transactional
    public ScheduleRunVO run() {
        List<MesWorkOrder> workOrders = workOrderMapper.selectList(new LambdaQueryWrapper<MesWorkOrder>()
                .in(MesWorkOrder::getStatus, WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS));
        workOrders.sort(Comparator
                .comparingInt((MesWorkOrder w) -> priorityRank(w.getPriority()))
                .thenComparing(w -> w.getPlanEndTime() == null ? LocalDateTime.MAX : w.getPlanEndTime())
                .thenComparing(MesWorkOrder::getId));
        if (workOrders.isEmpty()) {
            return new ScheduleRunVO(0, 0, LocalDateTime.now());
        }
        Map<Long, Integer> woOrder = new HashMap<>();
        for (int i = 0; i < workOrders.size(); i++) {
            woOrder.put(workOrders.get(i).getId(), i);
        }
        Map<Long, MesWorkOrder> woById = workOrders.stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        List<Long> woIds = new ArrayList<>(woOrder.keySet());
        List<MesOperationTask> tasks = operationTaskMapper.selectList(new LambdaQueryWrapper<MesOperationTask>()
                .in(MesOperationTask::getWorkOrderId, woIds)
                .in(MesOperationTask::getStatus, TaskStatus.PENDING, TaskStatus.ASSIGNED,
                        TaskStatus.RUNNING, TaskStatus.PAUSED));
        // 按工位分组：NULL 工位归 0L 虚拟组（一视同仁参与排布）
        Map<Long, List<MesOperationTask>> byWorkstation = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getWorkstationId() == null ? 0L : t.getWorkstationId()));
        LocalDateTime dayStart = LocalDate.now().atTime(8, 0);
        int updated = 0;
        for (List<MesOperationTask> group : byWorkstation.values()) {
            group.sort(Comparator
                    .comparingInt((MesOperationTask t) -> woOrder.getOrDefault(t.getWorkOrderId(), Integer.MAX_VALUE))
                    .thenComparing(t -> t.getSequenceNo() == null ? Integer.MAX_VALUE : t.getSequenceNo()));
            LocalDateTime cursor = dayStart;
            for (MesOperationTask task : group) {
                LocalDateTime start = cursor;
                MesWorkOrder wo = woById.get(task.getWorkOrderId());
                if (wo != null && wo.getPlanStartTime() != null && wo.getPlanStartTime().isAfter(start)) {
                    start = wo.getPlanStartTime();
                }
                // 时长 = ceil(标准工时(分钟/台) × 计划台数)，至少 1 分钟
                double std = task.getStandardMinutes() == null ? 0 : task.getStandardMinutes().doubleValue();
                long minutes = Math.max(1, (long) Math.ceil(std * (task.getPlanQty() == null ? 0 : task.getPlanQty())));
                LocalDateTime end = start.plusMinutes(minutes);
                operationTaskMapper.update(null, new LambdaUpdateWrapper<MesOperationTask>()
                        .eq(MesOperationTask::getId, task.getId())
                        .set(MesOperationTask::getPlanStartTime, start)
                        .set(MesOperationTask::getPlanEndTime, end));
                updated++;
                cursor = end;
            }
        }
        return new ScheduleRunVO(workOrders.size(), updated, LocalDateTime.now());
    }

    @Override
    public List<GanttTaskVO> gantt(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        // 计划窗口 [planStart, planEnd) 与该日有交集；跨日任务两天各返回一次（前端按起止时刻渲染）
        List<MesOperationTask> tasks = operationTaskMapper.selectList(new LambdaQueryWrapper<MesOperationTask>()
                .lt(MesOperationTask::getPlanStartTime, dayEnd)
                .gt(MesOperationTask::getPlanEndTime, dayStart));
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> woIds = tasks.stream().map(MesOperationTask::getWorkOrderId).collect(Collectors.toSet());
        Map<Long, MesWorkOrder> wos = workOrderMapper.selectBatchIds(woIds).stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        Set<Long> wsIds = tasks.stream().map(MesOperationTask::getWorkstationId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesWorkstation> wss = wsIds.isEmpty() ? Collections.emptyMap()
                : workstationMapper.selectBatchIds(wsIds).stream()
                .collect(Collectors.toMap(MesWorkstation::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .sorted(Comparator
                        .comparing((MesOperationTask t) -> t.getWorkstationId() == null ? Long.MAX_VALUE : t.getWorkstationId())
                        .thenComparing(t -> t.getSequenceNo() == null ? Integer.MAX_VALUE : t.getSequenceNo()))
                .map(t -> {
                    GanttTaskVO vo = new GanttTaskVO();
                    vo.setTaskId(t.getId());
                    vo.setTaskNo(t.getTaskNo());
                    vo.setWorkOrderId(t.getWorkOrderId());
                    MesWorkOrder wo = wos.get(t.getWorkOrderId());
                    vo.setWorkOrderNo(wo == null ? null : wo.getWorkOrderNo());
                    vo.setProcessCodeSnapshot(t.getProcessCodeSnapshot());
                    vo.setProcessNameSnapshot(t.getProcessNameSnapshot());
                    vo.setSequenceNo(t.getSequenceNo());
                    vo.setWorkstationId(t.getWorkstationId());
                    MesWorkstation ws = t.getWorkstationId() == null ? null : wss.get(t.getWorkstationId());
                    vo.setWorkstationCode(ws == null ? null : ws.getWorkstationCode());
                    vo.setWorkstationName(ws == null ? "未分配工位" : ws.getWorkstationName());
                    vo.setPlanStartTime(t.getPlanStartTime());
                    vo.setPlanEndTime(t.getPlanEndTime());
                    vo.setStatus(t.getStatus().getCode());
                    vo.setPriority(wo == null ? null : wo.getPriority().getCode());
                    vo.setPlanQty(t.getPlanQty());
                    boolean done = t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED;
                    vo.setIsOverdue(!done && t.getPlanEndTime() != null && t.getPlanEndTime().isBefore(now));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 优先级排序权重：HIGH=0 < NORMAL=1 < LOW=2（越小越先） */
    private int priorityRank(OrderPriority priority) {
        if (priority == OrderPriority.HIGH) {
            return 0;
        }
        if (priority == OrderPriority.LOW) {
            return 2;
        }
        return 1;
    }
}
