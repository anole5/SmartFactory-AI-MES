package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.production.dto.TraceRecordVO;
import com.smartfactory.mes.production.entity.MesTraceRecord;
import com.smartfactory.mes.production.enums.ActionType;
import com.smartfactory.mes.production.mapper.MesTraceRecordMapper;
import com.smartfactory.mes.production.service.TraceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 追溯记录服务实现：单号 + 当前操作人统一回填，业务层只管传动作与明细
 */
@Service
public class TraceServiceImpl implements TraceService {

    private final MesTraceRecordMapper traceRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final ObjectMapper objectMapper;

    public TraceServiceImpl(MesTraceRecordMapper traceRecordMapper,
                            SysUserMapper sysUserMapper,
                            OrderNoGenerator orderNoGenerator, ObjectMapper objectMapper) {
        this.traceRecordMapper = traceRecordMapper;
        this.sysUserMapper = sysUserMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(Long workOrderId, Long taskId, ActionType actionType, Object detail) {
        MesTraceRecord record = new MesTraceRecord();
        record.setTraceNo(orderNoGenerator.nextTraceNo());
        record.setWorkOrderId(workOrderId);
        record.setTaskId(taskId);
        record.setActionType(actionType);
        record.setActionTime(LocalDateTime.now());
        // 操作人来自登录拦截器放入的 ThreadLocal（非登录场景为 0）
        record.setOperatorId(CurrentUserContext.getUserIdOrZero());
        record.setActionDetail(toJson(detail));
        traceRecordMapper.insert(record);
    }

    @Override
    public List<TraceRecordVO> listByWorkOrder(Long workOrderId) {
        List<MesTraceRecord> records = traceRecordMapper.selectList(new LambdaQueryWrapper<MesTraceRecord>()
                .eq(MesTraceRecord::getWorkOrderId, workOrderId)
                .orderByAsc(MesTraceRecord::getActionTime)
                .orderByAsc(MesTraceRecord::getId));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        // 操作人名称一次批量查全（时间线不分页，条数可控）
        Set<Long> operatorIds = records.stream().map(MesTraceRecord::getOperatorId).collect(Collectors.toSet());
        Map<Long, SysUser> operators = sysUserMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return records.stream().map(r -> {
            TraceRecordVO vo = TraceRecordVO.of(r);
            SysUser operator = operators.get(r.getOperatorId());
            if (operator != null) {
                vo.setOperatorName(operator.getRealName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            // 明细都是简单 Map 结构，序列化失败说明调用方传了不可序列化对象——按编程错误抛出
            throw new BusinessException("追溯明细序列化失败: " + e.getMessage());
        }
    }
}
