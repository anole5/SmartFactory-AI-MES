package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.entity.MesWorkstation;
import com.smartfactory.mes.master.mapper.WorkstationMapper;
import com.smartfactory.mes.production.dto.OperationTaskVO;
import com.smartfactory.mes.production.dto.TaskQueryDTO;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.mapper.MesOperationTaskMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.OperationTaskService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工序任务服务实现：批量回填工单号/工位/操作员名称（避免 N+1 查询，面试可讲）
 */
@Service
public class OperationTaskServiceImpl extends ServiceImpl<MesOperationTaskMapper, MesOperationTask>
        implements OperationTaskService {

    private final MesWorkOrderMapper workOrderMapper;
    private final WorkstationMapper workstationMapper;
    private final SysUserMapper sysUserMapper;

    public OperationTaskServiceImpl(MesWorkOrderMapper workOrderMapper,
                                    WorkstationMapper workstationMapper,
                                    SysUserMapper sysUserMapper) {
        this.workOrderMapper = workOrderMapper;
        this.workstationMapper = workstationMapper;
        this.sysUserMapper = sysUserMapper;
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
