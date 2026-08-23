package com.smartfactory.mes.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.ai.client.DeepSeekClient;
import com.smartfactory.mes.ai.dto.WeeklyPreviewVO;
import com.smartfactory.mes.ai.dto.WeeklyReportRow;
import com.smartfactory.mes.ai.entity.MesAiReport;
import com.smartfactory.mes.ai.exception.AiServiceException;
import com.smartfactory.mes.ai.mapper.AiReportMapper;
import com.smartfactory.mes.ai.mapper.WeeklyReportMapper;
import com.smartfactory.mes.ai.service.WeeklyReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 周报服务实现
 *
 * <p>管线：近两周逐日报工聚合 → 趋势摘要（本周/上周合计 + 环比）→ pro 档生成周报正文。
 * LLM 故障降级：统计摘要直出（fallback=true），演示永不白屏。</p>
 *
 * <p>窗口口径（与 sql/14-seed-week7.sql 注释对齐）：截止日期 E，本周 = E-7..E-1，
 * 上周 = E-14..E-8。E 当天不纳入——种子数据不种今天（今天报工留给冒烟自己的数据），
 * 纳入会出现"本周末一天无报工"的干扰行。</p>
 */
@Service
public class WeeklyReportServiceImpl extends ServiceImpl<AiReportMapper, MesAiReport>
        implements WeeklyReportService {

    private static final String REPORT_TYPE_WEEK = "WEEK";

    private static final String SYSTEM_PROMPT = "你是智能电视工厂的生产周报分析助手。"
            + "请根据以下统计数据生成一段简洁的周度生产报告，"
            + "包括本周产量与质量概况、与上周的环比变化（报工数/合格数/良率）、趋势判断与下周关注建议，"
            + "中文，250 字以内，直接输出正文。";

    private final WeeklyReportMapper weeklyReportMapper;
    private final DeepSeekClient deepSeekClient;

    public WeeklyReportServiceImpl(WeeklyReportMapper weeklyReportMapper, DeepSeekClient deepSeekClient) {
        this.weeklyReportMapper = weeklyReportMapper;
        this.deepSeekClient = deepSeekClient;
    }

    @Override
    public WeeklyPreviewVO preview(LocalDate endDate) {
        // 一次查询覆盖两周窗口 [E-14 00:00, E 00:00)，服务层按周切分
        LocalDateTime start = endDate.minusDays(14).atStartOfDay();
        LocalDateTime end = endDate.atStartOfDay();
        List<WeeklyReportRow> rows = weeklyReportMapper.dailyAgg(start, end);

        String summary = buildSummary(endDate, rows);

        String content;
        boolean fallback;
        try {
            content = deepSeekClient.chatPro(SYSTEM_PROMPT, summary);
            fallback = false;
        } catch (AiServiceException e) {
            content = "【模板周报】AI 服务暂不可用，以下为统计数据：\n\n" + summary;
            fallback = true;
        }

        WeeklyPreviewVO vo = new WeeklyPreviewVO();
        vo.setEndDate(endDate);
        vo.setContent(content);
        vo.setSummary(summary);
        vo.setFallback(fallback);
        return vo;
    }

    @Override
    @Transactional
    public void save(LocalDate endDate, String content) {
        MesAiReport existing = this.getOne(new LambdaQueryWrapper<MesAiReport>()
                .eq(MesAiReport::getReportDate, endDate)
                .eq(MesAiReport::getReportType, REPORT_TYPE_WEEK), false);
        if (existing != null) {
            existing.setContent(content);
            this.updateById(existing);
        } else {
            MesAiReport entity = new MesAiReport();
            entity.setReportDate(endDate);
            entity.setReportType(REPORT_TYPE_WEEK);
            entity.setContent(content);
            this.save(entity);
        }
    }

    // ------------------------------------------------------------
    // 私有工具
    // ------------------------------------------------------------

    private String buildSummary(LocalDate endDate, List<WeeklyReportRow> rows) {
        Map<LocalDate, WeeklyReportRow> byDate = rows.stream()
                .collect(Collectors.toMap(WeeklyReportRow::getReportDate, r -> r, (a, b) -> a));
        LocalDate thisStart = endDate.minusDays(7);
        LocalDate thisEnd = endDate.minusDays(1);
        LocalDate lastStart = endDate.minusDays(14);
        LocalDate lastEnd = endDate.minusDays(8);

        StringBuilder sb = new StringBuilder();
        sb.append("截止日期：").append(endDate).append('\n');
        sb.append("【本周】").append(thisStart).append(" 至 ").append(thisEnd).append('\n');
        for (int i = 0; i < 7; i++) {
            LocalDate d = thisStart.plusDays(i);
            sb.append(formatDay(d, byDate.get(d))).append('\n');
        }
        long[] thisTotals = totals(byDate, thisStart, thisEnd);
        long[] lastTotals = totals(byDate, lastStart, lastEnd);
        sb.append("本周合计：报工 ").append(thisTotals[2]).append(" 笔，合格 ").append(thisTotals[0])
                .append(" 台，不良 ").append(thisTotals[1]).append(" 台，良率 ")
                .append(yieldOf(thisTotals[0], thisTotals[1])).append('\n');
        sb.append("【上周】").append(lastStart).append(" 至 ").append(lastEnd).append('\n');
        sb.append("上周合计：报工 ").append(lastTotals[2]).append(" 笔，合格 ").append(lastTotals[0])
                .append(" 台，不良 ").append(lastTotals[1]).append(" 台，良率 ")
                .append(yieldOf(lastTotals[0], lastTotals[1])).append('\n');
        sb.append("环比：");
        if (lastTotals[2] == 0 && lastTotals[0] + lastTotals[1] == 0) {
            sb.append("无上期数据，无法计算环比");
        } else {
            sb.append("报工数 ").append(rateChange(thisTotals[2], lastTotals[2]))
                    .append("，合格数 ").append(rateChange(thisTotals[0], lastTotals[0]))
                    .append("，良率 ").append(yieldChange(thisTotals, lastTotals));
        }
        return sb.toString();
    }

    /** 逐日行：MM-dd：报工/合格/不良/良率；无数据日标"无报工" */
    private String formatDay(LocalDate d, WeeklyReportRow row) {
        String prefix = String.format(Locale.ROOT, "%02d-%02d：", d.getMonthValue(), d.getDayOfMonth());
        if (row == null) {
            return prefix + "无报工";
        }
        return prefix + String.format(Locale.ROOT, "报工 %d 笔，合格 %d 台，不良 %d 台，良率 %s",
                row.getReportCount(), row.getGoodQty(), row.getDefectQty(),
                yieldOf(row.getGoodQty(), row.getDefectQty()));
    }

    /** 窗口内合计：{good, defect, count} */
    private long[] totals(Map<LocalDate, WeeklyReportRow> byDate, LocalDate start, LocalDate end) {
        long good = 0, defect = 0, count = 0;
        for (Map.Entry<LocalDate, WeeklyReportRow> e : byDate.entrySet()) {
            if (!e.getKey().isBefore(start) && !e.getKey().isAfter(end)) {
                good += e.getValue().getGoodQty();
                defect += e.getValue().getDefectQty();
                count += e.getValue().getReportCount();
            }
        }
        return new long[]{good, defect, count};
    }

    /** 良率格式化（yield 是 Java 受限标识符，方法名取 yieldOf） */
    private String yieldOf(long good, long defect) {
        return (good + defect) == 0 ? "无报工数据"
                : String.format(Locale.ROOT, "%.1f%%", good * 100.0 / (good + defect));
    }

    /** 数量环比（带符号百分比） */
    private String rateChange(long cur, long prev) {
        if (prev == 0) {
            return "上期为 0";
        }
        return String.format(Locale.ROOT, "%+.1f%%", (cur - prev) * 100.0 / prev);
    }

    /** 良率环比（带符号百分点） */
    private String yieldChange(long[] cur, long[] prev) {
        if (prev[0] + prev[1] == 0) {
            return "上期无报工";
        }
        double c = cur[0] * 100.0 / (cur[0] + cur[1]);
        double p = prev[0] * 100.0 / (prev[0] + prev[1]);
        return String.format(Locale.ROOT, "%+.1f 个百分点", c - p);
    }
}
