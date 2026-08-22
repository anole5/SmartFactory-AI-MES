package com.smartfactory.mes.master.dto;

import com.smartfactory.mes.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 工序分页查询入参
 */
@Getter
@Setter
public class ProcessQueryDTO extends PageQuery {

    /** 编码或名称关键字 */
    private String keyword;
}
