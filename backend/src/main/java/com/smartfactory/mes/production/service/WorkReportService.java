package com.smartfactory.mes.production.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.MaterialBatchBindDTO;
import com.smartfactory.mes.production.dto.WorkReportQueryDTO;
import com.smartfactory.mes.production.dto.WorkReportSaveDTO;
import com.smartfactory.mes.production.dto.WorkReportVO;

import java.util.List;

/**
 * 报工服务：数量校验 + 任务/工单进度回写 + 报工记录查询
 */
public interface WorkReportService {

    /** 报工记录分页列表 */
    PageResult<WorkReportVO> page(WorkReportQueryDTO query);

    /** 按生产批次号查报工记录（批量回填工单号/任务号/操作人，批次追溯数据源） */
    List<WorkReportVO> listByBatchNo(String batchNo);

    /** 报工：校验链 + CAS 累计 + 状态结转 + 工单进度回写（整单事务，任一步失败全部回滚） */
    void report(WorkReportSaveDTO dto);

    /**
     * 补录关键件批次绑定（第 6 周，独立事务）：报工后漏绑可补录，
     * 校验规则与报工内嵌绑定共用（批次存在/物料匹配/关键件/同料换批拦截/重放幂等）
     */
    void bindBatches(Long reportId, List<MaterialBatchBindDTO> items);
}
