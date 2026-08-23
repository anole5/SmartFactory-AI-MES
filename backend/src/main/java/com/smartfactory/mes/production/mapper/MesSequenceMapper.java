package com.smartfactory.mes.production.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 单号序列表 mapper（注解 SQL，与项目零 XML 约定一致）。
 *
 * <p>只服务于 {@link com.smartfactory.mes.common.sequence.OrderNoGenerator}，
 * 不继承 BaseMapper（无 CRUD 需求）。</p>
 */
public interface MesSequenceMapper {

    /**
     * 当日行不存在则补插（INSERT IGNORE 靠唯一键去重，并发下冲突自动忽略）
     */
    @Insert("INSERT IGNORE INTO mes_sequence (seq_type, seq_date, current_value, tenant_id) "
            + "VALUES (#{seqType}, #{seqDate}, 0, #{tenantId})")
    int insertIgnoreToday(@Param("seqType") String seqType,
                          @Param("seqDate") String seqDate,
                          @Param("tenantId") Long tenantId);

    /**
     * 原子自增：UPDATE 对唯一键行加排他锁，并发请求串行执行；
     * LAST_INSERT_ID(current_value + 1) 把新值写入当前连接的会话变量
     */
    @Update("UPDATE mes_sequence SET current_value = LAST_INSERT_ID(current_value + 1) "
            + "WHERE seq_type = #{seqType} AND seq_date = #{seqDate} AND tenant_id = #{tenantId}")
    int increment(@Param("seqType") String seqType,
                  @Param("seqDate") String seqDate,
                  @Param("tenantId") Long tenantId);

    /**
     * 批量原子自增（SN 批量取号用）：一次 UPDATE 取 count 个连续号，
     * LAST_INSERT_ID 记录的是区间末值，区间 = [末值-count+1, 末值]。
     * 比逐台取号少 count-1 次行锁竞争。
     */
    @Update("UPDATE mes_sequence SET current_value = LAST_INSERT_ID(current_value + #{count}) "
            + "WHERE seq_type = #{seqType} AND seq_date = #{seqDate} AND tenant_id = #{tenantId}")
    int incrementBatch(@Param("seqType") String seqType,
                       @Param("seqDate") String seqDate,
                       @Param("tenantId") Long tenantId,
                       @Param("count") int count);

    /**
     * 取回本连接的本次自增值（连接级会话变量，绝不串号）
     */
    @Select("SELECT LAST_INSERT_ID()")
    Long lastInsertId();
}
