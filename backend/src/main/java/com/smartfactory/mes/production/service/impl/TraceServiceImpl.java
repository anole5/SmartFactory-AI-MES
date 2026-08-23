package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.api.ResultCode;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.dto.BatchSnItemVO;
import com.smartfactory.mes.production.dto.BatchSnTraceVO;
import com.smartfactory.mes.production.dto.BatchTraceVO;
import com.smartfactory.mes.production.dto.MaterialBatchUsageVO;
import com.smartfactory.mes.production.dto.SnTraceVO;
import com.smartfactory.mes.production.dto.TraceRecordVO;
import com.smartfactory.mes.production.dto.WorkOrderVO;
import com.smartfactory.mes.production.dto.WorkReportVO;
import com.smartfactory.mes.production.entity.MesMaterialBatch;
import com.smartfactory.mes.production.entity.MesProductSn;
import com.smartfactory.mes.production.entity.MesReportMaterialBatch;
import com.smartfactory.mes.production.entity.MesTraceRecord;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.entity.MesWorkReport;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.mapper.MesMaterialBatchMapper;
import com.smartfactory.mes.production.mapper.MesProductSnMapper;
import com.smartfactory.mes.production.mapper.MesReportMaterialBatchMapper;
import com.smartfactory.mes.production.mapper.MesTraceRecordMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.mapper.MesWorkReportMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.production.service.WorkReportService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 追溯记录服务实现：单号 + 当前操作人统一回填，业务层只管传动作与明细
 */
@Service
public class TraceServiceImpl implements TraceService {

    private final MesTraceRecordMapper traceRecordMapper;
    private final MesProductSnMapper productSnMapper;
    private final MesWorkOrderMapper workOrderMapper;
    private final MesWorkReportMapper workReportMapper;
    private final SysUserMapper sysUserMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final ObjectMapper objectMapper;
    private final WorkReportService workReportService;
    private final MesMaterialBatchMapper materialBatchMapper;
    private final MesReportMaterialBatchMapper reportMaterialBatchMapper;

    public TraceServiceImpl(MesTraceRecordMapper traceRecordMapper,
                            MesProductSnMapper productSnMapper,
                            MesWorkOrderMapper workOrderMapper,
                            MesWorkReportMapper workReportMapper,
                            SysUserMapper sysUserMapper,
                            OrderNoGenerator orderNoGenerator,
                            ObjectMapper objectMapper,
                            // 同模块双向依赖：报工写追溯（WorkReport → Trace），
                            // 批次追溯复用报工列表（Trace → WorkReport），@Lazy 打破构造环
                            @Lazy WorkReportService workReportService,
                            MesMaterialBatchMapper materialBatchMapper,
                            MesReportMaterialBatchMapper reportMaterialBatchMapper) {
        this.traceRecordMapper = traceRecordMapper;
        this.productSnMapper = productSnMapper;
        this.workOrderMapper = workOrderMapper;
        this.workReportMapper = workReportMapper;
        this.sysUserMapper = sysUserMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.objectMapper = objectMapper;
        this.workReportService = workReportService;
        this.materialBatchMapper = materialBatchMapper;
        this.reportMaterialBatchMapper = reportMaterialBatchMapper;
    }

    @Override
    public void write(Long workOrderId, Long taskId, ActionType actionType, Object detail) {
        MesTraceRecord record = new MesTraceRecord();
        record.setTraceNo(orderNoGenerator.nextTraceNo());
        record.setWorkOrderId(workOrderId);
        record.setTaskId(taskId);
        record.setActionType(actionType);
        record.setActionTime(LocalDateTime.now());
        // 操作人来自登录拦截器放入的 ThreadLocal（非登录场景为 0）
        record.setOperatorId(CurrentUserContext.getUserIdOrZero());
        record.setActionDetail(toJson(detail));
        traceRecordMapper.insert(record);
    }

