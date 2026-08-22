package com.smartfactory.mes.master.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.BomQueryDTO;
import com.smartfactory.mes.master.dto.BomSaveDTO;
import com.smartfactory.mes.master.dto.BomVO;
import com.smartfactory.mes.master.entity.MesBom;

/**
 * BOM Service：头 + 明细整单事务维护，状态机 DRAFT -> ACTIVE -> OBSOLETE
 */
public interface BomService extends IService<MesBom> {

    PageResult<BomVO> page(BomQueryDTO query);

    BomVO getDetail(Long id);

    Long create(BomSaveDTO dto);

    void update(Long id, BomSaveDTO dto);

    void changeStatus(Long id, String statusCode);

    void delete(Long id);
}
