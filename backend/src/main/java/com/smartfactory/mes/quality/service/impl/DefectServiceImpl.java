package com.smartfactory.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.quality.dto.DefectQueryDTO;
import com.smartfactory.mes.quality.dto.DefectRecordVO;
import com.smartfactory.mes.quality.entity.MesDefectRecord;
import com.smartfactory.mes.quality.entity.MesInspectionTask;
import com.smartfactory.mes.quality.mapper.MesDefectRecordMapper;
import com.smartfactory.mes.quality.mapper.MesInspectionTaskMapper;
import com.smartfactory.mes.quality.service.DefectService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public DefectServiceImpl(MesWorkOrderMapper workOrderMapper,
                             MesInspectionTaskMapper inspectionTaskMapper) {
        this.workOrderMapper = workOrderMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
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
}
