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
import com.smartfactory.mes.common.api.ResultCode;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.integration.erp.service.ErpOrderService;
import com.smartfactory.mes.integration.wms.service.WmsService;
import com.smartfactory.mes.master.entity.MesMaterial;
import com.smartfactory.mes.master.service.MaterialService;
import com.smartfactory.mes.production.dto.MaterialBatchBindDTO;
import com.smartfactory.mes.production.dto.WorkReportQueryDTO;
import com.smartfactory.mes.production.dto.WorkReportSaveDTO;
import com.smartfactory.mes.production.dto.WorkReportVO;
import com.smartfactory.mes.production.entity.MesMaterialBatch;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesProductSn;
import com.smartfactory.mes.production.entity.MesReportMaterialBatch;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.entity.MesWorkReport;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.enums.TaskStatus;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import com.smartfactory.mes.production.mapper.MesMaterialBatchMapper;
import com.smartfactory.mes.production.mapper.MesOperationTaskMapper;
import com.smartfactory.mes.production.mapper.MesProductSnMapper;
import com.smartfactory.mes.production.mapper.MesReportMaterialBatchMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.mapper.MesWorkReportMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.production.service.WorkReportService;
import com.smartfactory.mes.quality.service.InspectionTaskService;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>需质检工序达 COMPLETED → 生成质检任务（第 3 周，同事务）</li>
 *   <li>最后一道工序：累计回写工单（完成数量 = 最后一道累计合格+不良），
 *       最后一道 COMPLETED → 工单 COMPLETED + 实际完工时间</li>
 *   <li>最后一道 COMPLETED 且合格&gt;0 → 批量生成整机 SN（第 3 周，同事务）</li>
 *   <li>第 5 周系统集成完工钩子：工单 CAS 翻转成功（flipped==1）且为 ERP 推单工单时，
 *       回传外部订单 DONE + 成品完工入库 + 写 ERP_DONE/WMS_FINISHED_IN 追溯；
 *       钩子异常吞掉降级（不回滚报工主事务），外部订单停留 SYNCED 可人工重试</li>
 * </ol>
 */
