package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.enums.UserStatus;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.integration.wms.service.WmsService;
import com.smartfactory.mes.master.entity.MesWorkstation;
import com.smartfactory.mes.master.enums.WorkstationStatus;
import com.smartfactory.mes.master.mapper.WorkstationMapper;
import com.smartfactory.mes.production.dto.OperationTaskVO;
import com.smartfactory.mes.production.dto.TaskAssignDTO;
import com.smartfactory.mes.production.dto.TaskQueryDTO;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.enums.TaskStatus;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import com.smartfactory.mes.production.mapper.MesOperationTaskMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.OperationTaskService;
import com.smartfactory.mes.production.service.TraceService;
import org.springframework.context.annotation.Lazy;
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
 * 工序任务服务实现：批量回填工单号/工位/操作员名称（避免 N+1 查询，面试可讲）
 *
 * <p>状态机（术语表权威版）：PENDING → ASSIGNED → RUNNING ↔ PAUSED → COMPLETED；
 * 派工/开工/暂停/继续每个动作写追溯记录，同状态幂等、非法流转 409。</p>
 */
@Service
public class OperationTaskServiceImpl extends ServiceImpl<MesOperationTaskMapper, MesOperationTask>
        implements OperationTaskService {

    private final MesWorkOrderMapper workOrderMapper;
    private final WorkstationMapper workstationMapper;
    private final SysUserMapper sysUserMapper;
    private final TraceService traceService;
    private final WmsService wmsService;

    public OperationTaskServiceImpl(MesWorkOrderMapper workOrderMapper,
                                    WorkstationMapper workstationMapper,
                                    SysUserMapper sysUserMapper,
                                    TraceService traceService,
                                    // @Lazy 解环：WmsService -> WorkOrderService -> OperationTaskService 形成引用环，
                                    // 懒代理延迟初始化，开工钩子只在真正调用时才进入 WMS 链路
                                    @Lazy WmsService wmsService) {
        this.workOrderMapper = workOrderMapper;
        this.workstationMapper = workstationMapper;
        this.sysUserMapper = sysUserMapper;
        this.traceService = traceService;
        this.wmsService = wmsService;
    }

    @Override
    public PageResult<OperationTaskVO> page(TaskQueryDTO query) {
        LambdaQueryWrapper<MesOperationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkOrderId() != null, MesOperationTask::getWorkOrderId, query.getWorkOrderId())
                .eq(query.getStatus() != null, MesOperationTask::getStatus, query.getStatus())
                .eq(query.getWorkstationId() != null, MesOperationTask::getWorkstationId, query.getWorkstationId())
                .eq(query.getOperatorId() != null, MesOperationTask::getOperatorId, query.getOperatorId())
                .orderByAsc(MesOperationTask::getWorkOrderId)
                .orderByAsc(MesOperationTask::getSequenceNo);
        Page<MesOperationTask> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(this::toVO));
    }

    @Override
    public List<OperationTaskVO> listByWorkOrder(Long workOrderId) {
        List<MesOperationTask> tasks = this.list(new LambdaQueryWrapper<MesOperationTask>()
                .eq(MesOperationTask::getWorkOrderId, workOrderId)
                .orderByAsc(MesOperationTask::getSequenceNo));
        return toVOs(tasks);
    }

    @Override
    @Transactional
    public void assign(Long taskId, TaskAssignDTO dto) {
        MesOperationTask task = mustExist(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BusinessException("仅待派工状态的任务可以派工，当前状态: " + task.getStatus().getLabel());
        }
        SysUser operator = sysUserMapper.selectById(dto.getOperatorId());
        if (operator == null || operator.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException("操作员不存在或已停用: id=" + dto.getOperatorId());
        }
        task.setOperatorId(dto.getOperatorId());
        if (dto.getWorkstationId() != null) {
            // 工位覆盖：校验启用后替换，并同步刷新设备快照
            MesWorkstation ws = workstationMapper.selectById(dto.getWorkstationId());
            if (ws == null || ws.getStatus() != WorkstationStatus.ENABLED) {
                throw new BusinessException("工位不存在或已停用: id=" + dto.getWorkstationId());
            }
            task.setWorkstationId(dto.getWorkstationId());
            task.setEquipmentCodeSnapshot(ws.getEquipmentCode());
            task.setEquipmentNameSnapshot(ws.getEquipmentName());
        }
        task.setStatus(TaskStatus.ASSIGNED);
        this.updateById(task);
        traceService.write(task.getWorkOrderId(), taskId, ActionType.ASSIGN,
                Map.of("taskNo", task.getTaskNo(), "operatorId", dto.getOperatorId(),
                        "workstationId", task.getWorkstationId()));
    }

    @Override
    @Transactional
    public void start(Long taskId) {
        MesOperationTask task = mustExist(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) {
            return; // 幂等：重复开工不报错
        }
        if (task.getStatus() != TaskStatus.ASSIGNED) {
            throw new BusinessException("仅已派工状态的任务可以开工，当前状态: " + task.getStatus().getLabel());
        }
        // 第 5 周系统集成开工钩子：ERP 推单工单须关键物料足额领用方可开工
        // （手建工单无外部订单记录直接放行，老冒烟链路零影响）
        wmsService.assertPickReady(task.getWorkOrderId());
        task.setStatus(TaskStatus.RUNNING);
        task.setStartTime(LocalDateTime.now());
        this.updateById(task);
        // 工单级联：首个任务开工时 RELEASED -> IN_PROGRESS + 回填实际开工时间。
        // CAS 条件更新：多个任务并发开工只有一个能翻转工单状态，输家 0 行不报错（工单已 IN_PROGRESS）
        workOrderMapper.update(null, new LambdaUpdateWrapper<MesWorkOrder>()
                .eq(MesWorkOrder::getId, task.getWorkOrderId())
                .eq(MesWorkOrder::getStatus, WorkOrderStatus.RELEASED)
                .set(MesWorkOrder::getStatus, WorkOrderStatus.IN_PROGRESS)
                .set(MesWorkOrder::getActualStartTime, LocalDateTime.now()));
        traceService.write(task.getWorkOrderId(), taskId, ActionType.START,
                Map.of("taskNo", task.getTaskNo(), "operatorId", task.getOperatorId()));
    }

    @Override
    @Transactional
    public void pause(Long taskId) {
        MesOperationTask task = mustExist(taskId);
        if (task.getStatus() == TaskStatus.PAUSED) {
            return; // 幂等
        }
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new BusinessException("仅生产中的任务可以暂停，当前状态: " + task.getStatus().getLabel());
        }
        task.setStatus(TaskStatus.PAUSED);
        this.updateById(task);
        traceService.write(task.getWorkOrderId(), taskId, ActionType.PAUSE,
                Map.of("taskNo", task.getTaskNo(), "operatorId", task.getOperatorId()));
    }

    @Override
    @Transactional
    public void resume(Long taskId) {
        MesOperationTask task = mustExist(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) {
            return; // 幂等
        }
        if (task.getStatus() != TaskStatus.PAUSED) {
            throw new BusinessException("仅已暂停的任务可以继续，当前状态: " + task.getStatus().getLabel());
        }
        task.setStatus(TaskStatus.RUNNING);
        this.updateById(task);
        traceService.write(task.getWorkOrderId(), taskId, ActionType.RESUME,
                Map.of("taskNo", task.getTaskNo(), "operatorId", task.getOperatorId()));
    }

    private MesOperationTask mustExist(Long taskId) {
        MesOperationTask task = this.getById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: id=" + taskId);
        }
        return task;
    }

    private List<OperationTaskVO> toVOs(List<MesOperationTask> tasks) {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        // 一次查全：工单号、工位、操作员（列表只展示，不做分页内 N+1）
        Set<Long> workOrderIds = tasks.stream().map(MesOperationTask::getWorkOrderId).collect(Collectors.toSet());
        Map<Long, MesWorkOrder> workOrders = workOrderMapper.selectBatchIds(workOrderIds).stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        Set<Long> workstationIds = tasks.stream().map(MesOperationTask::getWorkstationId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesWorkstation> workstations = workstationIds.isEmpty() ? Collections.emptyMap()
                : workstationMapper.selectBatchIds(workstationIds).stream()
                .collect(Collectors.toMap(MesWorkstation::getId, Function.identity()));
        Set<Long> operatorIds = tasks.stream().map(MesOperationTask::getOperatorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> operators = operatorIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return tasks.stream().map(t -> {
            OperationTaskVO vo = OperationTaskVO.of(t);
            MesWorkOrder wo = workOrders.get(t.getWorkOrderId());
            if (wo != null) {
                vo.setWorkOrderNo(wo.getWorkOrderNo());
            }
            MesWorkstation ws = workstations.get(t.getWorkstationId());
            if (ws != null) {
                vo.setWorkstationCode(ws.getWorkstationCode());
                vo.setWorkstationName(ws.getWorkstationName());
            }
            SysUser operator = operators.get(t.getOperatorId());
            if (operator != null) {
                vo.setOperatorName(operator.getRealName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private OperationTaskVO toVO(MesOperationTask task) {
        return toVOs(Collections.singletonList(task)).get(0);
    }
}
