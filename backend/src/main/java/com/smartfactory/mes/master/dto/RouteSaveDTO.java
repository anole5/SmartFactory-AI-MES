package com.smartfactory.mes.master.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 工艺路线新增/编辑入参（头 + 步骤整单提交）
 */
@Getter
@Setter
public class RouteSaveDTO {

    @NotNull(message = "产品不能为空")
    private Long productId;

    @Size(max = 16, message = "版本号最长 16 位")
    private String version;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;

    @NotEmpty(message = "工艺步骤不能为空")
    @Valid
    private List<RouteStepDTO> steps;

    /**
     * 工艺步骤入参（processId 引用工序主数据，快照字段服务端回填）
     */
    @Getter
    @Setter
    public static class RouteStepDTO {

        @NotNull(message = "工序不能为空")
        private Long processId;

        /** 默认工位（可空） */
        private Long workstationId;

        /** 本步是否质检；不传则继承工序主数据的设置 */
        private Boolean needInspection;

        @Size(max = 255, message = "备注最长 255 位")
        private String remark;
    }
}
