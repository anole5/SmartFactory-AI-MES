package com.smartfactory.mes.master.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.EnumUtils;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.master.dto.WorkstationQueryDTO;
import com.smartfactory.mes.master.dto.WorkstationSaveDTO;
import com.smartfactory.mes.master.dto.WorkstationVO;
import com.smartfactory.mes.master.entity.MesRouteStep;
import com.smartfactory.mes.master.entity.MesWorkstation;
import com.smartfactory.mes.master.enums.WorkstationStatus;
import com.smartfactory.mes.master.mapper.RouteStepMapper;
import com.smartfactory.mes.master.mapper.WorkstationMapper;
import com.smartfactory.mes.master.service.WorkstationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工位 Service 实现
 */
@Service
public class WorkstationServiceImpl extends ServiceImpl<WorkstationMapper, MesWorkstation> implements WorkstationService {

    private final RouteStepMapper routeStepMapper;

    public WorkstationServiceImpl(RouteStepMapper routeStepMapper) {
        this.routeStepMapper = routeStepMapper;
    }

    @Override
    public PageResult<WorkstationVO> page(WorkstationQueryDTO query) {
        LambdaQueryWrapper<MesWorkstation> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MesWorkstation::getWorkstationCode, query.getKeyword())
                    .or().like(MesWorkstation::getWorkstationName, query.getKeyword()));
        }
        wrapper.eq(query.getStatus() != null, MesWorkstation::getStatus, query.getStatus())
                .orderByDesc(MesWorkstation::getId);
        Page<MesWorkstation> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(WorkstationVO::of));
    }

    @Override
    public WorkstationVO getDetail(Long id) {
        return WorkstationVO.of(mustExist(id));
    }

    @Override
    @Transactional
    public Long create(WorkstationSaveDTO dto) {
        checkCodeUnique(dto.getWorkstationCode(), null);
        MesWorkstation entity = new MesWorkstation();
        entity.setWorkstationCode(dto.getWorkstationCode());
        entity.setWorkstationName(dto.getWorkstationName());
        entity.setEquipmentCode(dto.getEquipmentCode());
        entity.setEquipmentName(dto.getEquipmentName());
        entity.setStatus(WorkstationStatus.ENABLED);
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, WorkstationSaveDTO dto) {
        MesWorkstation entity = mustExist(id);
        checkCodeUnique(dto.getWorkstationCode(), id);
        entity.setWorkstationCode(dto.getWorkstationCode());
        entity.setWorkstationName(dto.getWorkstationName());
        entity.setEquipmentCode(dto.getEquipmentCode());
        entity.setEquipmentName(dto.getEquipmentName());
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String statusCode) {
        MesWorkstation entity = mustExist(id);
        WorkstationStatus target = EnumUtils.parse(WorkstationStatus.values(), WorkstationStatus::getCode, statusCode, "工位状态");
        entity.setStatus(target);
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mustExist(id);
        boolean referenced = routeStepMapper.selectCount(
                new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getWorkstationId, id)) > 0;
        if (referenced) {
            throw new BusinessException("工位已被工艺路线引用，不能删除");
        }
        this.removeById(id);
    }

    private MesWorkstation mustExist(Long id) {
        MesWorkstation entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("工位不存在: id=" + id);
        }
        return entity;
    }

    private void checkCodeUnique(String workstationCode, Long excludeId) {
        List<MesWorkstation> exist = this.list(new LambdaQueryWrapper<MesWorkstation>()
                .eq(MesWorkstation::getWorkstationCode, workstationCode)
                .ne(excludeId != null, MesWorkstation::getId, excludeId));
        if (!exist.isEmpty()) {
            throw new BusinessException("工位编码已存在: " + workstationCode);
        }
    }
}
