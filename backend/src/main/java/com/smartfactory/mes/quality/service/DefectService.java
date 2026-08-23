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
}
