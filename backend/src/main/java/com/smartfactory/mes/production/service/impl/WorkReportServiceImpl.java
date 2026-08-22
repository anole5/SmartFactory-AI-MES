package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.dto.WorkReportQueryDTO;
import com.smartfactory.mes.production.dto.WorkReportSaveDTO;
import com.smartfactory.mes.production.dto.WorkReportVO;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.entity.MesWorkReport;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.enums.TaskStatus;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import com.smartfactory.mes.production.mapper.MesOperationTaskMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.mapper.MesWorkReportMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.production.service.WorkReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报工服务实现
 *
 * <p>报工校验链（核心事务，面试重点）：</p>
 * <ol>
 *   <li>任务必须 RUNNING（未开工/已暂停/已完成均拒绝）</li>
 *   <li>报工数量 = 合格 + 不良，且 ≥ 1</li>
 *   <li>前道合格校验：本工序累计合格 ≤ 前道累计合格（首道跳过），
 *       杜绝「后道报得比前道多」的穿透数据</li>
 *   <li>CAS 累加：一条 UPDATE 同时完成并发防护 + 超量校验 + 状态结转
 *       （WHERE status='RUNNING' AND completed_qty+本次<=plan_qty，
 *       达标自动 COMPLETED + 回填完工时间，MySQL 赋值自左向右故 IF 看到的是累加后值）</li>
 *   <li>插报工记录（只增不改）+ 写 REPORT 追溯</li>
 *   <li>最后一道工序：累计回写工单（完成数量 = 最后一道累计合格+不良），
 *       最后一道 COMPLETED → 工单 COMPLETED + 实际完工时间</li>
 * </ol>
 */
