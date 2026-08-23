package com.smartfactory.mes.ai.controller;

import com.smartfactory.mes.ai.dto.AiAskRequest;
import com.smartfactory.mes.ai.dto.AiAskVO;
import com.smartfactory.mes.ai.dto.AiFeedbackRequest;
import com.smartfactory.mes.ai.dto.KnowledgeDocQueryDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocSaveDTO;
import com.smartfactory.mes.ai.dto.KnowledgeDocVO;
import com.smartfactory.mes.ai.service.KnowledgeService;
import com.smartfactory.mes.auth.RequirePermission;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工厂知识库接口（第 4 周：文档维护 + SOP 问答 + 问答反馈）
 *
 * <p>权限：查询/问答/反馈全员可用（工人查 SOP 是核心场景），文档写仅 admin。</p>
 */
@RestController
@RequestMapping("/ai/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
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

    /** 问答反馈（有用/无用，覆盖回填） */
    @RequirePermission("ai:knowledge:query")
    @PutMapping("/qa-records/{id}/feedback")
    public ApiResult<Void> feedback(@PathVariable Long id, @Valid @RequestBody AiFeedbackRequest request) {
        knowledgeService.feedback(id, request.getUseful());
        return ApiResult.success();
    }
}
