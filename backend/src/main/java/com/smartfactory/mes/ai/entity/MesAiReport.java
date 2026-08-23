package com.smartfactory.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 生产日报（AI 生成，草稿编辑后可保存；同一 report_date 幂等覆盖）
 */
@Getter
@Setter
@TableName("mes_ai_report")
public class MesAiReport extends BaseEntity {

    /** 报告日期 */
    private LocalDate reportDate;

    /** 报告正文（AI 润色后的日报内容） */
    private String content;
}