@Service
public class WorkReportServiceImpl extends ServiceImpl<MesWorkReportMapper, MesWorkReport>
        implements WorkReportService {

    private final MesOperationTaskMapper operationTaskMapper;
    private final MesWorkOrderMapper workOrderMapper;
    private final SysUserMapper sysUserMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;

    public WorkReportServiceImpl(MesOperationTaskMapper operationTaskMapper,
                                 MesWorkOrderMapper workOrderMapper,
                                 SysUserMapper sysUserMapper,
                                 OrderNoGenerator orderNoGenerator,
                                 TraceService traceService) {
        this.operationTaskMapper = operationTaskMapper;
        this.workOrderMapper = workOrderMapper;
        this.sysUserMapper = sysUserMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
    }

    @Override
    public PageResult<WorkReportVO> page(WorkReportQueryDTO query) {
        LambdaQueryWrapper<MesWorkReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkOrderId() != null, MesWorkReport::getWorkOrderId, query.getWorkOrderId())
                .eq(query.getTaskId() != null, MesWorkReport::getTaskId, query.getTaskId())
                .eq(query.getOperatorId() != null, MesWorkReport::getOperatorId, query.getOperatorId())
                .orderByDesc(MesWorkReport::getId);
        Page<MesWorkReport> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return new PageResult<>(toVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional
    public void report(WorkReportSaveDTO dto) {
        // ① 任务必须 RUNNING
        MesOperationTask task = operationTaskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new BusinessException("任务不存在: id=" + dto.getTaskId());
        }
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new BusinessException("仅生产中的任务可以报工，当前状态: " + task.getStatus().getLabel());
        }
        // ② 报工数量 = 合格 + 不良，且 ≥ 1
        int reportQty = dto.getReportQty();
        if (reportQty <= 0) {
            throw new BusinessException("报工数量必须大于 0");
        }
        if (dto.getGoodQty() + dto.getDefectQty() != reportQty) {
            throw new BusinessException("报工数量必须等于合格数量加不良数量");
        }
        // ③ 前道合格校验（首道跳过）：本工序累计合格 ≤ 前道累计合格。
        // 读后写窗口说明：本校验是「读前道 → 写本道」两步，极端并发下两笔报工可能同时通过校验；
        // 学习项目取舍，生产环境用 SELECT ... FOR UPDATE 或版本号乐观锁封闭窗口。
        if (task.getSequenceNo() > 1) {
            MesOperationTask prevTask = operationTaskMapper.selectOne(new LambdaQueryWrapper<MesOperationTask>()
                    .eq(MesOperationTask::getWorkOrderId, task.getWorkOrderId())
                    .eq(MesOperationTask::getSequenceNo, task.getSequenceNo() - 1));
            int prevGood = prevTask == null ? 0 : prevTask.getGoodQty();
            if (task.getGoodQty() + dto.getGoodQty() > prevGood) {
                throw new BusinessException("本工序累计合格数量(" + (task.getGoodQty() + dto.getGoodQty())
                        + ")不能超过前道工序合格数量(" + prevGood + ")");
            }
        }
        // ④ CAS 累加：一条 UPDATE 完成并发防护 + 超量校验 + 状态结转。
        // 影响 0 行 = 状态已变或超量，抛异常回滚整单（含后续的报工记录与追溯）。
        // MySQL UPDATE 赋值自左向右执行，IF 条件里的 completed_qty 是累加后的新值。
        int updated = operationTaskMapper.update(null, new LambdaUpdateWrapper<MesOperationTask>()
                .eq(MesOperationTask::getId, dto.getTaskId())
                .eq(MesOperationTask::getStatus, TaskStatus.RUNNING)
                .apply("completed_qty + {0} <= plan_qty", reportQty)
                .setSql("completed_qty = completed_qty + " + reportQty)
                .setSql("good_qty = good_qty + " + dto.getGoodQty())
                .setSql("defect_qty = defect_qty + " + dto.getDefectQty())
                .setSql("status = IF(completed_qty >= plan_qty, 'COMPLETED', status)")
                .setSql("end_time = IF(completed_qty >= plan_qty, NOW(), end_time)"));
        if (updated == 0) {
            throw new BusinessException("报工数量超出任务剩余计划数量或任务状态已变化，请刷新后重试");
        }
        // ⑤ 插报工记录（只增不改，审计数据）+ 重新读累计值
        MesOperationTask fresh = operationTaskMapper.selectById(dto.getTaskId());
        LocalDateTime now = LocalDateTime.now();
        MesWorkReport report = new MesWorkReport();
        report.setReportNo(orderNoGenerator.nextReportNo());
        report.setWorkOrderId(task.getWorkOrderId());
        report.setTaskId(dto.getTaskId());
        // 报工人 = 当前登录用户（非任务派工操作员，报工是登录用户本人的动作）
        report.setOperatorId(CurrentUserContext.getUserIdOrZero());
        report.setProductBatchNo(dto.getProductBatchNo());
        report.setReportQty(reportQty);
        report.setGoodQty(dto.getGoodQty());
        report.setDefectQty(dto.getDefectQty());
        report.setStartTime(dto.getStartTime() == null ? now : dto.getStartTime());
        report.setEndTime(dto.getEndTime() == null ? now : dto.getEndTime());
        report.setRemark(dto.getRemark());
        this.save(report);
        // ⑥ 需质检工序完成后生成质检任务——第 3 周质检模块接入（触发点 TODO）
        // TODO 第 3 周：if (fresh.getNeedInspection() && fresh.getStatus() == COMPLETED) 生成质检任务与异常单
        traceService.write(task.getWorkOrderId(), dto.getTaskId(), ActionType.REPORT,
                Map.of("reportNo", report.getReportNo(), "reportQty", reportQty,
                        "goodQty", dto.getGoodQty(), "defectQty", dto.getDefectQty()));
        // ⑦ 最后一道工序：累计回写工单（完成数量 = 最后一道累计合格+不良，合格 = 最后一道累计合格）；
        // 最后一道 COMPLETED → 工单 COMPLETED + 实际完工时间（CAS 翻转，0 行说明已 COMPLETED，静默跳过）
        MesOperationTask lastSeq = operationTaskMapper.selectOne(new QueryWrapper<MesOperationTask>()
                .select("MAX(sequence_no) AS sequence_no")
                .eq("work_order_id", task.getWorkOrderId()));
        if (lastSeq != null && fresh.getSequenceNo().intValue() == lastSeq.getSequenceNo()) {
            workOrderMapper.update(null, new LambdaUpdateWrapper<MesWorkOrder>()
                    .eq(MesWorkOrder::getId, task.getWorkOrderId())
                    .set(MesWorkOrder::getCompletedQty, fresh.getCompletedQty())
                    .set(MesWorkOrder::getGoodQty, fresh.getGoodQty())
                    .set(MesWorkOrder::getDefectQty, fresh.getDefectQty()));
            if (fresh.getStatus() == TaskStatus.COMPLETED) {
                workOrderMapper.update(null, new LambdaUpdateWrapper<MesWorkOrder>()
                        .eq(MesWorkOrder::getId, task.getWorkOrderId())
                        .eq(MesWorkOrder::getStatus, WorkOrderStatus.IN_PROGRESS)
                        .set(MesWorkOrder::getStatus, WorkOrderStatus.COMPLETED)
                        .set(MesWorkOrder::getActualEndTime, now));
            }
        }
    }

    private List<WorkReportVO> toVOs(List<MesWorkReport> reports) {
        if (reports.isEmpty()) {
            return Collections.emptyList();
        }
        // 一次查全：工单号、任务号+工序名、报工人名称（列表页避免 N+1，面试可讲）
        Set<Long> workOrderIds = reports.stream().map(MesWorkReport::getWorkOrderId).collect(Collectors.toSet());
        Map<Long, MesWorkOrder> workOrders = workOrderMapper.selectBatchIds(workOrderIds).stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        Set<Long> taskIds = reports.stream().map(MesWorkReport::getTaskId).collect(Collectors.toSet());
        Map<Long, MesOperationTask> tasks = operationTaskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(MesOperationTask::getId, Function.identity()));
        Set<Long> operatorIds = reports.stream().map(MesWorkReport::getOperatorId).collect(Collectors.toSet());
        Map<Long, SysUser> operators = sysUserMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return reports.stream().map(r -> {
            WorkReportVO vo = WorkReportVO.of(r);
            MesWorkOrder wo = workOrders.get(r.getWorkOrderId());
            if (wo != null) {
                vo.setWorkOrderNo(wo.getWorkOrderNo());
            }
            MesOperationTask task = tasks.get(r.getTaskId());
            if (task != null) {
                vo.setTaskNo(task.getTaskNo());
                vo.setProcessNameSnapshot(task.getProcessNameSnapshot());
            }
            SysUser operator = operators.get(r.getOperatorId());
            if (operator != null) {
                vo.setOperatorName(operator.getRealName());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
