package com.smartfactory.mes.master.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.master.entity.MesProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品 Mapper（业务规则全部放 Service，Mapper 只做数据访问）
 */
@Mapper
public interface ProductMapper extends BaseMapper<MesProduct> {
}
