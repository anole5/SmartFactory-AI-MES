package com.smartfactory.mes.common.sequence;

import com.smartfactory.mes.production.mapper.MesSequenceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成器：mes_sequence 表 + MySQL 原子自增。
 * 格式：前缀 + yyyyMMdd + 4 位流水，如 WO202608230001（一看即知 8 月 23 日第 1 单）。
 *
 * <p>并发正确性（面试可讲）：
 * <ol>
 *   <li>UPDATE ... SET current_value = LAST_INSERT_ID(current_value + 1) 对唯一键行加排他锁，
 *       两个并发请求串行执行，各自取到不同值，自增不会丢失</li>
 *   <li>LAST_INSERT_ID() 是连接级会话变量，同一连接内取到的就是本连接的本次自增值</li>
 *   <li><b>本方法必须带 @Transactional</b>：无事务时 Spring 每次 mapper 调用可能开新连接，
 *       SELECT LAST_INSERT_ID() 会取到别的连接的值——本方案第一大坑</li>
 *   <li>调用方事务回滚时序号不回收（跳号），业界通行做法：号码本就不承诺连续</li>
 * </ol>
 * 对比方案：Redis INCR（高性能但要引入组件）、UUID/雪花（无序难排查）、
 * 唯一索引 + 冲突重试（冲突风暴）。本项目选序列表：零额外组件、单号可读可排序。
 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 第一版固定默认租户（BaseEntity 同约定） */
    private static final Long DEFAULT_TENANT_ID = 1L;

    private final MesSequenceMapper mesSequenceMapper;

    public OrderNoGenerator(MesSequenceMapper mesSequenceMapper) {
        this.mesSequenceMapper = mesSequenceMapper;
    }

    /** 工单号，如 WO202608230001 */
    public String nextWorkOrderNo() {
        return next("WO");
    }

    /** 工序任务号，如 TASK202608230001 */
    public String nextTaskNo() {
        return next("TASK");
    }

    /** 报工单号，如 RPT202608230001 */
    public String nextReportNo() {
        return next("RPT");
    }

    /** 追溯单号，如 TRC202608230001 */
    public String nextTraceNo() {
        return next("TRC");
    }

    /** BOM 单号，如 BOM202608230001（第 2 周起替换时间戳格式） */
    public String nextBomNo() {
        return next("BOM");
    }

    /** 工艺路线单号，如 RT202608230001（第 2 周起替换时间戳格式） */
    public String nextRouteNo() {
        return next("RT");
    }

    /**
     * 三步原子取号（见类注释，必须同事务保证同连接）：
     * ① INSERT IGNORE 补当日行 ② UPDATE 原子自增并写 LAST_INSERT_ID ③ SELECT 取回
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        mesSequenceMapper.insertIgnoreToday(prefix, date, DEFAULT_TENANT_ID);
        mesSequenceMapper.increment(prefix, date, DEFAULT_TENANT_ID);
        Long seq = mesSequenceMapper.lastInsertId();
        return prefix + date + String.format("%04d", seq);
    }
}
