package com.smartfactory.mes.master.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.WorkstationQueryDTO;
import com.smartfactory.mes.master.dto.WorkstationSaveDTO;
import com.smartfactory.mes.master.dto.WorkstationVO;
import com.smartfactory.mes.master.entity.MesWorkstation;

/**
 * 工位 Service
 */
public interface WorkstationService extends IService<MesWorkstation> {

    PageResult<WorkstationVO> page(WorkstationQueryDTO query);

    WorkstationVO getDetail(Long id);

    Long create(WorkstationSaveDTO dto);

    void update(Long id, WorkstationSaveDTO dto);

    void changeStatus(Long id, String statusCode);

    void delete(Long id);
}
