package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.master.entity.MesRoute;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工艺路线出参（列表不带 steps，详情带）
 */
@Getter
@Setter
public class RouteVO {

    private Long id;
    private String routeNo;
    private Long productId;
    private String productCode;
    private String productName;
    private String version;
    private String status;
    private String remark;
    private List<RouteStepVO> steps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RouteVO of(MesRoute entity) {
        RouteVO vo = new RouteVO();
        vo.setId(entity.getId());
        vo.setRouteNo(entity.getRouteNo());
        vo.setProductId(entity.getProductId());
        vo.setVersion(entity.getVersion());
        vo.setStatus(entity.getStatus().getCode());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
