package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.LoginUser;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.integration.erp.service.ErpOrderService;
import com.smartfactory.mes.integration.wms.service.WmsService;
import com.smartfactory.mes.master.entity.MesMaterial;
import com.smartfactory.mes.master.service.MaterialService;
import com.smartfactory.mes.production.dto.MaterialBatchBindDTO;
import com.smartfactory.mes.production.dto.WorkReportSaveDTO;
import com.smartfactory.mes.production.entity.MesMaterialBatch;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesProductSn;
import com.smartfactory.mes.production.entity.MesReportMaterialBatch;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.entity.MesWorkReport;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.enums.TaskStatus;
import com.smartfactory.mes.production.mapper.MesMaterialBatchMapper;
import com.smartfactory.mes.production.mapper.MesOperationTaskMapper;
import com.smartfactory.mes.production.mapper.MesProductSnMapper;
import com.smartfactory.mes.production.mapper.MesReportMaterialBatchMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.mapper.MesWorkReportMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.quality.service.InspectionTaskService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报工 9 步大事务单测（第 8 周）：校验链 → CAS 累加 → 落库 → 质检任务生成 →
 * 末工序回写 → SN 铸号 → ERP/WMS 完工钩子。全部走 Mockito 纯单测（无 Spring 上下文 / 无 DB），
 * 逐层钉死「合格+不良=报工数」「前道合格上限」「钩子异常降级」等生产口径。
 */
@ExtendWith(MockitoExtension.class)
class WorkReportServiceImplTest {

    private static final long TASK_ID = 10L;
    private static final long WO_ID = 1L;

    @Mock
    private MesOperationTaskMapper operationTaskMapper;
    @Mock
    private MesWorkOrderMapper workOrderMapper;
    @Mock
    private MesProductSnMapper productSnMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private OrderNoGenerator orderNoGenerator;
    @Mock
    private TraceService traceService;
    @Mock
    private InspectionTaskService inspectionTaskService;
    @Mock
    private ErpOrderService erpOrderService;
    @Mock
    private WmsService wmsService;
    @Mock
    private MaterialService materialService;
    @Mock
    private MesMaterialBatchMapper materialBatchMapper;
    @Mock
    private MesReportMaterialBatchMapper reportMaterialBatchMapper;
    /** 经 ServiceImpl.baseMapper 字段按类型注入（this.save 的落库通道） */
    @Mock
    private MesWorkReportMapper workReportMapper;

    @InjectMocks
    private WorkReportServiceImpl service;

    @BeforeEach
    void setUp() {
        // CrudRepository.baseMapper 是私有超类字段，@InjectMocks 注入不到，显式反射注入（this.save 的落库通道）
        ReflectionTestUtils.setField(service, "baseMapper", workReportMapper);
        // ⑦⑨ 用 LambdaUpdateWrapper<MesWorkOrder>.set(类型化 lambda)，纯单测无 Spring 不会初始化
        // TableInfo → lambda 列缓存为空直接抛异常；手工 init（幂等，MyBatis-Plus 官方单测同款做法）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MesWorkOrder.class);
        CurrentUserContext.set(new LoginUser(7L, "operator", "操作工"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private static MesOperationTask task(int sequenceNo, TaskStatus status) {
        MesOperationTask t = new MesOperationTask();
        t.setId(TASK_ID);
        t.setWorkOrderId(WO_ID);
        t.setSequenceNo(sequenceNo);
        t.setStatus(status);
        t.setPlanQty(10);
        // 前道合格校验会读累计值，未报工时三值均 0（测试内按场景覆盖）
        t.setGoodQty(0);
        t.setCompletedQty(0);
        t.setDefectQty(0);
        return t;
    }

    private static WorkReportSaveDTO dto(int reportQty, int good, int defect) {
        WorkReportSaveDTO dto = new WorkReportSaveDTO();
        dto.setTaskId(TASK_ID);
        dto.setReportQty(reportQty);
        dto.setGoodQty(good);
        dto.setDefectQty(defect);
        return dto;
    }

    private static MesWorkOrder wo() {
        MesWorkOrder wo = new MesWorkOrder();
        wo.setId(WO_ID);
        wo.setProductId(3L);
        wo.setProductCodeSnapshot("AOC-55");
        wo.setProductNameSnapshot("AOC 55寸");
        return wo;
    }

    @Test
    void taskNotFoundThrows() {
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.report(dto(5, 5, 0)));
    }

    @Test
    void taskNotRunningThrows() {
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(1, TaskStatus.PAUSED));
        assertThrows(BusinessException.class, () -> service.report(dto(5, 5, 0)));
    }

    @Test
    void reportQtyNotPositiveThrows() {
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(1, TaskStatus.RUNNING));
        assertThrows(BusinessException.class, () -> service.report(dto(0, 0, 0)));
    }

