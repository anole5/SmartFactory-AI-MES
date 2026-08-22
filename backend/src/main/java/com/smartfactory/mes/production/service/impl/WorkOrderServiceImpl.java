package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.master.entity.MesBom;
import com.smartfactory.mes.master.entity.MesProcess;
import com.smartfactory.mes.master.entity.MesProduct;
import com.smartfactory.mes.master.entity.MesRoute;
import com.smartfactory.mes.master.entity.MesRouteStep;
import com.smartfactory.mes.master.entity.MesWorkstation;
import com.smartfactory.mes.master.enums.BomStatus;
import com.smartfactory.mes.master.enums.ProductStatus;
import com.smartfactory.mes.master.enums.RouteStatus;
import com.smartfactory.mes.master.mapper.BomMapper;
import com.smartfactory.mes.master.mapper.ProcessMapper;
import com.smartfactory.mes.master.mapper.ProductMapper;
import com.smartfactory.mes.master.mapper.RouteMapper;
import com.smartfactory.mes.master.mapper.RouteStepMapper;
import com.smartfactory.mes.master.mapper.WorkstationMapper;
import com.smartfactory.mes.production.dto.WorkOrderQueryDTO;
import com.smartfactory.mes.production.dto.WorkOrderSaveDTO;
import com.smartfactory.mes.production.dto.WorkOrderVO;
import com.smartfactory.mes.production.entity.MesOperationTask;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.enums.OrderPriority;
import com.smartfactory.mes.production.enums.TaskStatus;
import com.smartfactory.mes.production.enums.WorkOrderStatus;
import com.smartfactory.mes.production.mapper.MesOperationTaskMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.OperationTaskService;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.production.service.WorkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final RouteStepMapper routeStepMapper;
    private final ProcessMapper processMapper;
    private final WorkstationMapper workstationMapper;
    private final MesOperationTaskMapper operationTaskMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;
    private final OperationTaskService operationTaskService;

    public WorkOrderServiceImpl(ProductMapper productMapper, BomMapper bomMapper,
                                RouteMapper routeMapper, RouteStepMapper routeStepMapper,
                                ProcessMapper processMapper, WorkstationMapper workstationMapper,
                                MesOperationTaskMapper operationTaskMapper, OrderNoGenerator orderNoGenerator,
                                TraceService traceService, OperationTaskService operationTaskService) {
        this.productMapper = productMapper;
        this.bomMapper = bomMapper;
        this.routeMapper = routeMapper;
        this.routeStepMapper = routeStepMapper;
        this.processMapper = processMapper;
        this.workstationMapper = workstationMapper;
        this.operationTaskMapper = operationTaskMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
        this.operationTaskService = operationTaskService;
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
        vo.setTasks(operationTaskService.listByWorkOrder(id));
        // TODO T8：填充报工统计
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

    @Override
    @Transactional
    public void release(Long id) {
        MesWorkOrder wo = mustExist(id);
        if (wo.getStatus() != WorkOrderStatus.DRAFT) {
            throw new BusinessException("仅草稿状态的工单可以下发，当前状态: " + wo.getStatus().getLabel());
        }
        // 下发前二次校验：创建后产品可能被停用、BOM/路线可能被作废——下发瞬间再兜底
        MesProduct product = productMapper.selectById(wo.getProductId());
        if (product == null || product.getStatus() != ProductStatus.ENABLED) {
            throw new BusinessException("产品未启用，不能下发工单: " + wo.getProductCodeSnapshot());
        }
        MesBom bom = bomMapper.selectById(wo.getBomId());
        if (bom == null || bom.getStatus() != BomStatus.ACTIVE) {
            throw new BusinessException("BOM 已失效，不能下发工单（请重新维护工单的 BOM）");
        }
        MesRoute route = routeMapper.selectById(wo.getRouteId());
        if (route == null || route.getStatus() != RouteStatus.ACTIVE) {
            throw new BusinessException("工艺路线已失效，不能下发工单（请重新维护工单的工艺路线）");
        }
        List<MesRouteStep> steps = routeStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getRouteId, wo.getRouteId())
                .orderByAsc(MesRouteStep::getSequenceNo));
        if (steps.isEmpty()) {
            throw new BusinessException("工艺路线无步骤，不能下发工单");
        }
        generateTasks(wo, steps);
        // CAS 防并发双下发：并发请求只有一个能把 DRAFT 改成 RELEASED，
        // 输家 UPDATE 影响 0 行 → 抛异常 → 其已生成的任务随事务回滚
        boolean released = this.update(new LambdaUpdateWrapper<MesWorkOrder>()
                .eq(MesWorkOrder::getId, id)
                .eq(MesWorkOrder::getStatus, WorkOrderStatus.DRAFT)
                .set(MesWorkOrder::getStatus, WorkOrderStatus.RELEASED));
        if (!released) {
            throw new BusinessException("工单状态已变化，下发失败，请刷新后重试");
        }
        traceService.write(wo.getId(), null, ActionType.RELEASE,
                Map.of("workOrderNo", wo.getWorkOrderNo(), "taskCount", steps.size(),
                        "routeId", wo.getRouteId()));
    }

    /** 按路线步骤生成工序任务：快照固化 + 默认工位/设备回填 + 数量=工单计划数量 */
    private void generateTasks(MesWorkOrder wo, List<MesRouteStep> steps) {
        Set<Long> workstationIds = steps.stream().map(MesRouteStep::getWorkstationId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesWorkstation> workstations = workstationIds.isEmpty() ? Map.of()
                : workstationMapper.selectBatchIds(workstationIds).stream()
                .collect(Collectors.toMap(MesWorkstation::getId, Function.identity()));
        Set<Long> processIds = steps.stream().map(MesRouteStep::getProcessId).collect(Collectors.toSet());
        Map<Long, MesProcess> processes = processMapper.selectBatchIds(processIds).stream()
                .collect(Collectors.toMap(MesProcess::getId, Function.identity()));
        for (MesRouteStep step : steps) {
            MesOperationTask task = new MesOperationTask();
            task.setTaskNo(orderNoGenerator.nextTaskNo());
            task.setWorkOrderId(wo.getId());
            task.setProcessId(step.getProcessId());
            // 快照优先取路线步骤已固化的值；空则回退工序主数据（兼容手工补录数据）
            String processCode = step.getProcessCodeSnapshot();
            String processName = step.getProcessNameSnapshot();
            MesProcess process = processes.get(step.getProcessId());
            if (!StringUtils.hasText(processCode) && process != null) {
                processCode = process.getProcessCode();
                processName = process.getProcessName();
            }
            task.setProcessCodeSnapshot(processCode);
            task.setProcessNameSnapshot(processName);
            task.setSequenceNo(step.getSequenceNo());
            task.setWorkstationId(step.getWorkstationId());
            MesWorkstation ws = workstations.get(step.getWorkstationId());
            if (ws != null) {
                task.setEquipmentCodeSnapshot(ws.getEquipmentCode());
                task.setEquipmentNameSnapshot(ws.getEquipmentName());
            }
            task.setPlanQty(wo.getPlanQty());
            task.setCompletedQty(0);
            task.setGoodQty(0);
            task.setDefectQty(0);
            task.setStatus(TaskStatus.PENDING);
            task.setNeedInspection(Boolean.TRUE.equals(step.getNeedInspection()));
            task.setStandardMinutes(step.getStandardMinutes());
            operationTaskMapper.insert(task);
        }
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
