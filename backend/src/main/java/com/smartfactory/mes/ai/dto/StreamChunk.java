package com.smartfactory.mes.ai.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 大模型流式输出分块（SSE 打字机的最小数据单元）
 */
@Getter
@Setter
public class StreamChunk {

    /** 增量回答文本（delta.content；reasoning_content 不上送——推理过程不展示给用户） */
    private String content;

    public StreamChunk(String content) {
        this.content = content;
    }
}
