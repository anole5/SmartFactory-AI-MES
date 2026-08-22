package com.smartfactory.mes.production.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.OperationTaskVO;
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
}
