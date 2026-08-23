package com.smartfactory.mes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartFactory-MES 启动类
 *
 * <p>模块化单体架构：系统管理(system)、基础资料(master)、生产执行(production)、
 * 质量(quality)、追溯(trace)、现场(shopfloor)、看板(dashboard)、AI(ai) 按 package 分包，
 * 各模块 Controller/Service/Mapper/Entity/DTO 分层，通过 Service 接口调用，不跨模块直接操作表。</p>
 *
 * <p>@EnableScheduling：第 3 周设备状态漂移模拟器（@Scheduled）依赖此全局开关，
 * 不加则定时器静默不触发。</p>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.smartfactory.mes.**.mapper")
public class SmartFactoryMesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartFactoryMesApplication.class, args);
    }
}
