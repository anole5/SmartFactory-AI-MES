package com.smartfactory.mes.master.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.ProcessQueryDTO;
import com.smartfactory.mes.master.dto.ProcessSaveDTO;
import com.smartfactory.mes.master.dto.ProcessVO;
import com.smartfactory.mes.master.entity.MesProcess;

/**
 * 工序 Service（无启停用状态）
 */
public interface ProcessService extends IService<MesProcess> {

    PageResult<ProcessVO> page(ProcessQueryDTO query);

    ProcessVO getDetail(Long id);

    Long create(ProcessSaveDTO dto);

    void update(Long id, ProcessSaveDTO dto);

    void delete(Long id);
}
