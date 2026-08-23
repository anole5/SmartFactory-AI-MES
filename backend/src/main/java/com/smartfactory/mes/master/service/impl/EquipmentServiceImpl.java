package com.smartfactory.mes.master.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.EnumUtils;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.master.dto.EquipmentQueryDTO;
import com.smartfactory.mes.master.dto.EquipmentSaveDTO;
import com.smartfactory.mes.master.dto.EquipmentVO;
import com.smartfactory.mes.master.entity.MesEquipment;
import com.smartfactory.mes.master.entity.MesWorkstation;
import com.smartfactory.mes.master.enums.EquipmentStatus;
import com.smartfactory.mes.master.mapper.EquipmentMapper;
import com.smartfactory.mes.master.mapper.WorkstationMapper;
import com.smartfactory.mes.master.service.EquipmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备 Service 实现（CRUD 照 Material 模板，编码重复 409）
 */
@Service
public class EquipmentServiceImpl extends ServiceImpl<EquipmentMapper, MesEquipment>
        implements EquipmentService {

    private final WorkstationMapper workstationMapper;

    public EquipmentServiceImpl(WorkstationMapper workstationMapper) {
        this.workstationMapper = workstationMapper;
    }

    @Override
    public PageResult<EquipmentVO> page(EquipmentQueryDTO query) {
        LambdaQueryWrapper<MesEquipment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MesEquipment::getEquipmentCode, query.getKeyword())
                    .or().like(MesEquipment::getEquipmentName, query.getKeyword()));
        }
        wrapper.eq(query.getWorkstationId() != null, MesEquipment::getWorkstationId, query.getWorkstationId())
                .eq(query.getStatus() != null, MesEquipment::getStatus, query.getStatus())
                .orderByDesc(MesEquipment::getId);
        Page<MesEquipment> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<MesEquipment> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        // 工位名称批量回填（selectBatchIds 空集合会生成非法 SQL，必查非空）
        Set<Long> workstationIds = records.stream().map(MesEquipment::getWorkstationId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesWorkstation> workstations = workstationIds.isEmpty() ? Collections.emptyMap()
                : workstationMapper.selectBatchIds(workstationIds).stream()
                .collect(Collectors.toMap(MesWorkstation::getId, Function.identity()));
        List<EquipmentVO> vos = records.stream().map(r -> {
            EquipmentVO vo = EquipmentVO.of(r);
            MesWorkstation ws = workstations.get(r.getWorkstationId());
            if (ws != null) {
                vo.setWorkstationName(ws.getWorkstationName());
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public EquipmentVO getDetail(Long id) {
        MesEquipment entity = mustExist(id);
        EquipmentVO vo = EquipmentVO.of(entity);
        if (entity.getWorkstationId() != null) {
            MesWorkstation ws = workstationMapper.selectById(entity.getWorkstationId());
            if (ws != null) {
                vo.setWorkstationName(ws.getWorkstationName());
            }
        }
        return vo;
    }

    @Override
    @Transactional
    public Long create(EquipmentSaveDTO dto) {
        checkCodeUnique(dto.getEquipmentCode(), null);
        MesEquipment entity = new MesEquipment();
        entity.setEquipmentCode(dto.getEquipmentCode());
        entity.setEquipmentName(dto.getEquipmentName());
        entity.setModel(dto.getModel());
        entity.setWorkstationId(dto.getWorkstationId());
        entity.setRemark(dto.getRemark());
        entity.setStatus(EquipmentStatus.RUNNING);
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, EquipmentSaveDTO dto) {
        MesEquipment entity = mustExist(id);
        checkCodeUnique(dto.getEquipmentCode(), id);
        entity.setEquipmentCode(dto.getEquipmentCode());
        entity.setEquipmentName(dto.getEquipmentName());
        entity.setModel(dto.getModel());
        entity.setWorkstationId(dto.getWorkstationId());
        entity.setRemark(dto.getRemark());
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String statusCode) {
        MesEquipment entity = mustExist(id);
        EquipmentStatus target = EnumUtils.parse(EquipmentStatus.values(), EquipmentStatus::getCode, statusCode, "设备状态");
        entity.setStatus(target);
        this.updateById(entity);
    }

    private MesEquipment mustExist(Long id) {
        MesEquipment entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("设备不存在: id=" + id);
        }
        return entity;
    }

    private void checkCodeUnique(String equipmentCode, Long excludeId) {
        List<MesEquipment> exist = this.list(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getEquipmentCode, equipmentCode)
                .ne(excludeId != null, MesEquipment::getId, excludeId));
        if (!exist.isEmpty()) {
            throw new BusinessException("设备编码已存在: " + equipmentCode);
        }
    }
}
