package com.smartfactory.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.quality.dto.InspectionRecordVO;
import com.smartfactory.mes.quality.dto.InspectionTaskQueryDTO;
import com.smartfactory.mes.quality.dto.InspectionTaskVO;
import com.smartfactory.mes.quality.entity.MesInspectionRecord;
import com.smartfactory.mes.quality.entity.MesInspectionTask;
import com.smartfactory.mes.quality.enums.InspectionTaskStatus;
import com.smartfactory.mes.quality.mapper.MesInspectionRecordMapper;
import com.smartfactory.mes.quality.mapper.MesInspectionTaskMapper;
import com.smartfactory.mes.quality.service.InspectionTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 质检任务服务实现
 *
 * <p>第 3 周跨模块取舍（周报记录）：production 报工/取消在事务内调用 quality 的 Service 接口，
 * 反向 quality 实现内使用 production 的实体与追溯服务——同 Maven 模块双向类型依赖，
 * 生产化演进时可拆多模块按接口包治理。</p>
 */
@Service
public class InspectionTaskServiceImpl extends ServiceImpl<MesInspectionTaskMapper, MesInspectionTask>
        implements InspectionTaskService {

    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;
    private final SysUserMapper sysUserMapper;
    private final MesWorkOrderMapper workOrderMapper;
    private final MesInspectionRecordMapper inspectionRecordMapper;

    public InspectionTaskServiceImpl(OrderNoGenerator orderNoGenerator, TraceService traceService,
                                     SysUserMapper sysUserMapper, MesWorkOrderMapper workOrderMapper,
                                     MesInspectionRecordMapper inspectionRecordMapper) {
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
        this.sysUserMapper = sysUserMapper;
        this.workOrderMapper = workOrderMapper;
        this.inspectionRecordMapper = inspectionRecordMapper;
    }

    @Override
    public void generateFromCompletedTask(Long workOrderId, MesOperationTask task) {
        MesInspectionTask inspectionTask = new MesInspectionTask();
        inspectionTask.setInspectionTaskNo(orderNoGenerator.nextInspectionTaskNo());
        inspectionTask.setWorkOrderId(workOrderId);
        inspectionTask.setOperationTaskId(task.getId());
        inspectionTask.setProcessCodeSnapshot(task.getProcessCodeSnapshot());
        inspectionTask.setProcessNameSnapshot(task.getProcessNameSnapshot());
        inspectionTask.setWorkstationId(task.getWorkstationId());
        inspectionTask.setPlanQty(task.getCompletedQty());
        inspectionTask.setInspectedQty(0);
        inspectionTask.setGoodQty(0);
        inspectionTask.setDefectQty(0);
        inspectionTask.setStatus(InspectionTaskStatus.PENDING);
        this.save(inspectionTask);
        traceService.write(workOrderId, task.getId(), ActionType.INSPECT_TASK,
                Map.of("inspectionTaskNo", inspectionTask.getInspectionTaskNo(),
                        "planQty", inspectionTask.getPlanQty()));
    }

    @Override
    public int cancelByWorkOrder(Long workOrderId) {
        long count = this.count(new LambdaQueryWrapper<MesInspectionTask>()
                .eq(MesInspectionTask::getWorkOrderId, workOrderId)
                .in(MesInspectionTask::getStatus, InspectionTaskStatus.PENDING, InspectionTaskStatus.INSPECTING));
        if (count > 0) {
            this.update(new LambdaUpdateWrapper<MesInspectionTask>()
                    .eq(MesInspectionTask::getWorkOrderId, workOrderId)
                    .in(MesInspectionTask::getStatus, InspectionTaskStatus.PENDING, InspectionTaskStatus.INSPECTING)
                    .set(MesInspectionTask::getStatus, InspectionTaskStatus.CANCELLED));
        }
        return (int) count;
    }

    @Override
    public PageResult<InspectionTaskVO> page(InspectionTaskQueryDTO query) {
        LambdaQueryWrapper<MesInspectionTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkOrderId() != null, MesInspectionTask::getWorkOrderId, query.getWorkOrderId())
                .eq(query.getStatus() != null, MesInspectionTask::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(MesInspectionTask::getInspectionTaskNo, query.getKeyword())
                        .or().like(MesInspectionTask::getProcessNameSnapshot, query.getKeyword()))
                .orderByDesc(MesInspectionTask::getId);
        Page<MesInspectionTask> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return new PageResult<>(toVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public InspectionTaskVO getDetail(Long id) {
        return toVO(mustExist(id));
    }

    @Override
    public List<InspectionRecordVO> listRecords(Long taskId) {
        List<MesInspectionRecord> records = inspectionRecordMapper.selectList(
                new LambdaQueryWrapper<MesInspectionRecord>()
                        .eq(MesInspectionRecord::getInspectionTaskId, taskId)
                        .orderByAsc(MesInspectionRecord::getId));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> inspectorIds = records.stream().map(MesInspectionRecord::getInspectorId)
                .collect(Collectors.toSet());
        Map<Long, SysUser> inspectors = sysUserMapper.selectBatchIds(inspectorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return records.stream().map(r -> {
            InspectionRecordVO vo = InspectionRecordVO.of(r);
            SysUser inspector = inspectors.get(r.getInspectorId());
            if (inspector != null) {
                vo.setInspectorName(inspector.getRealName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void start(Long taskId) {
        MesInspectionTask task = mustExist(taskId);
        if (task.getStatus() == InspectionTaskStatus.INSPECTING) {
            return; // 同状态幂等：重复开始不报错、不重置开始时间
        }
        if (task.getStatus() != InspectionTaskStatus.PENDING) {
            throw new BusinessException("仅待检验的质检任务可以开始检验，当前状态: " + task.getStatus().getLabel());
        }
        // CAS 防并发双开始：并发请求只有一个能把 PENDING 改成 INSPECTING
        boolean updated = this.update(new LambdaUpdateWrapper<MesInspectionTask>()
                .eq(MesInspectionTask::getId, taskId)
                .eq(MesInspectionTask::getStatus, InspectionTaskStatus.PENDING)
                .set(MesInspectionTask::getStatus, InspectionTaskStatus.INSPECTING)
                .set(MesInspectionTask::getInspectorId, CurrentUserContext.getUserIdOrZero())
                .set(MesInspectionTask::getStartTime, LocalDateTime.now()));
        if (!updated) {
            throw new BusinessException("质检任务状态已变化，请刷新后重试");
        }
    }

    /** 列表批量回填：工单号 + 质检员名称（避免 N+1，面试可讲） */
    private List<InspectionTaskVO> toVOs(List<MesInspectionTask> tasks) {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> workOrderIds = tasks.stream().map(MesInspectionTask::getWorkOrderId).collect(Collectors.toSet());
        Map<Long, MesWorkOrder> workOrders = workOrderMapper.selectBatchIds(workOrderIds).stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        Set<Long> inspectorIds = tasks.stream().map(MesInspectionTask::getInspectorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        // 全 PENDING 时无质检员：selectBatchIds(空集) 会生成 IN () 的非法 SQL，须前置短路；
        // 空 HashMap 的 null 键查询返回 null 不抛错（Map.of() 的 null 键查询按规范抛 NPE，勿用）
        Map<Long, SysUser> inspectors = inspectorIds.isEmpty() ? new java.util.HashMap<>()
                : sysUserMapper.selectBatchIds(inspectorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return tasks.stream().map(t -> {
            InspectionTaskVO vo = InspectionTaskVO.of(t);
            MesWorkOrder wo = workOrders.get(t.getWorkOrderId());
            if (wo != null) {
                vo.setWorkOrderNo(wo.getWorkOrderNo());
            }
            SysUser inspector = inspectors.get(t.getInspectorId());
            if (inspector != null) {
                vo.setInspectorName(inspector.getRealName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private InspectionTaskVO toVO(MesInspectionTask task) {
        return toVOs(List.of(task)).get(0);
    }

    private MesInspectionTask mustExist(Long id) {
        MesInspectionTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("质检任务不存在: id=" + id);
        }
        return task;
    }
}
