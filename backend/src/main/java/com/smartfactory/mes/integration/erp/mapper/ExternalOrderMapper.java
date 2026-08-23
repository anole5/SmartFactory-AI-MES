package com.smartfactory.mes.integration.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.integration.erp.entity.MesExternalOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * ERP 外部订单 Mapper
 */
@Mapper
public interface ExternalOrderMapper extends BaseMapper<MesExternalOrder> {
}
