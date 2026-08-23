package com.smartfactory.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.service.TraceService;
import com.smartfactory.mes.quality.dto.ExceptionCloseDTO;
import com.smartfactory.mes.quality.dto.ExceptionOrderVO;
import com.smartfactory.mes.quality.dto.ExceptionQueryDTO;
import com.smartfactory.mes.quality.dto.ExceptionSaveDTO;
import com.smartfactory.mes.quality.entity.MesDefectRecord;
import com.smartfactory.mes.quality.entity.MesExceptionOrder;
import com.smartfactory.mes.quality.enums.ExceptionSourceType;
import com.smartfactory.mes.quality.enums.ExceptionStatus;
import com.smartfactory.mes.quality.mapper.MesDefectRecordMapper;
import com.smartfactory.mes.quality.mapper.MesExceptionOrderMapper;
import com.smartfactory.mes.quality.service.ExceptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 异常单服务实现
 *
 * <p>状态机（面试可讲）：OPEN → PROCESSING → CLOSED，显式流转 + 同状态幂等 + 非法跳转 409；
 * 处理/关闭均 CAS（WHERE status=前态），并发下只有一个请求能流转成功。</p>
 */
@Service
public class ExceptionServiceImpl extends ServiceImpl<MesExceptionOrderMapper, MesExceptionOrder>
        implements ExceptionService {

    private final OrderNoGenerator orderNoGenerator;
    private final TraceService traceService;
    private final MesWorkOrderMapper workOrderMapper;
    private final SysUserMapper sysUserMapper;
    private final MesDefectRecordMapper defectRecordMapper;

    public ExceptionServiceImpl(OrderNoGenerator orderNoGenerator, TraceService traceService,
                                MesWorkOrderMapper workOrderMapper, SysUserMapper sysUserMapper,
                                MesDefectRecordMapper defectRecordMapper) {
        this.orderNoGenerator = orderNoGenerator;
        this.traceService = traceService;
        this.workOrderMapper = workOrderMapper;
        this.sysUserMapper = sysUserMapper;
        this.defectRecordMapper = defectRecordMapper;
    }

    @Override
    public PageResult<ExceptionOrderVO> page(ExceptionQueryDTO query) {
        LambdaQueryWrapper<MesExceptionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkOrderId() != null, MesExceptionOrder::getWorkOrderId, query.getWorkOrderId())
                .eq(query.getStatus() != null, MesExceptionOrder::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(MesExceptionOrder::getExceptionNo, query.getKeyword())
                        .or().like(MesExceptionOrder::getDescription, query.getKeyword()))
                .orderByDesc(MesExceptionOrder::getId);
        Page<MesExceptionOrder> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<MesExceptionOrder> orders = page.getRecords();
        if (orders.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        // 批量回填：工单号 / 处理人名称 / 不良单号（注意空集短路，selectBatchIds(空) 会生成非法 SQL）
        Set<Long> workOrderIds = orders.stream().map(MesExceptionOrder::getWorkOrderId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesWorkOrder> workOrders = workOrderIds.isEmpty() ? new HashMap<>()
                : workOrderMapper.selectBatchIds(workOrderIds).stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        Set<Long> handlerIds = orders.stream().map(MesExceptionOrder::getHandlerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> handlers = handlerIds.isEmpty() ? new HashMap<>()
                : sysUserMapper.selectBatchIds(handlerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Set<Long> defectIds = orders.stream().map(MesExceptionOrder::getDefectRecordId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesDefectRecord> defects = defectIds.isEmpty() ? new HashMap<>()
                : defectRecordMapper.selectBatchIds(defectIds).stream()
                .collect(Collectors.toMap(MesDefectRecord::getId, Function.identity()));
        List<ExceptionOrderVO> vos = orders.stream().map(o -> {
            ExceptionOrderVO vo = ExceptionOrderVO.of(o);
            MesWorkOrder wo = workOrders.get(o.getWorkOrderId());
            if (wo != null) {
                vo.setWorkOrderNo(wo.getWorkOrderNo());
            }
            SysUser handler = handlers.get(o.getHandlerId());
            if (handler != null) {
                vo.setHandlerName(handler.getRealName());
            }
            MesDefectRecord defect = defects.get(o.getDefectRecordId());
            if (defect != null) {
                vo.setDefectNo(defect.getDefectNo());
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional
    public Long createManual(ExceptionSaveDTO dto) {
        MesExceptionOrder order = new MesExceptionOrder();
        order.setExceptionNo(orderNoGenerator.nextExceptionNo());
        order.setSourceType(ExceptionSourceType.MANUAL);
        order.setWorkOrderId(dto.getWorkOrderId());
        order.setOperationTaskId(dto.getOperationTaskId());
        order.setInspectionTaskId(dto.getInspectionTaskId());
        order.setDefectCode(dto.getDefectCode());
        order.setDescription(dto.getDescription());
        order.setStatus(ExceptionStatus.OPEN);
        this.save(order);
        // 追溯表 work_order_id 非空约束：仅关联工单的异常写追溯
        if (order.getWorkOrderId() != null) {
            traceService.write(order.getWorkOrderId(), order.getOperationTaskId(), ActionType.EXCEPTION_CREATE,
                    Map.of("exceptionNo", order.getExceptionNo(), "sourceType", order.getSourceType().getCode()));
        }
        return order.getId();
    }

    @Override
    @Transactional
    public void process(Long id) {
        MesExceptionOrder order = mustExist(id);
        if (order.getStatus() == ExceptionStatus.PROCESSING) {
            return; // 同状态幂等：重复开始处理不报错、不重置处理人
        }
        if (order.getStatus() != ExceptionStatus.OPEN) {
            throw new BusinessException("仅待处理的异常可以开始处理，当前状态: " + order.getStatus().getLabel());
        }
        // CAS 防并发双处理：并发请求只有一个能把 OPEN 改成 PROCESSING
        boolean updated = this.update(new LambdaUpdateWrapper<MesExceptionOrder>()
                .eq(MesExceptionOrder::getId, id)
                .eq(MesExceptionOrder::getStatus, ExceptionStatus.OPEN)
                .set(MesExceptionOrder::getStatus, ExceptionStatus.PROCESSING)
                .set(MesExceptionOrder::getHandlerId, CurrentUserContext.getUserIdOrZero()));
        if (!updated) {
            throw new BusinessException("异常单状态已变化，请刷新后重试");
        }
        if (order.getWorkOrderId() != null) {
            traceService.write(order.getWorkOrderId(), order.getOperationTaskId(), ActionType.EXCEPTION_PROCESS,
                    Map.of("exceptionNo", order.getExceptionNo(),
                            "handlerId", CurrentUserContext.getUserIdOrZero()));
        }
    }

    @Override
    @Transactional
    public void close(Long id, ExceptionCloseDTO dto) {
        MesExceptionOrder order = mustExist(id);
        if (order.getStatus() == ExceptionStatus.CLOSED) {
            return; // 同状态幂等：重复关闭不报错、不覆盖原处理结论
        }
        if (order.getStatus() != ExceptionStatus.PROCESSING) {
            throw new BusinessException("仅处理中的异常可以关闭，当前状态: " + order.getStatus().getLabel());
        }
        // CAS 防并发双关闭：并发请求只有一个能把 PROCESSING 改成 CLOSED
        boolean updated = this.update(new LambdaUpdateWrapper<MesExceptionOrder>()
                .eq(MesExceptionOrder::getId, id)
                .eq(MesExceptionOrder::getStatus, ExceptionStatus.PROCESSING)
                .set(MesExceptionOrder::getStatus, ExceptionStatus.CLOSED)
                .set(MesExceptionOrder::getResolveRemark, dto.getResolveRemark())
                .set(MesExceptionOrder::getResolvedAt, LocalDateTime.now()));
        if (!updated) {
            throw new BusinessException("异常单状态已变化，请刷新后重试");
        }
        if (order.getWorkOrderId() != null) {
            traceService.write(order.getWorkOrderId(), order.getOperationTaskId(), ActionType.EXCEPTION_CLOSE,
                    Map.of("exceptionNo", order.getExceptionNo(), "resolveRemark", dto.getResolveRemark()));
        }
    }

    private MesExceptionOrder mustExist(Long id) {
        MesExceptionOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("异常单不存在: id=" + id);
        }
        return order;
    }
}
