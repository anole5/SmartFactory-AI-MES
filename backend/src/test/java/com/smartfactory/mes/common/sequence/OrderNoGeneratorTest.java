package com.smartfactory.mes.common.sequence;

import com.smartfactory.mes.production.mapper.MesSequenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 单号生成器单测（第 8 周）：
 * 钉死「前缀+yyyyMMdd+%04d」格式与三步取号顺序（insertIgnoreToday → increment → lastInsertId），
 * 三步必须同事务同连接是生产口径（类注释坑 1），顺序错位会取到别人的自增值。
 */
@ExtendWith(MockitoExtension.class)
class OrderNoGeneratorTest {

    private static final String TODAY = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Mock
    private MesSequenceMapper mesSequenceMapper;

    @InjectMocks
    private OrderNoGenerator orderNoGenerator;

    @Test
    void nextReturnsPrefixPlusDatePlusFourDigits() {
        when(mesSequenceMapper.insertIgnoreToday("WO", TODAY, 1L)).thenReturn(1);
        when(mesSequenceMapper.increment("WO", TODAY, 1L)).thenReturn(1);
        when(mesSequenceMapper.lastInsertId()).thenReturn(42L);

        assertEquals("WO" + TODAY + "0042", orderNoGenerator.next("WO"));

        InOrder inOrder = inOrder(mesSequenceMapper);
        inOrder.verify(mesSequenceMapper).insertIgnoreToday("WO", TODAY, 1L);
        inOrder.verify(mesSequenceMapper).increment("WO", TODAY, 1L);
        inOrder.verify(mesSequenceMapper).lastInsertId();
    }

    @Test
    void fourDigitsArePaddedButNotTruncated() {
        when(mesSequenceMapper.lastInsertId()).thenReturn(1L);
        assertEquals("WO" + TODAY + "0001", orderNoGenerator.next("WO"));
        when(mesSequenceMapper.lastInsertId()).thenReturn(9999L);
        assertEquals("WO" + TODAY + "9999", orderNoGenerator.next("WO"));
        // %04d 超过 4 位不截断（日流水理论上限远大于 9999）
        when(mesSequenceMapper.lastInsertId()).thenReturn(10000L);
        assertEquals("WO" + TODAY + "10000", orderNoGenerator.next("WO"));
    }

    @Test
    void nextSnBatchReturnsConsecutiveRange() {
        when(mesSequenceMapper.insertIgnoreToday("SN", TODAY, 1L)).thenReturn(1);
        when(mesSequenceMapper.incrementBatch("SN", TODAY, 1L, 3)).thenReturn(1);
        when(mesSequenceMapper.lastInsertId()).thenReturn(100L);

        assertEquals(List.of("SN" + TODAY + "0098", "SN" + TODAY + "0099", "SN" + TODAY + "0100"),
                orderNoGenerator.nextSnBatch(3));

        InOrder inOrder = inOrder(mesSequenceMapper);
        inOrder.verify(mesSequenceMapper).insertIgnoreToday("SN", TODAY, 1L);
        inOrder.verify(mesSequenceMapper).incrementBatch("SN", TODAY, 1L, 3);
        inOrder.verify(mesSequenceMapper).lastInsertId();
    }

    @Test
    void allBusinessNoPrefixesDelegateToNext() {
        when(mesSequenceMapper.lastInsertId()).thenReturn(1L);
        assertEquals("WO" + TODAY + "0001", orderNoGenerator.nextWorkOrderNo());
        assertEquals("TASK" + TODAY + "0001", orderNoGenerator.nextTaskNo());
        assertEquals("RPT" + TODAY + "0001", orderNoGenerator.nextReportNo());
        assertEquals("TRC" + TODAY + "0001", orderNoGenerator.nextTraceNo());
        assertEquals("BOM" + TODAY + "0001", orderNoGenerator.nextBomNo());
        assertEquals("RT" + TODAY + "0001", orderNoGenerator.nextRouteNo());
        assertEquals("INP" + TODAY + "0001", orderNoGenerator.nextInspectionTaskNo());
        assertEquals("INS" + TODAY + "0001", orderNoGenerator.nextInspectionRecordNo());
        assertEquals("DEF" + TODAY + "0001", orderNoGenerator.nextDefectNo());
        assertEquals("EXP" + TODAY + "0001", orderNoGenerator.nextExceptionNo());
    }
}
