package com.smartfactory.mes.ai.controller;

import com.smartfactory.mes.ai.dto.AiAskRequest;
import com.smartfactory.mes.ai.dto.AiAskVO;
import com.smartfactory.mes.ai.dto.AiFeedbackRequest;
import com.smartfactory.mes.ai.dto.KnowledgeDocQueryDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocSaveDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.ai.dto.KnowledgeDocVO;
import com.smartfactory.mes.ai.enums.AiIntent;
import com.smartfactory.mes.ai.service.KnowledgeService;
import com.smartfactory.mes.ai.sse.SseSupport;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 工厂知识库接口（第 4 周：文档维护 + SOP 问答 + 问答反馈）
 *
 * <p>权限：查询/问答/反馈全员可用（工人查 SOP 是核心场景），文档写仅 admin。
 * 第 7 周：ask/stream 流式端点返回裸 SseEmitter（见 {@link SseSupport}）。</p>
 */
@RestController
@RequestMapping("/ai/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final TaskExecutor aiExecutor;
    private final ObjectMapper objectMapper;

    public KnowledgeController(KnowledgeService knowledgeService,
                               @Qualifier("aiExecutor") TaskExecutor aiExecutor,
                               ObjectMapper objectMapper) {
        this.knowledgeService = knowledgeService;
        this.aiExecutor = aiExecutor;
        this.objectMapper = objectMapper;
    }

    /** 文档分页 */
    @RequirePermission("ai:knowledge:query")
    @GetMapping("/docs/page")
    public ApiResult<PageResult<KnowledgeDocVO>> page(@Valid KnowledgeDocQueryDTO query) {
        return ApiResult.success(knowledgeService.page(query));
    }

    /** 文档详情 */
    @RequirePermission("ai:knowledge:query")
    @GetMapping("/docs/{id}")
    public ApiResult<KnowledgeDocVO> get(@PathVariable Long id) {
        return ApiResult.success(knowledgeService.getDetail(id));
    }

    /** 新增文档 */
    @RequirePermission("ai:knowledge:create")
    @PostMapping("/docs")
    public ApiResult<Long> create(@Valid @RequestBody KnowledgeDocSaveDTO dto) {
        return ApiResult.success(knowledgeService.create(dto));
    }

    /** 编辑文档 */
    @RequirePermission("ai:knowledge:update")
    @PutMapping("/docs/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody KnowledgeDocSaveDTO dto) {
        knowledgeService.update(id, dto);
        return ApiResult.success();
    }

    /** 知识库问答（RAG：召回 → 生成 → 引用） */
    @RequirePermission("ai:knowledge:query")
    @PostMapping("/ask")
    public ApiResult<AiAskVO> ask(@Valid @RequestBody AiAskRequest request) {
        return ApiResult.success(knowledgeService.ask(request.getQuestion().trim()));
    }

    /** 流式问答：meta → delta* → done{recordId,answer,references,fallback}（事件序契约见 verify-t7-2） */
    @RequirePermission("ai:knowledge:query")
    @PostMapping("/ask/stream")
    public SseEmitter askStream(@Valid @RequestBody AiAskRequest request) {
        return SseSupport.start(aiExecutor, objectMapper, sink -> {
            sink.sendMeta(Map.of("intent", AiIntent.KNOWLEDGE.getCode()));
            AiAskVO vo = knowledgeService.askStream(request.getQuestion().trim(), sink);
            if (vo != null) {
                sink.sendDone(Map.of(
                        "recordId", vo.getRecordId(),
                        "intent", AiIntent.KNOWLEDGE.getCode(),
                        "answer", vo.getAnswer(),
                        "references", vo.getReferences(),
                        "fallback", Boolean.TRUE.equals(vo.getFallback())));
            }
        });
    }

    /** 问答反馈（有用/无用，覆盖回填） */
    @RequirePermission("ai:knowledge:query")
    @PutMapping("/qa-records/{id}/feedback")
    public ApiResult<Void> feedback(@PathVariable Long id, @Valid @RequestBody AiFeedbackRequest request) {
        knowledgeService.feedback(id, request.getUseful());
        return ApiResult.success();
    }
}
