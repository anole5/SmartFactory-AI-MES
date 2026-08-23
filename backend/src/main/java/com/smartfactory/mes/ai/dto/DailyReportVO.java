package com.smartfactory.mes.ai.dto;

import com.smartfactory.mes.ai.entity.MesAiReport;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产日报出参
 */
@Getter
@Setter
public class DailyReportVO {

    private Long id;
    private LocalDate reportDate;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DailyReportVO of(MesAiReport entity) {
        DailyReportVO vo = new DailyReportVO();
        vo.setId(entity.getId());
        vo.setReportDate(entity.getReportDate());
        vo.setContent(entity.getContent());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