    @Test
    void goodPlusDefectNotEqualToReportQtyThrows() {
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(1, TaskStatus.RUNNING));
        assertThrows(BusinessException.class, () -> service.report(dto(5, 3, 1)));
    }

    @Test
    void previousProcessGoodQtyExceededThrows() {
        // 第 2 道工序：本工序累计合格 + 本次合格 > 前道累计合格 → 拒绝（992 口径）
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(2, TaskStatus.RUNNING));
        MesOperationTask prev = task(1, TaskStatus.COMPLETED);
        prev.setId(9L);
        prev.setGoodQty(0);
        when(operationTaskMapper.selectOne(any())).thenReturn(prev);
        assertThrows(BusinessException.class, () -> service.report(dto(5, 5, 0)));
    }

    @Test
    void casUpdateZeroRowsThrows() {
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(1, TaskStatus.RUNNING));
        when(operationTaskMapper.update(any(), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.report(dto(5, 5, 0)));
        verify(workReportMapper, never()).insert(any(MesWorkReport.class));
    }

    @Test
    void simpleHappyPathSavesReportWithCurrentUser() {
        MesOperationTask prev = task(1, TaskStatus.COMPLETED);
        prev.setId(9L);
        prev.setGoodQty(5);
        when(operationTaskMapper.selectOne(any())).thenReturn(prev, null); // 前道 → lastSeq
        when(operationTaskMapper.update(any(), any())).thenReturn(1);
        MesOperationTask fresh = task(2, TaskStatus.RUNNING);
        fresh.setCompletedQty(5);
        fresh.setGoodQty(5);
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(2, TaskStatus.RUNNING), fresh);
        when(orderNoGenerator.nextReportNo()).thenReturn("RPT202608230001");

        service.report(dto(5, 5, 0));

        ArgumentCaptor<MesWorkReport> captor = ArgumentCaptor.forClass(MesWorkReport.class);
        verify(workReportMapper).insert(captor.capture());
        MesWorkReport report = captor.getValue();
        assertEquals("RPT202608230001", report.getReportNo());
        assertEquals(WO_ID, report.getWorkOrderId());
        assertEquals(TASK_ID, report.getTaskId());
        assertEquals(5, report.getReportQty());
        assertEquals(7L, report.getOperatorId()); // CurrentUserContext 生效
        verify(traceService).write(eq(WO_ID), eq(TASK_ID), eq(ActionType.REPORT), any());
        verify(inspectionTaskService, never()).generateFromCompletedTask(any(), any());
        verify(orderNoGenerator, never()).nextSnBatch(anyInt());
        verify(erpOrderService, never()).markDoneByExternalOrderNo(anyString());
        verify(wmsService, never()).finishedIn(any(), anyInt(), anyString());
    }

    @Test
    void completedTaskWithInspectionGeneratesInspectionTask() {
        MesOperationTask prev = task(1, TaskStatus.COMPLETED);
        prev.setId(9L);
        prev.setGoodQty(2);
        when(operationTaskMapper.selectOne(any())).thenReturn(prev, null); // 前道 → lastSeq
        when(operationTaskMapper.update(any(), any())).thenReturn(1);
        MesOperationTask fresh = task(2, TaskStatus.COMPLETED);
        fresh.setCompletedQty(2);
        fresh.setGoodQty(2);
        fresh.setNeedInspection(true);
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(2, TaskStatus.RUNNING), fresh);
        when(orderNoGenerator.nextReportNo()).thenReturn("RPT202608230001");

        // lastSeq=null → ⑦⑧⑨ 整块跳过，只验证 ⑥ 质检任务生成
        service.report(dto(2, 2, 0));

        verify(inspectionTaskService).generateFromCompletedTask(WO_ID, fresh);
    }

    @Test
    void lastProcessCompletionCastsSnAndFlipsWorkOrder() {
        MesOperationTask fresh = task(1, TaskStatus.COMPLETED);
        fresh.setCompletedQty(5);
        fresh.setGoodQty(5);
        when(operationTaskMapper.selectOne(any())).thenReturn(fresh); // lastSeq = 本任务（末工序）
        when(operationTaskMapper.update(any(), any())).thenReturn(1);
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(1, TaskStatus.RUNNING), fresh);
        when(orderNoGenerator.nextReportNo()).thenReturn("RPT202608230001");
        when(orderNoGenerator.nextSnBatch(5)).thenReturn(List.of("SN1", "SN2", "SN3", "SN4", "SN5"));
        when(workOrderMapper.selectById(WO_ID)).thenReturn(wo());
        when(workOrderMapper.update(any(), any())).thenReturn(1, 1); // 回写 → CAS 翻转

        service.report(dto(5, 5, 0));

        verify(orderNoGenerator).nextSnBatch(5);
        verify(productSnMapper, times(5)).insert(any(MesProductSn.class));
        verify(workOrderMapper, times(2)).update(any(), any());
        // 非 ERP 外部工单：钩子不触发
        verify(erpOrderService, never()).markDoneByExternalOrderNo(anyString());
        verify(wmsService, never()).finishedIn(any(), anyInt(), anyString());
    }

    @Test
    void materialBatchBindingWritesBindRecordAndTrace() {
        MesOperationTask fresh = task(1, TaskStatus.RUNNING);
        fresh.setCompletedQty(2);
        fresh.setGoodQty(2);
        when(operationTaskMapper.selectOne(any())).thenReturn(fresh); // lastSeq = 本任务（末工序回写）
        when(operationTaskMapper.update(any(), any())).thenReturn(1);
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(1, TaskStatus.RUNNING), fresh);
        when(workOrderMapper.update(any(), any())).thenReturn(1);
        when(orderNoGenerator.nextReportNo()).thenReturn("RPT202608230001");

        MesMaterialBatch batch = new MesMaterialBatch();
        batch.setId(9L);
        batch.setBatchNo("MB1");
        batch.setMaterialId(9L);
        batch.setUsedQty(0);
        when(materialBatchMapper.selectOne(any())).thenReturn(batch);
        MesMaterial material = new MesMaterial();
        material.setMaterialCode("M-001");
        material.setMaterialName("主板");
        material.setTraceRequired(true);
        when(materialService.getById(9L)).thenReturn(material);
        when(reportMaterialBatchMapper.selectOne(any())).thenReturn(null);
        when(reportMaterialBatchMapper.insert(any(MesReportMaterialBatch.class))).thenReturn(1);
        when(materialBatchMapper.update(any(), any())).thenReturn(1);

        MaterialBatchBindDTO bind = new MaterialBatchBindDTO();
        bind.setMaterialId(9L);
        bind.setBatchNo("MB1");
        WorkReportSaveDTO dto = dto(2, 2, 0);
        dto.setMaterialBatchBindings(List.of(bind));
        service.report(dto);

        verify(reportMaterialBatchMapper).insert(any(MesReportMaterialBatch.class));
        verify(materialBatchMapper).update(any(), any());
        verify(traceService).write(eq(WO_ID), eq(TASK_ID), eq(ActionType.BATCH_BIND), any());
    }

    @Test
    void erpExternalWorkOrderTriggersDoneAndWmsFinishedIn() {
        MesOperationTask fresh = task(1, TaskStatus.COMPLETED);
        fresh.setCompletedQty(5);
        fresh.setGoodQty(5);
        when(operationTaskMapper.selectOne(any())).thenReturn(fresh);
        when(operationTaskMapper.update(any(), any())).thenReturn(1);
        when(operationTaskMapper.selectById(TASK_ID)).thenReturn(task(1, TaskStatus.RUNNING), fresh);
        when(orderNoGenerator.nextReportNo()).thenReturn("RPT202608230001");
        when(orderNoGenerator.nextSnBatch(5)).thenReturn(List.of("SN1", "SN2", "SN3", "SN4", "SN5"));
        MesWorkOrder wo = wo();
        wo.setExternalOrderNo("ERP-001");
        when(workOrderMapper.selectById(WO_ID)).thenReturn(wo, wo); // SN 铸号 → 钩子
        when(workOrderMapper.update(any(), any())).thenReturn(1, 1);
        when(erpOrderService.isExternalWorkOrder(WO_ID)).thenReturn(true);
        when(orderNoGenerator.next("STK")).thenReturn("STK1");

        service.report(dto(5, 5, 0));

        verify(erpOrderService).markDoneByExternalOrderNo("ERP-001");
        verify(traceService).write(eq(WO_ID), eq(TASK_ID), eq(ActionType.ERP_DONE), any());
        verify(wmsService).finishedIn(WO_ID, 5, "STK1");
        verify(traceService).write(eq(WO_ID), eq(TASK_ID), eq(ActionType.WMS_FINISHED_IN), any());
    }
}
