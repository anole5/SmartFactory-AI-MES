package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.master.entity.MesBom;
import com.smartfactory.mes.master.entity.MesProduct;
import com.smartfactory.mes.master.entity.MesRoute;
import com.smartfactory.mes.master.enums.BomStatus;
import com.smartfactory.mes.master.enums.ProductStatus;
import com.smartfactory.mes.master.enums.RouteStatus;
import com.smartfactory.mes.master.mapper.BomMapper;
import com.smartfactory.mes.master.mapper.ProductMapper;
import com.smartfactory.mes.master.mapper.RouteMapper;
import com.smartfactory.mes.production.dto.WorkOrderQueryDTO;
import com.smartfactory.mes.production.dto.WorkOrderSaveDTO;
import com.smartfactory.mes.production.dto.WorkOrderVO;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.enums.OrderPriority;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.production.service.WorkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 生产工单服务实现
 *
 * <p>创建链路（面试可讲）：产品 ENABLED 校验 → 自动解析产品下 ACTIVE 的 BOM/工艺路线
 * （工单与生效版本绑定，杜绝「工单指向草稿 BOM」）→ 快照固化 → 单号生成 → CREATE 追溯。</p>
 */
@Service
public class WorkOrderServiceImpl extends ServiceImpl<MesWorkOrderMapper, MesWorkOrder>
        implements WorkOrderService {

    private final ProductMapper productMapper;
    private final BomMapper bomMapper;
    private final RouteMapper routeMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;

    public WorkOrderServiceImpl(ProductMapper productMapper, BomMapper bomMapper,
                                RouteMapper routeMapper, OrderNoGenerator orderNoGenerator,
                                TraceService traceService) {
        this.productMapper = productMapper;
        this.bomMapper = bomMapper;
        this.routeMapper = routeMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
    }

    @Override
    public PageResult<WorkOrderVO> page(WorkOrderQueryDTO query) {
        LambdaQueryWrapper<MesWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getWorkOrderNo()), MesWorkOrder::getWorkOrderNo, query.getWorkOrderNo())
                .eq(query.getProductId() != null, MesWorkOrder::getProductId, query.getProductId())
                .eq(query.getStatus() != null, MesWorkOrder::getStatus, query.getStatus())
                .ge(query.getPlanStartFrom() != null, MesWorkOrder::getPlanStartTime, query.getPlanStartFrom())
                .le(query.getPlanEndTo() != null, MesWorkOrder::getPlanEndTime, query.getPlanEndTo())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(MesWorkOrder::getWorkOrderNo, query.getKeyword())
                        .or().like(MesWorkOrder::getExternalOrderNo, query.getKeyword())
                        .or().like(MesWorkOrder::getProductNameSnapshot, query.getKeyword()))
                .orderByDesc(MesWorkOrder::getId);
        Page<MesWorkOrder> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(WorkOrderVO::of));
    }

    @Override
    public WorkOrderVO getDetail(Long id) {
        WorkOrderVO vo = WorkOrderVO.of(mustExist(id));
        // TODO T6：填充工序任务列表；TODO T8：填充报工统计
        return vo;
    }

    @Override
    @Transactional
    public Long create(WorkOrderSaveDTO dto) {
        MesWorkOrder wo = new MesWorkOrder();
        applySave(wo, dto);
        wo.setWorkOrderNo(orderNoGenerator.nextWorkOrderNo());
        wo.setStatus(WorkOrderStatus.DRAFT);
        wo.setCompletedQty(0);
        wo.setGoodQty(0);
        wo.setDefectQty(0);
        this.save(wo);
        traceService.write(wo.getId(), null, ActionType.CREATE,
                Map.of("workOrderNo", wo.getWorkOrderNo(), "planQty", wo.getPlanQty(),
                        "bomId", wo.getBomId(), "routeId", wo.getRouteId()));
        return wo.getId();
    }

    @Override
    @Transactional
    public void update(Long id, WorkOrderSaveDTO dto) {
        MesWorkOrder wo = mustExist(id);
        if (wo.getStatus() != WorkOrderStatus.DRAFT) {
            throw new BusinessException("仅草稿状态的工单可以编辑，当前状态: " + wo.getStatus().getLabel());
        }
        applySave(wo, dto);
        this.updateById(wo);
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        MesWorkOrder wo = mustExist(id);
        switch (wo.getStatus()) {
            case CANCELLED:
                return; // 幂等：重复取消不报错
            case DRAFT:
            case RELEASED:
            case IN_PROGRESS:
                break;
            default:
                throw new BusinessException("当前状态不允许取消: " + wo.getStatus().getLabel());
        }
        // TODO T7：级联取消未完成工序任务（任务表逻辑就位后回填）
        wo.setStatus(WorkOrderStatus.CANCELLED);
        this.updateById(wo);
        traceService.write(wo.getId(), null, ActionType.CANCEL,
                Map.of("workOrderNo", wo.getWorkOrderNo()));
    }

    private MesWorkOrder mustExist(Long id) {
        MesWorkOrder wo = this.getById(id);
        if (wo == null) {
            throw new BusinessException("工单不存在: id=" + id);
        }
        return wo;
    }

    /** 保存前公共校验：产品启用 + 自动解析 ACTIVE BOM/路线 + 快照回填 */
    private void applySave(MesWorkOrder wo, WorkOrderSaveDTO dto) {
        MesProduct product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在: id=" + dto.getProductId());
        }
        if (product.getStatus() != ProductStatus.ENABLED) {
            throw new BusinessException("产品未启用，不能维护工单: " + product.getProductCode());
        }
        if (dto.getPlanStartTime() != null && dto.getPlanEndTime() != null
                && dto.getPlanEndTime().isBefore(dto.getPlanStartTime())) {
            throw new BusinessException("计划结束时间不能早于计划开始时间");
        }
        // 自动解析：产品下生效（ACTIVE）的 BOM 与工艺路线，各取最新一条
        MesBom bom = bomMapper.selectOne(new LambdaQueryWrapper<MesBom>()
                .eq(MesBom::getProductId, dto.getProductId())
                .eq(MesBom::getStatus, BomStatus.ACTIVE)
                .orderByDesc(MesBom::getId)
                .last("LIMIT 1"));
        if (bom == null) {
            throw new BusinessException("产品下无生效 BOM，请先在 BOM 管理维护并激活: " + product.getProductCode());
        }
        MesRoute route = routeMapper.selectOne(new LambdaQueryWrapper<MesRoute>()
                .eq(MesRoute::getProductId, dto.getProductId())
                .eq(MesRoute::getStatus, RouteStatus.ACTIVE)
                .orderByDesc(MesRoute::getId)
                .last("LIMIT 1"));
        if (route == null) {
            throw new BusinessException("产品下无生效工艺路线，请先在工艺路线维护并激活: " + product.getProductCode());
        }
        wo.setProductId(dto.getProductId());
        wo.setProductCodeSnapshot(product.getProductCode());
        wo.setProductNameSnapshot(product.getProductName());
        wo.setBomId(bom.getId());
        wo.setRouteId(route.getId());
        wo.setPlanQty(dto.getPlanQty());
        wo.setExternalOrderNo(dto.getExternalOrderNo());
        wo.setPriority(dto.getPriority() == null ? OrderPriority.NORMAL : dto.getPriority());
        wo.setPlanStartTime(dto.getPlanStartTime());
        wo.setPlanEndTime(dto.getPlanEndTime());
        wo.setRemark(dto.getRemark());
    }
}
