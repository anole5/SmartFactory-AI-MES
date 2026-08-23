package com.smartfactory.mes.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfactory.mes.ai.client.DeepSeekClient;
import com.smartfactory.mes.ai.dto.ExceptionSuggestionVO;
import com.smartfactory.mes.ai.entity.MesKnowledgeDoc;
import com.smartfactory.mes.ai.enums.KnowledgeDocStatus;
import com.smartfactory.mes.ai.enums.KnowledgeDocType;
import com.smartfactory.mes.ai.exception.AiServiceException;
import com.smartfactory.mes.ai.mapper.KnowledgeDocMapper;
import com.smartfactory.mes.ai.service.AssistantService;
import com.smartfactory.mes.ai.sse.StreamSink;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.quality.entity.MesExceptionOrder;
import com.smartfactory.mes.quality.mapper.MesExceptionOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 异常处理建议助手实现
 *
 * <p>管线：异常单上下文 + 知识库召回（不良代码/描述关键词匹配 FAULT_GUIDE 文档）
 * → pro 档 LLM 推理 → 建议。保存走 admin/qa 权限，回写异常单 ai_suggestion 列并写追溯。
 * LLM 故障降级：按不良代码模板建议（演示永不白屏）。</p>
 */
@Service
public class AssistantServiceImpl implements AssistantService {

    private static final String SYSTEM_PROMPT = "你是智能电视工厂的异常处理专家。"
            + "结合提供的知识库文档和异常单信息，分析可能原因，给出排查步骤和处理建议。"
            + "分点输出，中文。文档没有的内容不要编造。";

    /** 不良代码 → 模板建议（LLM 不可用时的降级输出） */
    private static final Map<String, String> DEFECT_TEMPLATES = Map.of(
            "BLACK_SCREEN", "排查方向：背光驱动、电源板、LVDS 排线、T-CON 逻辑板。步骤：确认电源指示灯→测背光供电→查排线是否松动→更换 T-CON 板交叉验证。",
            "FLOWER_SCREEN", "排查方向：屏线接触、逻辑板、主板信号。步骤：重插屏线→检查逻辑板供电→更换主板交叉验证→确认屏体是否受损。",
            "NO_SOUND", "排查方向：功放板、喇叭、音频排线、软件静音设置。步骤：检查静音设置→测功放供电→查喇叭排线→更换功放板验证。",
            "HDMI_ABNORMAL", "排查方向：HDMI 接口、EDID 信息、主板 HDMI 通道。步骤：更换信号源与线材→清洁接口→检查 EDID 读取→更换主板验证。",
            "BURN_FAIL", "排查方向：烧录环境、固件包、写码器连接。步骤：核对软件版本与固件包→检查写码器与串口连接→重试烧录→更换主板存储验证。",
            "AGING_RESTART", "排查方向：电源稳定性、主板散热、老化电源老化。步骤：观察重启时机→测老化架电压→检查散热与温度→更换电源板验证。",
            "ACCESSORY_MISSING", "排查方向：包装工序、装箱清单执行。步骤：核对装箱清单→检查包装工位物料→补装并复核→对同批次开箱抽检。");

    private final MesExceptionOrderMapper exceptionOrderMapper;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final DeepSeekClient deepSeekClient;
    private final TraceService traceService;

    public AssistantServiceImpl(MesExceptionOrderMapper exceptionOrderMapper, KnowledgeDocMapper knowledgeDocMapper,
                                DeepSeekClient deepSeekClient, TraceService traceService) {
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.deepSeekClient = deepSeekClient;
        this.traceService = traceService;
    }

    @Override
    public ExceptionSuggestionVO suggest(Long exceptionId) {
        MesExceptionOrder order = mustExist(exceptionId);
        String context = buildSuggestionContext(order);

        String suggestion;
        boolean fallback;
        try {
            suggestion = deepSeekClient.chatPro(SYSTEM_PROMPT, context);
            fallback = false;
        } catch (AiServiceException e) {
            suggestion = templateByDefectCode(order.getDefectCode());
            fallback = true;
        }

        ExceptionSuggestionVO vo = new ExceptionSuggestionVO();
        vo.setExceptionId(order.getId());
        vo.setExceptionNo(order.getExceptionNo());
        vo.setSuggestion(suggestion);
        vo.setFallback(fallback);
        return vo;
    }

