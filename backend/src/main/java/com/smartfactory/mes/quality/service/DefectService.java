package com.smartfactory.mes.quality.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.quality.dto.DefectQueryDTO;
import com.smartfactory.mes.quality.dto.DefectRecordVO;

/**
 * 不良记录服务
 */
public interface DefectService {

    /** 不良记录分页列表（工单号/工序快照批量回填） */
    PageResult<DefectRecordVO> page(DefectQueryDTO query);

    /**
     * 不良生成异常单（source_type=DEFECT）：
     * 工单/任务/质检任务/不良代码快照自不良记录，同不良已有未关闭（OPEN/PROCESSING）异常单则 409 防重复
     *
     * @return 异常单 ID
     */
    Long toException(Long defectId);
}
