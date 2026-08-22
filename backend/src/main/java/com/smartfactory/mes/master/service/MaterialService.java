package com.smartfactory.mes.master.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.MaterialQueryDTO;
import com.smartfactory.mes.master.dto.MaterialSaveDTO;
import com.smartfactory.mes.master.dto.MaterialVO;
import com.smartfactory.mes.master.entity.MesMaterial;

/**
 * 物料 Service
 */
public interface MaterialService extends IService<MesMaterial> {

    PageResult<MaterialVO> page(MaterialQueryDTO query);

    MaterialVO getDetail(Long id);

    Long create(MaterialSaveDTO dto);

    void update(Long id, MaterialSaveDTO dto);

    void changeStatus(Long id, String statusCode);

    void delete(Long id);
}