    @Override
    public ExceptionSuggestionVO suggestStream(Long exceptionId, StreamSink sink) {
        // 管线与 suggest() 一致，仅 pro 档推理换成流式：delta 逐块推给前端（打字机）。
        MesExceptionOrder order = mustExist(exceptionId);
        String context = buildSuggestionContext(order);

        StringBuilder suggestion = new StringBuilder();
        boolean fallback;
        try {
            deepSeekClient.chatProStream(SYSTEM_PROMPT, context,
                    chunk -> {
                        suggestion.append(chunk.getContent());
                        sink.sendDelta(chunk.getContent());
                    });
            fallback = false;
        } catch (AiServiceException e) {
            if (sink.isCancelled()) {
                return null;
            }
            String text = templateByDefectCode(order.getDefectCode());
            suggestion.append(text);
            sink.sendDelta(text);
            fallback = true;
        }
        if (sink.isCancelled()) {
            return null;
        }

        ExceptionSuggestionVO vo = new ExceptionSuggestionVO();
        vo.setExceptionId(order.getId());
        vo.setExceptionNo(order.getExceptionNo());
        vo.setSuggestion(suggestion.toString());
        vo.setFallback(fallback);
        return vo;
    }

    @Override
    @Transactional
    public void save(Long exceptionId, String suggestion) {
        MesExceptionOrder order = mustExist(exceptionId);
        order.setAiSuggestion(suggestion);
        exceptionOrderMapper.updateById(order);
        // 追溯表 work_order_id 非空约束：仅关联工单的异常写追溯
        if (order.getWorkOrderId() != null) {
            traceService.write(order.getWorkOrderId(), order.getOperationTaskId(), ActionType.AI_SUGGEST,
                    Map.of("exceptionNo", order.getExceptionNo()));
        }
    }

    @Override
    public ExceptionSuggestionVO getSuggestion(Long exceptionId) {
        MesExceptionOrder order = mustExist(exceptionId);
        ExceptionSuggestionVO vo = new ExceptionSuggestionVO();
        vo.setExceptionId(order.getId());
        vo.setExceptionNo(order.getExceptionNo());
        vo.setSuggestion(order.getAiSuggestion());
        return vo;
    }

    // ------------------------------------------------------------
    // 私有工具
    // ------------------------------------------------------------

    /** 召回 + 拼建议上下文（suggest 与 suggestStream 共用） */
    private String buildSuggestionContext(MesExceptionOrder order) {
        // 知识库召回：不良代码/描述关键词匹配（FAULT_GUIDE 文档优先）
        String queryText = String.valueOf(order.getDefectCode() == null ? "" : order.getDefectCode())
                + " " + (order.getDescription() == null ? "" : order.getDescription());
        List<MesKnowledgeDoc> docs = knowledgeDocMapper.selectList(new LambdaQueryWrapper<MesKnowledgeDoc>()
                .eq(MesKnowledgeDoc::getStatus, KnowledgeDocStatus.ENABLED)).stream()
                .filter(doc -> StringUtils.hasText(doc.getKeywords()) && hitKeywords(doc, queryText) > 0)
                .sorted((a, b) -> {
                    // FAULT_GUIDE 文档优先，其次按关键词命中数降序
                    int fa = a.getDocType() == KnowledgeDocType.FAULT_GUIDE ? 1 : 0;
                    int fb = b.getDocType() == KnowledgeDocType.FAULT_GUIDE ? 1 : 0;
                    int cmp = Integer.compare(fb, fa);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Integer.compare(hitKeywords(b, queryText), hitKeywords(a, queryText));
                })
                .limit(2)
                .collect(Collectors.toList());

        // 拼上下文：异常单信息 + 命中文档
        StringBuilder context = new StringBuilder("异常单信息：\n")
                .append("异常单号：").append(order.getExceptionNo()).append("\n")
                .append("不良代码：").append(order.getDefectCode() == null ? "无" : order.getDefectCode()).append("\n")
                .append("异常描述：").append(order.getDescription() == null ? "无" : order.getDescription())
                .append("\n\n知识库文档：\n");
        if (docs.isEmpty()) {
            context.append("（无命中文档）");
        }
        for (MesKnowledgeDoc doc : docs) {
            String content = doc.getContent();
            if (content != null && content.length() > 1200) {
                content = content.substring(0, 1200);
            }
            context.append("【文档：").append(doc.getDocName()).append("】\n").append(content).append("\n\n");
        }
        return context.toString();
    }

    private MesExceptionOrder mustExist(Long id) {
        MesExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("异常单不存在: id=" + id);
        }
        return order;
    }

    /** 查询文本命中文档关键词的次数 */
    private int hitKeywords(MesKnowledgeDoc doc, String queryText) {
        int score = 0;
        for (String kw : doc.getKeywords().split("[,，]")) {
            if (StringUtils.hasText(kw) && queryText.toLowerCase().contains(kw.trim().toLowerCase())) {
                score++;
            }
        }
        return score;
    }

    /** 不良代码模板降级建议 */
    private String templateByDefectCode(String defectCode) {
        String template = DEFECT_TEMPLATES.get(defectCode);
        return "【模板建议】" + (template != null ? template
                : "按通用流程排查：确认异常复现条件→检查物料与工艺参数→隔离问题单元→记录结论并跟进效果。");
    }
}
