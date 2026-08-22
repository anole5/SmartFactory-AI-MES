package com.smartfactory.mes.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.util.List;

/**
 * 分页返回结构（不直接把 MyBatis-Plus 的 Page 暴露给前端，契约更稳）
 */
@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final long current;
    private final long size;

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
    }

    /** 从 MyBatis-Plus 分页结果转换（Page.convert 返回 IPage，故用接口类型） */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
