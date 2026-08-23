package com.smartfactory.mes.master.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.EquipmentQueryDTO;
import com.smartfactory.mes.master.dto.EquipmentSaveDTO;
import com.smartfactory.mes.master.dto.EquipmentVO;
import com.smartfactory.mes.master.entity.MesEquipment;

/**
 * 设备 Service（第 3 周：独立设备主数据 + 状态漂移模拟）
 */
public interface EquipmentService extends IService<MesEquipment> {

    PageResult<EquipmentVO> page(EquipmentQueryDTO query);

    EquipmentVO getDetail(Long id);

    Long create(EquipmentSaveDTO dto);

    void update(Long id, EquipmentSaveDTO dto);

    /** 状态切换（RUNNING/IDLE/STOPPED/MAINTENANCE，非严格状态机允许任意切换） */
    void changeStatus(Long id, String statusCode);
}
