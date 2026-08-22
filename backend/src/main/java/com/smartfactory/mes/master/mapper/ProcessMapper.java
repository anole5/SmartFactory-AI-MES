package com.smartfactory.mes.master.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.master.entity.MesProcess;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工序 Mapper
 */
@Mapper
public interface ProcessMapper extends BaseMapper<MesProcess> {
}
