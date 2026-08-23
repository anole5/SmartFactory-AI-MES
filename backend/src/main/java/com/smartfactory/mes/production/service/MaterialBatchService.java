package com.smartfactory.mes.production.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.MaterialBatchQueryDTO;
import com.smartfactory.mes.production.dto.MaterialBatchSaveDTO;
import com.smartfactory.mes.production.dto.MaterialBatchVO;

/**
 * 物料批次主数据（第 6 周）：关键件来料批次台账，报工绑定批次的数据源
 */
public interface MaterialBatchService {

    /** 批次分页列表（materialId/keyword 过滤） */
    PageResult<MaterialBatchVO> page(MaterialBatchQueryDTO query);

    /** 创建批次（batchNo 生成器 MB 前缀，物料必须存在） */
    Long create(MaterialBatchSaveDTO dto);
}
