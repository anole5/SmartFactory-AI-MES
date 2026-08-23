package com.smartfactory.mes.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.ai.client.DeepSeekClient;
import com.smartfactory.mes.ai.dto.DailyPreviewVO;
import com.smartfactory.mes.ai.dto.DailyReportQueryDTO;
import com.smartfactory.mes.ai.dto.DailyReportVO;
import com.smartfactory.mes.ai.dto.EquipmentStatusRow;
import com.smartfactory.mes.ai.entity.MesAiReport;
import com.smartfactory.mes.ai.exception.AiServiceException;
import com.smartfactory.mes.ai.mapper.AiReportMapper;
import com.smartfactory.mes.ai.mapper.DailyReportMapper;
import com.smartfactory.mes.ai.service.DailyReportService;
import com.smartfactory.mes.ai.sse.StreamSink;
import com.smartfactory.mes.common.api.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 生产日报服务实现
 *
 * <p>管线：当日生产数据聚合（产量/质量/工单/异常/设备）→ 统计摘要 → flash 档润色生成日报正文。
 * LLM 故障降级：统计摘要直出（fallback=true），演示永不白屏。</p>
 */
@Service
public class DailyReportServiceImpl extends ServiceImpl<AiReportMapper, MesAiReport>
        implements DailyReportService {

    private static final String SYSTEM_PROMPT = "你是智能电视工厂的生产日报助手。"
            + "请根据以下统计数据生成一段简洁的当日生产日报，"
            + "包括产量、质量、设备、异常情况和明日关注建议，中文，200 字以内，直接输出正文。";

    private final DailyReportMapper dailyReportMapper;
    private final DeepSeekClient deepSeekClient;

    public DailyReportServiceImpl(DailyReportMapper dailyReportMapper, DeepSeekClient deepSeekClient) {
        this.dailyReportMapper = dailyReportMapper;
        this.deepSeekClient = deepSeekClient;
    }

    @Override
    public DailyPreviewVO preview(LocalDate reportDate) {
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end = reportDate.plusDays(1).atStartOfDay();

        long good = dailyReportMapper.sumGood(start, end);
        long defect = dailyReportMapper.sumDefect(start, end);
        long reportCount = dailyReportMapper.countReport(start, end);
        long exceptionCount = dailyReportMapper.countException(start, end);
        long inspectionCompleted = dailyReportMapper.countInspectionCompleted(start, end);
        long openException = dailyReportMapper.openExceptionCount();
        long activeWorkOrders = dailyReportMapper.activeWorkOrderCount();
        List<EquipmentStatusRow> equipment = dailyReportMapper.equipmentStatusCount();

        String summary = buildSummary(reportDate, good, defect, reportCount, exceptionCount,
                inspectionCompleted, openException, activeWorkOrders, equipment);

        String content;
        boolean fallback;
        try {
            content = deepSeekClient.chatFast(SYSTEM_PROMPT, summary);
            fallback = false;
        } catch (AiServiceException e) {
            content = "【模板日报】AI 服务暂不可用，以下为当日统计数据：\n\n" + summary;
            fallback = true;
        }

        DailyPreviewVO vo = new DailyPreviewVO();
        vo.setReportDate(reportDate);
        vo.setContent(content);
        vo.setSummary(summary);
        vo.setFallback(fallback);
        return vo;
    }

    @Override
    public DailyPreviewVO previewStream(LocalDate reportDate, StreamSink sink) {
        // 管线与 preview() 一致（聚合零改动），仅 flash 档润色换成流式：delta 逐块推给前端（打字机）。
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end = reportDate.plusDays(1).atStartOfDay();

        long good = dailyReportMapper.sumGood(start, end);
        long defect = dailyReportMapper.sumDefect(start, end);
        long reportCount = dailyReportMapper.countReport(start, end);
        long exceptionCount = dailyReportMapper.countException(start, end);
        long inspectionCompleted = dailyReportMapper.countInspectionCompleted(start, end);
        long openException = dailyReportMapper.openExceptionCount();
        long activeWorkOrders = dailyReportMapper.activeWorkOrderCount();
        List<EquipmentStatusRow> equipment = dailyReportMapper.equipmentStatusCount();

        String summary = buildSummary(reportDate, good, defect, reportCount, exceptionCount,
                inspectionCompleted, openException, activeWorkOrders, equipment);

        StringBuilder content = new StringBuilder();
        boolean fallback;
        try {
            deepSeekClient.chatFastStream(SYSTEM_PROMPT, summary,
                    chunk -> {
                        content.append(chunk.getContent());
                        sink.sendDelta(chunk.getContent());
                    });
            fallback = false;
        } catch (AiServiceException e) {
            if (sink.isCancelled()) {
                return null;
            }
            String text = "【模板日报】AI 服务暂不可用，以下为当日统计数据：\n\n" + summary;
            content.append(text);
            sink.sendDelta(text);
            fallback = true;
        }
        if (sink.isCancelled()) {
            return null;
        }

        DailyPreviewVO vo = new DailyPreviewVO();
        vo.setReportDate(reportDate);
        vo.setContent(content.toString());
        vo.setSummary(summary);
        vo.setFallback(fallback);
        return vo;
    }

    @Override
    @Transactional
    public void save(LocalDate reportDate, String content) {
        MesAiReport existing = this.getOne(new LambdaQueryWrapper<MesAiReport>()
                .eq(MesAiReport::getReportDate, reportDate), false);
        if (existing != null) {
            existing.setContent(content);
            this.updateById(existing);
        } else {
            MesAiReport entity = new MesAiReport();
            entity.setReportDate(reportDate);
            entity.setContent(content);
            this.save(entity);
        }
    }

    @Override
    public PageResult<DailyReportVO> page(DailyReportQueryDTO query) {
        LambdaQueryWrapper<MesAiReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getReportDate() != null, MesAiReport::getReportDate, query.getReportDate())
                .orderByDesc(MesAiReport::getReportDate);
        Page<MesAiReport> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<DailyReportVO> vos = page.getRecords().stream()
                .map(DailyReportVO::of).collect(Collectors.toList());
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ------------------------------------------------------------
    // 私有工具
    // ------------------------------------------------------------

    private String buildSummary(LocalDate date, long good, long defect, long reportCount, long exceptionCount,
                                long inspectionCompleted, long openException, long activeWorkOrders,
                                List<EquipmentStatusRow> equipment) {
        String yield = (good + defect) == 0 ? "无报工数据"
                : String.format(Locale.ROOT, "%.1f%%", good * 100.0 / (good + defect));
        String equipmentText = equipment.isEmpty() ? "无设备数据"
                : equipment.stream()
                .map(r -> r.getStatus() + " " + r.getCnt() + " 台")
                .collect(Collectors.joining("、"));
        return "日期：" + date + "\n"
                + "报工 " + reportCount + " 笔，合格 " + good + " 台，不良 " + defect + " 台，良率 " + yield + "\n"
                + "进行中工单 " + activeWorkOrders + " 个，今日完成质检任务 " + inspectionCompleted + " 个\n"
                + "今日新建异常 " + exceptionCount + " 个，未关闭异常 " + openException + " 个\n"
                + "设备状态：" + equipmentText;
    }
}
