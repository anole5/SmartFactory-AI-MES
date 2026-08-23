package com.smartfactory.mes.production.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.SnQueryDTO;
import com.smartfactory.mes.production.dto.SnVO;

/**
 * 整机 SN 服务：最后一道报工完成时生成，按工单分页查询
 */
public interface ProductSnService {

    /** SN 分页列表（工单号/出生报工单号批量回填） */
    PageResult<SnVO> page(SnQueryDTO query);
}
