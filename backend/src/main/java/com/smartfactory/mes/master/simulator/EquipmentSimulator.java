package com.smartfactory.mes.master.simulator;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartfactory.mes.master.entity.MesEquipment;
import com.smartfactory.mes.master.enums.EquipmentStatus;
import com.smartfactory.mes.master.mapper.EquipmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 设备状态漂移模拟器（演示环境专用）
 *
 * <p>每 equipment.simulate.interval-ms 随机挑 1-2 台设备漂移到随机状态，
 * 模拟真实产线设备状态抖动，供生产看板展示动态效果。</p>
 *
 * <p>equipment.simulate.enabled=false 可整体关闭。注意：定时器依赖启动类
 * 的 @EnableScheduling 全局开关——不加则 @Scheduled 静默不触发（第 3 周最高概率坑）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "equipment.simulate", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EquipmentSimulator {

    private final EquipmentMapper equipmentMapper;

    public EquipmentSimulator(EquipmentMapper equipmentMapper) {
        this.equipmentMapper = equipmentMapper;
    }

    @Scheduled(fixedDelayString = "${equipment.simulate.interval-ms:15000}")
    public void drift() {
        List<MesEquipment> all = equipmentMapper.selectList(null);
        if (all.isEmpty()) {
            return;
        }
        // 洗牌后取前 N 台，避免同轮重复漂移同一台
        List<MesEquipment> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        int count = 1 + ThreadLocalRandom.current().nextInt(2); // 每轮随机 1-2 台
        for (MesEquipment target : shuffled.subList(0, Math.min(count, shuffled.size()))) {
            EquipmentStatus newStatus = EquipmentStatus.values()[ThreadLocalRandom.current()
                    .nextInt(EquipmentStatus.values().length)];
            if (target.getStatus() == newStatus) {
                continue; // 同状态不写库，避免无意义 UPDATE
            }
            equipmentMapper.update(null, new LambdaUpdateWrapper<MesEquipment>()
                    .eq(MesEquipment::getId, target.getId())
                    .set(MesEquipment::getStatus, newStatus));
            log.info("设备状态漂移: {} {} -> {}", target.getEquipmentCode(),
                    target.getStatus().getCode(), newStatus.getCode());
        }
    }
}
