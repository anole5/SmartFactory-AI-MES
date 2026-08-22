package com.smartfactory.mes.production.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.WorkReportQueryDTO;
import com.smartfactory.mes.production.dto.WorkReportSaveDTO;
import com.smartfactory.mes.production.dto.WorkReportVO;

/**
 * 报工服务：数量校验 + 任务/工单进度回写 + 报工记录查询
 */
public interface WorkReportService {

    /** 报工记录分页列表 */
    PageResult<WorkReportVO> page(WorkReportQueryDTO query);

    /** 报工：校验链 + CAS 累计 + 状态结转 + 工单进度回写（整单事务，任一步失败全部回滚） */
    void report(WorkReportSaveDTO dto);
}
