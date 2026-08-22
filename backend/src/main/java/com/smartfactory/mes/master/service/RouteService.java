package com.smartfactory.mes.master.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.RouteQueryDTO;
import com.smartfactory.mes.master.dto.RouteSaveDTO;
import com.smartfactory.mes.master.dto.RouteVO;
import com.smartfactory.mes.master.entity.MesRoute;

/**
 * 工艺路线 Service：头 + 步骤整单事务维护，状态机与 BOM 一致
 */
public interface RouteService extends IService<MesRoute> {

    PageResult<RouteVO> page(RouteQueryDTO query);

    RouteVO getDetail(Long id);

    Long create(RouteSaveDTO dto);

    void update(Long id, RouteSaveDTO dto);

    void changeStatus(Long id, String statusCode);

    void delete(Long id);
}
