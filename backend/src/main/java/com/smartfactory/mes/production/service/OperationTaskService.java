package com.smartfactory.mes.production.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.OperationTaskVO;
import com.smartfactory.mes.production.dto.TaskAssignDTO;
import com.smartfactory.mes.production.dto.TaskQueryDTO;

import java.util.List;

/**
 * 工序任务服务
 */
public interface OperationTaskService {

    /** 分页查询（工单号/工位/操作员批量回填） */
    PageResult<OperationTaskVO> page(TaskQueryDTO query);

    /** 某工单的任务列表（按工序顺序号升序） */
    List<OperationTaskVO> listByWorkOrder(Long workOrderId);

    /** 派工：PENDING -> ASSIGNED（操作员必填校验，工位可选覆盖并刷新设备快照） */
    void assign(Long taskId, TaskAssignDTO dto);

    /** 开工：ASSIGNED -> RUNNING（首任务开工时工单 RELEASED -> IN_PROGRESS + 回填实际开工时间） */
    void start(Long taskId);

    /** 暂停：RUNNING -> PAUSED（同状态幂等） */
    void pause(Long taskId);

    /** 继续：PAUSED -> RUNNING（同状态幂等） */
    void resume(Long taskId);
}