@Slf4j
@Service
public class WorkReportServiceImpl extends ServiceImpl<MesWorkReportMapper, MesWorkReport>
        implements WorkReportService {

    private final MesOperationTaskMapper operationTaskMapper;
    private final MesWorkOrderMapper workOrderMapper;
    private final MesProductSnMapper productSnMapper;
    private final SysUserMapper sysUserMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;
    private final InspectionTaskService inspectionTaskService;
    private final ErpOrderService erpOrderService;
    private final WmsService wmsService;
    private final MaterialService materialService;
    private final MesMaterialBatchMapper materialBatchMapper;
    private final MesReportMaterialBatchMapper reportMaterialBatchMapper;

    public WorkReportServiceImpl(MesOperationTaskMapper operationTaskMapper,
                                 MesWorkOrderMapper workOrderMapper,
                                 MesProductSnMapper productSnMapper,
                                 SysUserMapper sysUserMapper,
                                 OrderNoGenerator orderNoGenerator,
                                 TraceService traceService,
                                 InspectionTaskService inspectionTaskService,
                                 ErpOrderService erpOrderService,
                                 WmsService wmsService,
                                 MaterialService materialService,
                                 MesMaterialBatchMapper materialBatchMapper,
                                 MesReportMaterialBatchMapper reportMaterialBatchMapper) {
        this.operationTaskMapper = operationTaskMapper;
        this.workOrderMapper = workOrderMapper;
        this.productSnMapper = productSnMapper;
        this.sysUserMapper = sysUserMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
        this.inspectionTaskService = inspectionTaskService;
        this.erpOrderService = erpOrderService;
        this.wmsService = wmsService;
        this.materialService = materialService;
        this.materialBatchMapper = materialBatchMapper;
        this.reportMaterialBatchMapper = reportMaterialBatchMapper;
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
    public List<WorkReportVO> listByBatchNo(String batchNo) {
        List<MesWorkReport> reports = this.list(new LambdaQueryWrapper<MesWorkReport>()
                .eq(MesWorkReport::getProductBatchNo, batchNo)
                .orderByAsc(MesWorkReport::getId));
        return toVOs(reports);
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
        // ⑥ 需质检工序达 COMPLETED → 生成质检任务（第 3 周接入：同事务，失败随报工整单回滚）
        if (Boolean.TRUE.equals(fresh.getNeedInspection()) && fresh.getStatus() == TaskStatus.COMPLETED) {
            inspectionTaskService.generateFromCompletedTask(task.getWorkOrderId(), fresh);
        }
        traceService.write(task.getWorkOrderId(), dto.getTaskId(), ActionType.REPORT,
                Map.of("reportNo", report.getReportNo(), "reportQty", reportQty,
                        "goodQty", dto.getGoodQty(), "defectQty", dto.getDefectQty()));
        // ⑤.5（第 6 周）内嵌关键件批次绑定：校验失败抛 409 整单回滚（报工落库 + 绑定同事务）；
        // 旧调用方不传 materialBatchBindings 直接跳过，139 旧断言零影响
        if (dto.getMaterialBatchBindings() != null && !dto.getMaterialBatchBindings().isEmpty()) {
            bindBatchesInternal(report, dto.getMaterialBatchBindings());
        }
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
            int flipped = 0;
            if (fresh.getStatus() == TaskStatus.COMPLETED) {
                flipped = workOrderMapper.update(null, new LambdaUpdateWrapper<MesWorkOrder>()
                        .eq(MesWorkOrder::getId, task.getWorkOrderId())
                        .eq(MesWorkOrder::getStatus, WorkOrderStatus.IN_PROGRESS)
                        .set(MesWorkOrder::getStatus, WorkOrderStatus.COMPLETED)
                        .set(MesWorkOrder::getActualEndTime, now));
            }
            // ⑧（第 3 周）最后一道 COMPLETED 且合格>0 → 按合格数量批量生成整机 SN。
            // 守卫：部分报工（任务未 COMPLETED）不铸号，避免 SN 与实际完工台数不符；
            // 已完成任务重复报工在①已被拒，不会重复铸号。取号与插表同事务，回滚不留半截号。
            if (fresh.getStatus() == TaskStatus.COMPLETED && fresh.getGoodQty() > 0) {
                MesWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
                List<String> sns = orderNoGenerator.nextSnBatch(fresh.getGoodQty());
                for (String sn : sns) {
                    MesProductSn productSn = new MesProductSn();
                    productSn.setSn(sn);
                    productSn.setWorkOrderId(task.getWorkOrderId());
                    productSn.setProductId(wo.getProductId());
                    productSn.setProductCodeSnapshot(wo.getProductCodeSnapshot());
                    productSn.setProductNameSnapshot(wo.getProductNameSnapshot());
                    productSn.setReportId(report.getId());
                    productSnMapper.insert(productSn);
                }
            }
            // ⑨（第 5 周）系统集成完工钩子：仅本笔报工完成工单（CAS 翻转成功）才执行，
            // 且仅 ERP 推单工单触发（手建工单不写 ERP_DONE/WMS_FINISHED_IN 追溯，冒烟计数不破）。
            // 异常吞掉降级（不回滚报工主事务）：外部订单停留 SYNCED，成品入库失败可人工补录。
            if (flipped == 1) {
                try {
                    if (erpOrderService.isExternalWorkOrder(task.getWorkOrderId())) {
                        MesWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
                        erpOrderService.markDoneByExternalOrderNo(wo.getExternalOrderNo());
                        traceService.write(task.getWorkOrderId(), fresh.getId(), ActionType.ERP_DONE,
                                Map.of("externalOrderNo", wo.getExternalOrderNo()));
                        // 流水号在报工主事务内先取号传入：finishedIn 以 REQUIRES_NEW 独立事务执行，
                        // 若在事务内再取号会与主事务竞争 mes_sequence 行锁（锁等待超时，钩子必失败）
                        wmsService.finishedIn(task.getWorkOrderId(), fresh.getGoodQty(),
                                orderNoGenerator.next("STK"));
                        traceService.write(task.getWorkOrderId(), fresh.getId(), ActionType.WMS_FINISHED_IN,
                                Map.of("goodQty", fresh.getGoodQty()));
                    }
                } catch (Exception e) {
                    log.warn("系统集成完工钩子执行失败（不回滚报工）: workOrderId={}",
                            task.getWorkOrderId(), e);
                }
            }
        }
    }

    @Override
    @Transactional
    public void bindBatches(Long reportId, List<MaterialBatchBindDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("绑定列表不能为空");
        }
        MesWorkReport report = this.getById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报工记录不存在: id=" + reportId);
        }
        bindBatchesInternal(report, items);
    }

    /**
     * 关键件批次绑定核心（报工内嵌 + 补录两通道共用，调用方已开事务）：
     * ① 批次必须存在；② 批次物料必须与入参一致；③ 物料必须 trace_required=1；
     * ④ 同 (报工,物料,批次) 重放幂等跳过；⑤ 同 (报工,物料) 换批 409 拦截（绑定只增不改）。
     * 成功：插绑定行（快照回填）+ 批次 used_qty 按报工台数累加 + 写 BATCH_BIND 追溯。
     */
    private void bindBatchesInternal(MesWorkReport report, List<MaterialBatchBindDTO> items) {
        for (MaterialBatchBindDTO item : items) {
            MesMaterialBatch batch = materialBatchMapper.selectOne(new LambdaQueryWrapper<MesMaterialBatch>()
                    .eq(MesMaterialBatch::getBatchNo, item.getBatchNo()));
            if (batch == null) {
                throw new BusinessException("物料批次不存在: batchNo=" + item.getBatchNo());
            }
            if (!batch.getMaterialId().equals(item.getMaterialId())) {
                throw new BusinessException("批次与物料不匹配: batchNo=" + item.getBatchNo()
                        + ", materialId=" + item.getMaterialId());
            }
            MesMaterial material = materialService.getById(item.getMaterialId());
            if (material == null) {
                throw new BusinessException("物料不存在: id=" + item.getMaterialId());
            }
            if (!Boolean.TRUE.equals(material.getTraceRequired())) {
                throw new BusinessException("非关键件物料不需要批次绑定: " + material.getMaterialCode());
            }
            // 同报工同物料已绑过：同批次重放幂等跳过（补录接口可安全重试）；换批次 409
            MesReportMaterialBatch existing = reportMaterialBatchMapper.selectOne(
                    new LambdaQueryWrapper<MesReportMaterialBatch>()
                            .eq(MesReportMaterialBatch::getReportId, report.getId())
                            .eq(MesReportMaterialBatch::getMaterialId, item.getMaterialId()));
            if (existing != null) {
                if (existing.getBatchNoSnapshot().equals(batch.getBatchNo())) {
                    continue;
                }
                throw new BusinessException("该报工已绑定物料批次: " + material.getMaterialName()
                        + " (" + existing.getBatchNoSnapshot() + ")");
            }
            MesReportMaterialBatch bind = new MesReportMaterialBatch();
            bind.setReportId(report.getId());
            bind.setWorkOrderId(report.getWorkOrderId());
            bind.setMaterialId(material.getId());
            bind.setMaterialCodeSnapshot(material.getMaterialCode());
            bind.setMaterialNameSnapshot(material.getMaterialName());
            bind.setBatchId(batch.getId());
            bind.setBatchNoSnapshot(batch.getBatchNo());
            bind.setQtyUsed(report.getReportQty());
            reportMaterialBatchMapper.insert(bind);
            // 批次台账已用量按台数累加（展示口径）
            materialBatchMapper.update(null, new LambdaUpdateWrapper<MesMaterialBatch>()
                    .eq(MesMaterialBatch::getId, batch.getId())
                    .setSql("used_qty = used_qty + " + report.getReportQty()));
            traceService.write(report.getWorkOrderId(), report.getTaskId(), ActionType.BATCH_BIND,
                    Map.of("reportNo", report.getReportNo(), "materialCode", material.getMaterialCode(),
                            "batchNo", batch.getBatchNo(), "qtyUsed", report.getReportQty()));
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
