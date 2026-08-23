package com.smartfactory.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.quality.dto.InspectionRecordSaveDTO;
import com.smartfactory.mes.quality.entity.MesDefectRecord;
import com.smartfactory.mes.quality.entity.MesInspectionRecord;
import com.smartfactory.mes.quality.entity.MesInspectionTask;
import com.smartfactory.mes.quality.enums.InspectionTaskStatus;
import com.smartfactory.mes.quality.mapper.MesDefectRecordMapper;
import com.smartfactory.mes.quality.mapper.MesInspectionRecordMapper;
import com.smartfactory.mes.quality.mapper.MesInspectionTaskMapper;
import com.smartfactory.mes.quality.service.InspectionRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 质检记录服务实现
 *
 * <p>检验录入校验链（核心事务，面试重点）：</p>
 * <ol>
 *   <li>任务必须 INSPECTING（PENDING 直接录入拒绝——必须先开始检验）</li>
 *   <li>合格 + 不良 ≥ 1（空录入拒绝）</li>
 *   <li>不良明细行数量合计 = 不良数量（不良数>0 必须有明细，防数量与明细对不上）</li>
 *   <li>CAS 累计：一条 UPDATE 完成并发防护 + 超量校验 + 状态结转
 *       （WHERE status='INSPECTING' AND inspected_qty+本次&lt;=plan_qty，
 *       达标自动 COMPLETED + 回填完成时间——与报工同款技巧）</li>
 *   <li>插质检记录（只增不改）+ INSPECT 追溯</li>
 *   <li>逐不良行插不良记录 + DEFECT 追溯</li>
 * </ol>
 */
@Service
public class InspectionRecordServiceImpl extends ServiceImpl<MesInspectionRecordMapper, MesInspectionRecord>
        implements InspectionRecordService {

    private final MesInspectionTaskMapper inspectionTaskMapper;
    private final MesDefectRecordMapper defectRecordMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;

    public InspectionRecordServiceImpl(MesInspectionTaskMapper inspectionTaskMapper,
                                       MesDefectRecordMapper defectRecordMapper,
                                       OrderNoGenerator orderNoGenerator,
                                       TraceService traceService) {
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.defectRecordMapper = defectRecordMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
    }

    @Override
    @Transactional
    public Long create(InspectionRecordSaveDTO dto) {
        // ① 任务必须 INSPECTING
        MesInspectionTask task = inspectionTaskMapper.selectById(dto.getInspectionTaskId());
        if (task == null) {
            throw new BusinessException("质检任务不存在: id=" + dto.getInspectionTaskId());
        }
        if (task.getStatus() != InspectionTaskStatus.INSPECTING) {
            throw new BusinessException("仅检验中的质检任务可以录入，当前状态: " + task.getStatus().getLabel());
        }
        // ② 合格 + 不良 ≥ 1
        if (dto.getGoodQty() + dto.getDefectQty() <= 0) {
            throw new BusinessException("合格数量与不良数量不能同时为 0");
        }
        // ③ 不良明细行数量合计 = 不良数量
        List<InspectionRecordSaveDTO.DefectItem> defectItems =
                dto.getDefectItems() == null ? List.of() : dto.getDefectItems();
        int defectSum = defectItems.stream().mapToInt(InspectionRecordSaveDTO.DefectItem::getDefectQty).sum();
        if (defectSum != dto.getDefectQty()) {
            throw new BusinessException("不良明细数量合计(" + defectSum + ")必须等于不良数量(" + dto.getDefectQty() + ")");
        }
        // ④ CAS 累计：并发防护 + 超量校验 + 达标结转（与报工同款一条 UPDATE）
        int thisQty = dto.getGoodQty() + dto.getDefectQty();
        int updated = inspectionTaskMapper.update(null, new LambdaUpdateWrapper<MesInspectionTask>()
                .eq(MesInspectionTask::getId, dto.getInspectionTaskId())
                .eq(MesInspectionTask::getStatus, InspectionTaskStatus.INSPECTING)
                .apply("inspected_qty + {0} <= plan_qty", thisQty)
                .setSql("inspected_qty = inspected_qty + " + thisQty)
                .setSql("good_qty = good_qty + " + dto.getGoodQty())
                .setSql("defect_qty = defect_qty + " + dto.getDefectQty())
                .setSql("status = IF(inspected_qty >= plan_qty, 'COMPLETED', status)")
                .setSql("end_time = IF(inspected_qty >= plan_qty, NOW(), end_time)"));
        if (updated == 0) {
            throw new BusinessException("检验数量超出任务剩余送检数量或任务状态已变化，请刷新后重试");
        }
        // ⑤ 插质检记录（只增不改，审计数据）+ INSPECT 追溯
        MesInspectionRecord record = new MesInspectionRecord();
        record.setInspectionRecordNo(orderNoGenerator.nextInspectionRecordNo());
        record.setInspectionTaskId(task.getId());
        record.setWorkOrderId(task.getWorkOrderId());
        record.setOperationTaskId(task.getOperationTaskId());
        record.setGoodQty(dto.getGoodQty());
        record.setDefectQty(dto.getDefectQty());
        record.setInspectTime(LocalDateTime.now());
        record.setInspectorId(CurrentUserContext.getUserIdOrZero());
        record.setRemark(dto.getRemark());
        this.save(record);
        traceService.write(task.getWorkOrderId(), task.getOperationTaskId(), ActionType.INSPECT,
                Map.of("inspectionRecordNo", record.getInspectionRecordNo(),
                        "goodQty", dto.getGoodQty(), "defectQty", dto.getDefectQty()));
        // ⑥ 逐不良行插不良记录 + DEFECT 追溯
        for (InspectionRecordSaveDTO.DefectItem item : defectItems) {
            MesDefectRecord defect = new MesDefectRecord();
            defect.setDefectNo(orderNoGenerator.nextDefectNo());
            defect.setInspectionRecordId(record.getId());
            defect.setInspectionTaskId(task.getId());
            defect.setWorkOrderId(task.getWorkOrderId());
            defect.setOperationTaskId(task.getOperationTaskId());
            defect.setDefectCode(item.getDefectCode());
            defect.setDefectQty(item.getDefectQty());
            defect.setRemark(item.getRemark());
            defectRecordMapper.insert(defect);
            traceService.write(task.getWorkOrderId(), task.getOperationTaskId(), ActionType.DEFECT,
                    Map.of("defectNo", defect.getDefectNo(), "defectCode", defect.getDefectCode(),
                            "defectQty", defect.getDefectQty()));
        }
        return record.getId();
    }
}
