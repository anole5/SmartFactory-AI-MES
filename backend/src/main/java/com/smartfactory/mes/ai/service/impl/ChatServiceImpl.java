package com.smartfactory.mes.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfactory.mes.ai.client.DeepSeekClient;
import com.smartfactory.mes.ai.dto.ActiveWorkOrderRow;
import com.smartfactory.mes.ai.dto.AiAskVO;
import com.smartfactory.mes.ai.dto.AiChatVO;
import com.smartfactory.mes.ai.dto.DailyPreviewVO;
import com.smartfactory.mes.ai.dto.EquipmentStatusRow;
import com.smartfactory.mes.ai.dto.ExceptionSuggestionVO;
import com.smartfactory.mes.ai.entity.MesAiQaRecord;
import com.smartfactory.mes.ai.enums.AiIntent;
import com.smartfactory.mes.ai.exception.AiServiceException;
import com.smartfactory.mes.ai.mapper.AiQaRecordMapper;
import com.smartfactory.mes.ai.mapper.DailyReportMapper;
import com.smartfactory.mes.ai.service.AssistantService;
import com.smartfactory.mes.ai.service.ChatService;
import com.smartfactory.mes.ai.service.DailyReportService;
import com.smartfactory.mes.ai.service.KnowledgeService;
import com.smartfactory.mes.quality.entity.MesExceptionOrder;
import com.smartfactory.mes.quality.mapper.MesExceptionOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 统一 AI 助手实现（演示核心：一个输入框问全局）
 *
 * <p>意图路由两段式（面试可讲）：
 * ① 规则关键词前置——确定性高、零 token 成本、毫秒级；
 * ② 规则未命中 → flash 档 LLM 兜底分类；分类失败再降级 KNOWLEDGE。
 * 四类意图分发到既有服务：OVERVIEW 生产概况综合（pro）/ KNOWLEDGE 知识库 RAG（flash）/
 * EXCEPTION 异常建议（pro）/ REPORT 生产日报（flash）。
 * 每次对话落 mes_ai_qa_record（intent 列留痕路由结果）。</p>
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** 异常单号格式：EXP + yyyyMMdd + 4 位流水 */
    private static final Pattern EXCEPTION_NO = Pattern.compile("EXP\\d{12}");

    private static final String CLASSIFY_SYSTEM = "你是意图分类器。把用户问题分为四类之一："
            + "OVERVIEW（生产概况/产量/工单/设备状态）、KNOWLEDGE（知识库/SOP/工艺规范）、"
            + "EXCEPTION（异常/故障处理）、REPORT（生产日报/今日总结）。只输出类别单词，不要解释。";

    private static final String OVERVIEW_SYSTEM = "你是智能电视工厂的生产管理助手。"
            + "根据以下工厂实时数据回答用户问题，中文，简洁分点。数据没有的内容不要编造。";

    private final KnowledgeService knowledgeService;
    private final AssistantService assistantService;
    private final DailyReportService dailyReportService;
    private final DailyReportMapper dailyReportMapper;
    private final MesExceptionOrderMapper exceptionOrderMapper;
    private final AiQaRecordMapper qaRecordMapper;
    private final DeepSeekClient deepSeekClient;

    public ChatServiceImpl(KnowledgeService knowledgeService, AssistantService assistantService,
                           DailyReportService dailyReportService, DailyReportMapper dailyReportMapper,
                           MesExceptionOrderMapper exceptionOrderMapper, AiQaRecordMapper qaRecordMapper,
                           DeepSeekClient deepSeekClient) {
        this.knowledgeService = knowledgeService;
        this.assistantService = assistantService;
        this.dailyReportService = dailyReportService;
        this.dailyReportMapper = dailyReportMapper;
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.qaRecordMapper = qaRecordMapper;
        this.deepSeekClient = deepSeekClient;
    }

    @Override
    public AiChatVO chat(String question) {
        AiIntent intent = detectIntent(question);
        log.info("AI 助手意图路由: {} -> {}", question, intent.getCode());
        switch (intent) {
            case REPORT:
                return routeReport(question);
            case EXCEPTION:
                return routeException(question);
            case OVERVIEW:
                return routeOverview(question);
            default:
                return routeKnowledge(question);
        }
    }

    // ------------------------------------------------------------
    // 意图识别
    // ------------------------------------------------------------

    private AiIntent detectIntent(String question) {
        AiIntent rule = ruleIntent(question);
        if (rule != null) {
            return rule;
        }
        // 规则未命中 → flash 档 LLM 分类兜底；分类失败降级 KNOWLEDGE（知识库兜底话术不白屏）
        try {
            String result = deepSeekClient.chatFast(CLASSIFY_SYSTEM, question).trim().toUpperCase();
            for (AiIntent intent : AiIntent.values()) {
                if (result.contains(intent.getCode())) {
                    return intent;
                }
            }
            return AiIntent.KNOWLEDGE;
        } catch (AiServiceException e) {
            return AiIntent.KNOWLEDGE;
        }
    }

    /** 规则关键词前置（确定性、免 token） */
    private AiIntent ruleIntent(String question) {
        if (containsAny(question, "日报", "生产报告", "今日总结")) {
            return AiIntent.REPORT;
        }
        if (containsAny(question, "异常", "故障", "黑屏", "花屏", "不良", "烧录失败", "BURN_FAIL") || EXCEPTION_NO.matcher(question).find()) {
            return AiIntent.EXCEPTION;
        }
        if (containsAny(question, "产量", "产能", "工单", "订单", "设备", "概况", "全局", "整体", "今天", "今日", "现在", "目前")) {
            return AiIntent.OVERVIEW;
        }
        if (containsAny(question, "怎么", "如何", "流程", "步骤", "指导", "SOP", "规范", "标准", "为什么", "什么原因")) {
            return AiIntent.KNOWLEDGE;
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------
    // 四类路由
    // ------------------------------------------------------------

    /** 知识库：复用 RAG 问答（ask 内部已落问答记录，不再重复保存） */
    private AiChatVO routeKnowledge(String question) {
        AiAskVO ask = knowledgeService.ask(question);
        AiChatVO vo = new AiChatVO();
        vo.setIntent(AiIntent.KNOWLEDGE.getCode());
        vo.setAnswer(ask.getAnswer());
        vo.setReferences(ask.getReferences());
        vo.setFallback(ask.getFallback());
        vo.setRecordId(ask.getRecordId());
        return vo;
    }

    /** 异常：识别出异常单号 → 建议助手 pro 推理；无单号 → 转知识库 FAULT_GUIDE 检索 */
    private AiChatVO routeException(String question) {
        Matcher matcher = EXCEPTION_NO.matcher(question);
        if (matcher.find()) {
            MesExceptionOrder order = exceptionOrderMapper.selectOne(new LambdaQueryWrapper<MesExceptionOrder>()
                    .eq(MesExceptionOrder::getExceptionNo, matcher.group()));
            if (order != null) {
                ExceptionSuggestionVO suggestion = assistantService.suggest(order.getId());
                AiChatVO vo = new AiChatVO();
                vo.setIntent(AiIntent.EXCEPTION.getCode());
                vo.setAnswer(suggestion.getSuggestion());
                vo.setFallback(suggestion.getFallback());
                vo.setExceptionId(order.getId());
                vo.setRecordId(saveRecord(question, suggestion.getSuggestion(), AiIntent.EXCEPTION));
                return vo;
            }
        }
        // 无单号：问题多半是"黑屏怎么办"类，知识库 FAULT_GUIDE 命中即答
        return routeKnowledge(question);
    }

    /** 日报：当日数据聚合 + flash 润色 */
    private AiChatVO routeReport(String question) {
        DailyPreviewVO preview = dailyReportService.preview(LocalDate.now());
        AiChatVO vo = new AiChatVO();
        vo.setIntent(AiIntent.REPORT.getCode());
        vo.setAnswer(preview.getContent());
        vo.setSummary(preview.getSummary());
        vo.setReportDate(preview.getReportDate());
        vo.setFallback(preview.getFallback());
        vo.setRecordId(saveRecord(question, preview.getContent(), AiIntent.REPORT));
        return vo;
    }

    /** 概况：实时数据聚合 + pro 综合分析 */
    private AiChatVO routeOverview(String question) {
        String summary = buildOverviewSummary();
        String answer;
        boolean fallback;
        try {
            answer = deepSeekClient.chatPro(OVERVIEW_SYSTEM, "用户问题：" + question + "\n\n工厂实时数据：\n" + summary);
            fallback = false;
        } catch (AiServiceException e) {
            answer = "【模板回答】AI 服务暂不可用，以下为工厂实时数据：\n\n" + summary;
            fallback = true;
        }
        AiChatVO vo = new AiChatVO();
        vo.setIntent(AiIntent.OVERVIEW.getCode());
        vo.setAnswer(answer);
        vo.setSummary(summary);
        vo.setFallback(fallback);
        vo.setRecordId(saveRecord(question, answer, AiIntent.OVERVIEW));
        return vo;
    }

    // ------------------------------------------------------------
    // 私有工具
    // ------------------------------------------------------------

    private String buildOverviewSummary() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
        long good = dailyReportMapper.sumGood(start, end);
        long defect = dailyReportMapper.sumDefect(start, end);
        long reportCount = dailyReportMapper.countReport(start, end);
        long openException = dailyReportMapper.openExceptionCount();
        List<ActiveWorkOrderRow> orders = dailyReportMapper.listActiveWorkOrders();
        List<EquipmentStatusRow> equipment = dailyReportMapper.equipmentStatusCount();

        StringBuilder sb = new StringBuilder();
        sb.append("今日报工 ").append(reportCount).append(" 笔，合格 ").append(good)
                .append(" 台，不良 ").append(defect).append(" 台");
        if (good + defect > 0) {
            sb.append("，良率 ").append(String.format(Locale.ROOT, "%.1f%%", good * 100.0 / (good + defect)));
        }
        sb.append("\n进行中/已下发工单 ").append(orders.size()).append(" 个：");
        if (orders.isEmpty()) {
            sb.append("无");
        }
        for (ActiveWorkOrderRow o : orders) {
            sb.append("\n  ").append(o.getWorkOrderNo()).append(" ").append(o.getProductNameSnapshot())
                    .append(" 计划 ").append(o.getPlanQty()).append(" / 完成 ").append(o.getCompletedQty())
                    .append("（").append(o.getStatus()).append("）");
        }
        sb.append("\n未关闭异常 ").append(openException).append(" 个");
        sb.append("\n设备状态：");
        sb.append(equipment.isEmpty() ? "无设备数据"
                : equipment.stream().map(r -> r.getStatus() + " " + r.getCnt() + " 台").collect(Collectors.joining("、")));
        return sb.toString();
    }

    private Long saveRecord(String question, String answer, AiIntent intent) {
        MesAiQaRecord record = new MesAiQaRecord();
        record.setQuestion(question);
        record.setAnswer(answer);
        record.setIntent(intent);
        qaRecordMapper.insert(record);
        return record.getId();
    }
}
