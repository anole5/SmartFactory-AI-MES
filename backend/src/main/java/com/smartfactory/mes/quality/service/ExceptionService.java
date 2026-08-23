package com.smartfactory.mes.quality.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.quality.dto.ExceptionCloseDTO;
import com.smartfactory.mes.quality.dto.ExceptionOrderVO;
import com.smartfactory.mes.quality.dto.ExceptionQueryDTO;
import com.smartfactory.mes.quality.dto.ExceptionSaveDTO;

/**
 * 异常单服务（不良生成异常单走 DefectService.toException）
 */
public interface ExceptionService {

    /** 异常单分页列表（工单号/处理人名称/不良单号批量回填） */
    PageResult<ExceptionOrderVO> page(ExceptionQueryDTO query);

    /** 手工创建异常单（MANUAL）：OPEN + EXCEPTION_CREATE 追溯（关联工单时写） */
    Long createManual(ExceptionSaveDTO dto);

    /** 开始处理：OPEN -> PROCESSING（同状态幂等，其余 409），回填处理人 + EXCEPTION_PROCESS 追溯 */
    void process(Long id);

    /** 关闭异常：PROCESSING -> CLOSED（同状态幂等，其余 409），处理结论必填 + EXCEPTION_CLOSE 追溯 */
    void close(Long id, ExceptionCloseDTO dto);
}
