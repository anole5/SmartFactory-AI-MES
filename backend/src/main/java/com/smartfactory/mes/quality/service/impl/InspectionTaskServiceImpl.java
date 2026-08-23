package com.smartfactory.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.quality.entity.MesInspectionTask;
import com.smartfactory.mes.quality.enums.InspectionTaskStatus;
import com.smartfactory.mes.quality.mapper.MesInspectionTaskMapper;
import com.smartfactory.mes.quality.service.InspectionTaskService;
import org.springframework.stereotype.Service;

import java.util.Map;

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

    public InspectionTaskServiceImpl(OrderNoGenerator orderNoGenerator, TraceService traceService) {
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
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
}
