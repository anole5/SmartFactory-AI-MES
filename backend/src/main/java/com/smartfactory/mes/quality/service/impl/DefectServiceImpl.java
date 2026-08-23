package com.smartfactory.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.quality.dto.DefectQueryDTO;
import com.smartfactory.mes.quality.dto.DefectRecordVO;
import com.smartfactory.mes.quality.entity.MesDefectRecord;
import com.smartfactory.mes.quality.entity.MesExceptionOrder;
import com.smartfactory.mes.quality.entity.MesInspectionTask;
import com.smartfactory.mes.quality.enums.ExceptionSourceType;
import com.smartfactory.mes.quality.enums.ExceptionStatus;
import com.smartfactory.mes.quality.mapper.MesDefectRecordMapper;
import com.smartfactory.mes.quality.mapper.MesExceptionOrderMapper;
import com.smartfactory.mes.quality.mapper.MesInspectionTaskMapper;
import com.smartfactory.mes.quality.service.DefectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 不良记录服务实现
 */
@Service
public class DefectServiceImpl extends ServiceImpl<MesDefectRecordMapper, MesDefectRecord>
        implements DefectService {

    private final MesWorkOrderMapper workOrderMapper;
    private final MesInspectionTaskMapper inspectionTaskMapper;
    private final MesExceptionOrderMapper exceptionOrderMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;

    public DefectServiceImpl(MesWorkOrderMapper workOrderMapper,
                             MesInspectionTaskMapper inspectionTaskMapper,
                             MesExceptionOrderMapper exceptionOrderMapper,
                             OrderNoGenerator orderNoGenerator,
                             TraceService traceService) {
        this.workOrderMapper = workOrderMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
    }

    @Override
    public PageResult<DefectRecordVO> page(DefectQueryDTO query) {
        LambdaQueryWrapper<MesDefectRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkOrderId() != null, MesDefectRecord::getWorkOrderId, query.getWorkOrderId())
                .eq(StringUtils.hasText(query.getDefectCode()), MesDefectRecord::getDefectCode, query.getDefectCode())
                .like(StringUtils.hasText(query.getKeyword()), MesDefectRecord::getDefectNo, query.getKeyword())
                .orderByDesc(MesDefectRecord::getId);
        Page<MesDefectRecord> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<MesDefectRecord> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        // 批量回填：工单号 + 工序快照（来自质检任务），避免 N+1
        Set<Long> workOrderIds = records.stream().map(MesDefectRecord::getWorkOrderId).collect(Collectors.toSet());
        Map<Long, MesWorkOrder> workOrders = workOrderMapper.selectBatchIds(workOrderIds).stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        Set<Long> taskIds = records.stream().map(MesDefectRecord::getInspectionTaskId).collect(Collectors.toSet());
        Map<Long, MesInspectionTask> tasks = inspectionTaskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(MesInspectionTask::getId, Function.identity()));
        List<DefectRecordVO> vos = records.stream().map(r -> {
            DefectRecordVO vo = DefectRecordVO.of(r);
            MesWorkOrder wo = workOrders.get(r.getWorkOrderId());
            if (wo != null) {
                vo.setWorkOrderNo(wo.getWorkOrderNo());
            }
            MesInspectionTask task = tasks.get(r.getInspectionTaskId());
            if (task != null) {
                vo.setProcessCodeSnapshot(task.getProcessCodeSnapshot());
                vo.setProcessNameSnapshot(task.getProcessNameSnapshot());
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional
    public Long toException(Long defectId) {
        MesDefectRecord defect = this.getById(defectId);
        if (defect == null) {
            throw new BusinessException("不良记录不存在: id=" + defectId);
        }
        // 防重复：同不良已有未关闭（OPEN/PROCESSING）异常单则拒绝
        long openCount = exceptionOrderMapper.selectCount(new LambdaQueryWrapper<MesExceptionOrder>()
                .eq(MesExceptionOrder::getDefectRecordId, defectId)
                .in(MesExceptionOrder::getStatus, ExceptionStatus.OPEN, ExceptionStatus.PROCESSING));
        if (openCount > 0) {
            throw new BusinessException("该不良记录已存在未关闭的异常单，不能重复生成");
        }
        MesExceptionOrder order = new MesExceptionOrder();
        order.setExceptionNo(orderNoGenerator.nextExceptionNo());
        order.setSourceType(ExceptionSourceType.DEFECT);
        order.setDefectRecordId(defect.getId());
        order.setWorkOrderId(defect.getWorkOrderId());
        order.setOperationTaskId(defect.getOperationTaskId());
        order.setInspectionTaskId(defect.getInspectionTaskId());
        order.setDefectCode(defect.getDefectCode());
        order.setDescription("不良 " + defect.getDefectNo() + "（" + defect.getDefectCode()
                + "）自动生成异常单，不良数量 " + defect.getDefectQty());
        order.setStatus(ExceptionStatus.OPEN);
        exceptionOrderMapper.insert(order);
        traceService.write(defect.getWorkOrderId(), defect.getOperationTaskId(), ActionType.EXCEPTION_CREATE,
                Map.of("exceptionNo", order.getExceptionNo(), "sourceType", order.getSourceType().getCode(),
                        "defectNo", defect.getDefectNo()));
        return order.getId();
    }
}
