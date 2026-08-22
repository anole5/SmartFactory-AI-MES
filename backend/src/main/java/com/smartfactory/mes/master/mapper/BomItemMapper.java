package com.smartfactory.mes.master.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.master.entity.MesBomItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * BOM 明细 Mapper
 */
@Mapper
public interface BomItemMapper extends BaseMapper<MesBomItem> {
}