    @Override
    public List<TraceRecordVO> listByWorkOrder(Long workOrderId) {
        List<MesTraceRecord> records = traceRecordMapper.selectList(new LambdaQueryWrapper<MesTraceRecord>()
                .eq(MesTraceRecord::getWorkOrderId, workOrderId)
                .orderByAsc(MesTraceRecord::getActionTime)
                .orderByAsc(MesTraceRecord::getId));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        // 操作人名称一次批量查全（时间线不分页，条数可控）
        Set<Long> operatorIds = records.stream().map(MesTraceRecord::getOperatorId).collect(Collectors.toSet());
        Map<Long, SysUser> operators = sysUserMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return records.stream().map(r -> {
            TraceRecordVO vo = TraceRecordVO.of(r);
            SysUser operator = operators.get(r.getOperatorId());
            if (operator != null) {
                vo.setOperatorName(operator.getRealName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public SnTraceVO snTrace(String sn) {
        MesProductSn snRow = productSnMapper.selectOne(new LambdaQueryWrapper<MesProductSn>()
                .eq(MesProductSn::getSn, sn));
        if (snRow == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SN 不存在: " + sn);
        }
        SnTraceVO vo = new SnTraceVO();
        vo.setId(snRow.getId());
        vo.setSn(snRow.getSn());
        vo.setWorkOrderId(snRow.getWorkOrderId());
        vo.setProductCodeSnapshot(snRow.getProductCodeSnapshot());
        vo.setProductNameSnapshot(snRow.getProductNameSnapshot());
        vo.setReportId(snRow.getReportId());
        vo.setCreatedAt(snRow.getCreatedAt());
        MesWorkOrder wo = workOrderMapper.selectById(snRow.getWorkOrderId());
        if (wo != null) {
            vo.setWorkOrderNo(wo.getWorkOrderNo());
            vo.setWorkOrderStatus(wo.getStatus().getCode());
        }
        MesWorkReport report = snRow.getReportId() == null ? null : workReportMapper.selectById(snRow.getReportId());
        if (report != null) {
            vo.setReportNo(report.getReportNo());
        }
        vo.setTimeline(listByWorkOrder(snRow.getWorkOrderId()));
        // 第 6 周：出生工单全部报工的批次绑定行，按物料+批次聚合去重（13 道报工 → 每关键件 1 行，qtyUsed 求和）
        vo.setMaterialBatches(listBatchUsageByWorkOrder(snRow.getWorkOrderId()));
        return vo;
    }

    @Override
    public BatchSnTraceVO batchSnTrace(String batchNo) {
        MesMaterialBatch batch = materialBatchMapper.selectOne(new LambdaQueryWrapper<MesMaterialBatch>()
                .eq(MesMaterialBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "物料批次不存在: batchNo=" + batchNo);
        }
        BatchSnTraceVO vo = new BatchSnTraceVO();
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setMaterialId(batch.getMaterialId());
        vo.setMaterialCodeSnapshot(batch.getMaterialCodeSnapshot());
        vo.setMaterialNameSnapshot(batch.getMaterialNameSnapshot());
        vo.setBatchQty(batch.getBatchQty());
        vo.setUsedQty(batch.getUsedQty());
        vo.setInDate(batch.getInDate());
        vo.setSupplier(batch.getSupplier());
        // 绑定记录（报工号回填）
        List<MesReportMaterialBatch> binds = reportMaterialBatchMapper.selectList(
                new LambdaQueryWrapper<MesReportMaterialBatch>()
                        .eq(MesReportMaterialBatch::getBatchId, batch.getId())
                        .orderByAsc(MesReportMaterialBatch::getId));
        Set<Long> reportIds = binds.stream().map(MesReportMaterialBatch::getReportId).collect(Collectors.toSet());
        Map<Long, MesWorkReport> reports = reportIds.isEmpty() ? Collections.emptyMap()
                : workReportMapper.selectBatchIds(reportIds).stream()
                .collect(Collectors.toMap(MesWorkReport::getId, Function.identity()));
        vo.setBindings(binds.stream().map(b -> {
            MaterialBatchUsageVO usage = new MaterialBatchUsageVO();
            usage.setReportId(b.getReportId());
            MesWorkReport r = reports.get(b.getReportId());
            usage.setReportNo(r == null ? null : r.getReportNo());
            usage.setBatchId(b.getBatchId());
            usage.setBatchNo(b.getBatchNoSnapshot());
            usage.setMaterialId(b.getMaterialId());
            usage.setMaterialCodeSnapshot(b.getMaterialCodeSnapshot());
            usage.setMaterialNameSnapshot(b.getMaterialNameSnapshot());
            usage.setQtyUsed(b.getQtyUsed());
            usage.setCreatedAt(b.getCreatedAt());
            return usage;
        }).collect(Collectors.toList()));
        // 工单去重
        Set<Long> workOrderIds = binds.stream().map(MesReportMaterialBatch::getWorkOrderId).collect(Collectors.toSet());
        List<WorkOrderVO> workOrders = workOrderIds.isEmpty() ? Collections.emptyList()
                : workOrderMapper.selectBatchIds(workOrderIds).stream()
                .sorted(Comparator.comparing(MesWorkOrder::getId))
                .map(WorkOrderVO::of)
                .collect(Collectors.toList());
        vo.setWorkOrders(workOrders);
        // 这些工单铸出的整机 SN（id 升序，工单号回填）
        List<MesProductSn> sns = workOrderIds.isEmpty() ? Collections.emptyList()
                : productSnMapper.selectList(new LambdaQueryWrapper<MesProductSn>()
                .in(MesProductSn::getWorkOrderId, workOrderIds)
                .orderByAsc(MesProductSn::getId));
        Map<Long, String> workOrderNos = workOrders.stream()
                .collect(Collectors.toMap(WorkOrderVO::getId, WorkOrderVO::getWorkOrderNo));
        vo.setSns(sns.stream().map(s -> {
            BatchSnItemVO item = new BatchSnItemVO();
            item.setId(s.getId());
            item.setSn(s.getSn());
            item.setWorkOrderId(s.getWorkOrderId());
            item.setWorkOrderNo(workOrderNos.get(s.getWorkOrderId()));
            item.setProductNameSnapshot(s.getProductNameSnapshot());
            item.setCreatedAt(s.getCreatedAt());
            return item;
        }).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 某工单全部报工的批次绑定行按物料+批次聚合去重（SN 追溯关键件批次区数据源）
     */
    private List<MaterialBatchUsageVO> listBatchUsageByWorkOrder(Long workOrderId) {
        List<MesReportMaterialBatch> binds = reportMaterialBatchMapper.selectList(
                new LambdaQueryWrapper<MesReportMaterialBatch>()
                        .eq(MesReportMaterialBatch::getWorkOrderId, workOrderId)
                        .orderByAsc(MesReportMaterialBatch::getId));
        if (binds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> reportIds = binds.stream().map(MesReportMaterialBatch::getReportId).collect(Collectors.toSet());
        Map<Long, MesWorkReport> reports = workReportMapper.selectBatchIds(reportIds).stream()
                .collect(Collectors.toMap(MesWorkReport::getId, Function.identity()));
        Map<String, MaterialBatchUsageVO> agg = new LinkedHashMap<>();
        for (MesReportMaterialBatch b : binds) {
            String key = b.getMaterialId() + "|" + b.getBatchNoSnapshot();
            MaterialBatchUsageVO usage = agg.computeIfAbsent(key, k -> {
                MaterialBatchUsageVO u = new MaterialBatchUsageVO();
                u.setReportId(b.getReportId());
                MesWorkReport r = reports.get(b.getReportId());
                u.setReportNo(r == null ? null : r.getReportNo());
                u.setBatchId(b.getBatchId());
                u.setBatchNo(b.getBatchNoSnapshot());
                u.setMaterialId(b.getMaterialId());
                u.setMaterialCodeSnapshot(b.getMaterialCodeSnapshot());
                u.setMaterialNameSnapshot(b.getMaterialNameSnapshot());
                u.setQtyUsed(0);
                u.setCreatedAt(b.getCreatedAt());
                return u;
            });
            usage.setQtyUsed(usage.getQtyUsed() + b.getQtyUsed());
        }
        return new ArrayList<>(agg.values());
    }

    @Override
    public BatchTraceVO batchTrace(String batchNo) {
        // 复用报工服务列表能力（构造注入 @Lazy 打破循环，见构造函数注释）
        List<WorkReportVO> reports = workReportService.listByBatchNo(batchNo);
        if (reports.isEmpty()) {
            return BatchTraceVO.empty();
        }
        // 批次内工单去重：同工单 13 条报工只出一行工单摘要
        Set<Long> workOrderIds = reports.stream().map(WorkReportVO::getWorkOrderId).collect(Collectors.toSet());
        List<WorkOrderVO> workOrders = workOrderMapper.selectBatchIds(workOrderIds).stream()
                .sorted(Comparator.comparing(MesWorkOrder::getId))
                .map(WorkOrderVO::of)
                .collect(Collectors.toList());
        BatchTraceVO vo = new BatchTraceVO();
        vo.setReports(reports);
        vo.setWorkOrders(workOrders);
        return vo;
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            // 明细都是简单 Map 结构，序列化失败说明调用方传了不可序列化对象——按编程错误抛出
            throw new BusinessException("追溯明细序列化失败: " + e.getMessage());
        }
    }
}
