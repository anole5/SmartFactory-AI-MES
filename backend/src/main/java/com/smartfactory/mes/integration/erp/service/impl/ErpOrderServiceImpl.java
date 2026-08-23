package com.smartfactory.mes.integration.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.api.ResultCode;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.integration.erp.dto.ErpOrderCreateRequest;
import com.smartfactory.mes.integration.erp.dto.ErpOrderQueryDTO;
import com.smartfactory.mes.integration.erp.dto.ErpOrderVO;
import com.smartfactory.mes.integration.erp.entity.MesExternalOrder;
import com.smartfactory.mes.integration.erp.enums.ExternalOrderStatus;
import com.smartfactory.mes.integration.erp.mapper.ExternalOrderMapper;
import com.smartfactory.mes.integration.erp.service.ErpOrderService;
import com.smartfactory.mes.master.entity.MesProduct;
import com.smartfactory.mes.master.service.ProductService;
import com.smartfactory.mes.production.dto.WorkOrderSaveDTO;
import com.smartfactory.mes.production.enums.OrderPriority;
import com.smartfactory.mes.production.service.WorkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * ERP 外部订单实现：模拟下单 → 一键转工单 → 完工回传
 */
@Service
public class ErpOrderServiceImpl implements ErpOrderService {

    private final ExternalOrderMapper externalOrderMapper;
    private final ProductService productService;
    private final WorkOrderService workOrderService;
    private final OrderNoGenerator orderNoGenerator;

    public ErpOrderServiceImpl(ExternalOrderMapper externalOrderMapper,
                               ProductService productService,
                               WorkOrderService workOrderService,
                               OrderNoGenerator orderNoGenerator) {
        this.externalOrderMapper = externalOrderMapper;
        this.productService = productService;
        this.workOrderService = workOrderService;
        this.orderNoGenerator = orderNoGenerator;
    }

    @Override
    public PageResult<ErpOrderVO> page(ErpOrderQueryDTO query) {
        LambdaQueryWrapper<MesExternalOrder> wrapper = new LambdaQueryWrapper<MesExternalOrder>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(MesExternalOrder::getExternalOrderNo, query.getKeyword())
                        .or()
                        .like(MesExternalOrder::getProductNameSnapshot, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), MesExternalOrder::getStatus, query.getStatus())
                .orderByDesc(MesExternalOrder::getId);
        Page<MesExternalOrder> page = externalOrderMapper.selectPage(
                Page.of(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(ErpOrderVO::of));
    }

    @Override
    public ErpOrderVO getDetail(Long id) {
        MesExternalOrder order = externalOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "外部订单不存在");
        }
        return ErpOrderVO.of(order);
    }

    @Override
    @Transactional
    public Long create(ErpOrderCreateRequest request) {
        MesProduct product = productService.getById(request.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        // 优先级校验：不填缺省 NORMAL，非法值 409（与工单优先级枚举对齐）
        String priority = StringUtils.hasText(request.getPriority()) ? request.getPriority() : "NORMAL";
        try {
            OrderPriority.valueOf(priority);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("优先级非法：仅支持 HIGH/NORMAL/LOW");
        }
        MesExternalOrder order = new MesExternalOrder();
        order.setExternalOrderNo(orderNoGenerator.next("ERP"));
        order.setProductId(product.getId());
        order.setProductCodeSnapshot(product.getProductCode());
        order.setProductNameSnapshot(product.getProductName());
        order.setPlanQty(request.getPlanQty());
        order.setPriority(priority);
        order.setPlanStartTime(request.getPlanStartTime());
        order.setPlanEndTime(request.getPlanEndTime());
        order.setStatus(ExternalOrderStatus.PENDING);
        order.setRemark(request.getRemark());
        externalOrderMapper.insert(order);
        return order.getId();
    }

    @Override
    @Transactional
    public void toWorkOrder(Long id) {
        MesExternalOrder order = mustExist(id);
        if (order.getStatus() != ExternalOrderStatus.PENDING) {
            throw new BusinessException("订单状态不允许转工单（仅 PENDING 可转）");
        }
        // 构造工单入参：外部订单号回填 external_order_no（工单侧字段）
        WorkOrderSaveDTO dto = new WorkOrderSaveDTO();
        dto.setProductId(order.getProductId());
        dto.setPlanQty(order.getPlanQty());
        dto.setExternalOrderNo(order.getExternalOrderNo());
        dto.setPriority(OrderPriority.valueOf(order.getPriority()));
        if (order.getPlanStartTime() != null) {
            dto.setPlanStartTime(order.getPlanStartTime().atStartOfDay());
        }
        if (order.getPlanEndTime() != null) {
            dto.setPlanEndTime(order.getPlanEndTime().atTime(23, 59, 59));
        }
        dto.setRemark(order.getRemark());
        // 工单创建会校验产品/BOM/路线，失败抛异常整个事务回滚
        Long workOrderId = workOrderService.create(dto);
        // CAS 翻转（防并发重复转单）：0 行说明被并发请求抢先，抛异常回滚刚创建的工单
        int updated = externalOrderMapper.update(null, new LambdaUpdateWrapper<MesExternalOrder>()
                .eq(MesExternalOrder::getId, id)
                .eq(MesExternalOrder::getStatus, ExternalOrderStatus.PENDING)
                .set(MesExternalOrder::getStatus, ExternalOrderStatus.SYNCED)
                .set(MesExternalOrder::getWorkOrderId, workOrderId));
        if (updated == 0) {
            throw new BusinessException("订单已被并发转单，请刷新重试");
        }
    }

    @Override
    public void markDoneByExternalOrderNo(String externalOrderNo) {
        if (!StringUtils.hasText(externalOrderNo)) {
            return;
        }
        externalOrderMapper.update(null, new LambdaUpdateWrapper<MesExternalOrder>()
                .eq(MesExternalOrder::getExternalOrderNo, externalOrderNo)
                .eq(MesExternalOrder::getStatus, ExternalOrderStatus.SYNCED)
                .set(MesExternalOrder::getStatus, ExternalOrderStatus.DONE));
    }

    private MesExternalOrder mustExist(Long id) {
        MesExternalOrder order = externalOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "外部订单不存在");
        }
        return order;
    }
}
