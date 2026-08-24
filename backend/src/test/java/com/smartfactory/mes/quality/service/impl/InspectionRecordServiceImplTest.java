package com.smartfactory.mes.quality.service.impl;

import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.LoginUser;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检验录入校验链单测（第 8 周）：状态机校验 → 数量校验 → 不良明细合计 → CAS 累加 → 落库 + 追溯。
 * 不良 DEF 记录只来自检验录入（报工 defect_qty 只是数字），这条闭环在缺陷明细路径钉死。
 */
@ExtendWith(MockitoExtension.class)
class InspectionRecordServiceImplTest {

    @Mock
    private MesInspectionTaskMapper inspectionTaskMapper;
    @Mock
    private MesDefectRecordMapper defectRecordMapper;
    @Mock
    private OrderNoGenerator orderNoGenerator;
    @Mock
    private TraceService traceService;
    /** 经 ServiceImpl.baseMapper 字段按类型注入（this.save 的落库通道） */
    @Mock
    private MesInspectionRecordMapper inspectionRecordMapper;

    @InjectMocks
    private InspectionRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        // CrudRepository.baseMapper 是私有超类字段，@InjectMocks 注入不到，显式反射注入（this.save 的落库通道）
        ReflectionTestUtils.setField(service, "baseMapper", inspectionRecordMapper);
        CurrentUserContext.set(new LoginUser(1L, "qa", "质检员"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private static MesInspectionTask task(InspectionTaskStatus status) {
        MesInspectionTask t = new MesInspectionTask();
        t.setId(10L);
        t.setWorkOrderId(100L);
        t.setOperationTaskId(200L);
        t.setStatus(status);
        return t;
    }

    private static InspectionRecordSaveDTO dto(int good, int defect, List<InspectionRecordSaveDTO.DefectItem> items) {
        InspectionRecordSaveDTO dto = new InspectionRecordSaveDTO();
        dto.setInspectionTaskId(10L);
        dto.setGoodQty(good);
        dto.setDefectQty(defect);
        dto.setDefectItems(items);
        return dto;
    }

    private static InspectionRecordSaveDTO.DefectItem item(String code, int qty) {
        InspectionRecordSaveDTO.DefectItem item = new InspectionRecordSaveDTO.DefectItem();
        item.setDefectCode(code);
        item.setDefectQty(qty);
        return item;
    }

    @Test
    void taskNotFoundThrows() {
        when(inspectionTaskMapper.selectById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.create(dto(1, 0, null)));
    }

    @Test
    void taskNotInspectingThrows() {
        when(inspectionTaskMapper.selectById(10L)).thenReturn(task(InspectionTaskStatus.PENDING));
        assertThrows(BusinessException.class, () -> service.create(dto(1, 0, null)));
    }

    @Test
    void zeroQuantityThrows() {
        when(inspectionTaskMapper.selectById(10L)).thenReturn(task(InspectionTaskStatus.INSPECTING));
        assertThrows(BusinessException.class, () -> service.create(dto(0, 0, null)));
    }

    @Test
    void defectItemsSumMismatchThrows() {
        when(inspectionTaskMapper.selectById(10L)).thenReturn(task(InspectionTaskStatus.INSPECTING));
        // 不良数 2，明细行合计 1 → 拒绝（明细合计必须等于不良数量）
        assertThrows(BusinessException.class, () -> service.create(dto(0, 2, List.of(item("D01", 1)))));
    }

    @Test
    void casUpdateZeroRowsThrows() {
        when(inspectionTaskMapper.selectById(10L)).thenReturn(task(InspectionTaskStatus.INSPECTING));
        when(inspectionTaskMapper.update(any(), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.create(dto(1, 0, null)));
        verify(inspectionRecordMapper, never()).insert(any(MesInspectionRecord.class));
    }

    @Test
    void happyPathSavesRecordAndTrace() {
        when(inspectionTaskMapper.selectById(10L)).thenReturn(task(InspectionTaskStatus.INSPECTING));
        when(inspectionTaskMapper.update(any(), any())).thenReturn(1);
        when(orderNoGenerator.nextInspectionRecordNo()).thenReturn("INS202608230001");

        service.create(dto(8, 0, null));

        ArgumentCaptor<MesInspectionRecord> captor = ArgumentCaptor.forClass(MesInspectionRecord.class);
        verify(inspectionRecordMapper).insert(captor.capture());
        MesInspectionRecord record = captor.getValue();
        assertEquals("INS202608230001", record.getInspectionRecordNo());
        assertEquals(10L, record.getInspectionTaskId());
        assertEquals(100L, record.getWorkOrderId());
        assertEquals(200L, record.getOperationTaskId());
        assertEquals(8, record.getGoodQty());
        assertEquals(0, record.getDefectQty());
        assertEquals(1L, record.getInspectorId()); // CurrentUserContext 生效
        verify(traceService).write(eq(100L), eq(200L), eq(ActionType.INSPECT), any());
        verify(defectRecordMapper, never()).insert(any(MesDefectRecord.class));
    }

    @Test
    void defectItemsInsertDefectRecordsPerLine() {
        when(inspectionTaskMapper.selectById(10L)).thenReturn(task(InspectionTaskStatus.INSPECTING));
        when(inspectionTaskMapper.update(any(), any())).thenReturn(1);
        when(orderNoGenerator.nextInspectionRecordNo()).thenReturn("INS202608230001");
        when(orderNoGenerator.nextDefectNo()).thenReturn("DEF202608230001", "DEF202608230002");

        service.create(dto(6, 2, List.of(item("D01", 1), item("D02", 1))));

        verify(defectRecordMapper, times(2)).insert(any(MesDefectRecord.class));
        verify(traceService, times(1)).write(eq(100L), eq(200L), eq(ActionType.INSPECT), any());
        verify(traceService, times(2)).write(eq(100L), eq(200L), eq(ActionType.DEFECT), any());
    }

    @Test
    void noLoginUserFallsBackToZeroInspector() {
        CurrentUserContext.clear();
        when(inspectionTaskMapper.selectById(10L)).thenReturn(task(InspectionTaskStatus.INSPECTING));
        when(inspectionTaskMapper.update(any(), any())).thenReturn(1);
        when(orderNoGenerator.nextInspectionRecordNo()).thenReturn("INS202608230001");

        service.create(dto(8, 0, null));

        ArgumentCaptor<MesInspectionRecord> captor = ArgumentCaptor.forClass(MesInspectionRecord.class);
        verify(inspectionRecordMapper).insert(captor.capture());
        assertEquals(0L, captor.getValue().getInspectorId());
    }
}
