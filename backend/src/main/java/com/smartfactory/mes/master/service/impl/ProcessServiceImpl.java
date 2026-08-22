package com.smartfactory.mes.master.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.master.dto.ProcessQueryDTO;
import com.smartfactory.mes.master.dto.ProcessSaveDTO;
import com.smartfactory.mes.master.dto.ProcessVO;
import com.smartfactory.mes.master.entity.MesProcess;
import com.smartfactory.mes.master.entity.MesRouteStep;
import com.smartfactory.mes.master.mapper.ProcessMapper;
import com.smartfactory.mes.master.mapper.RouteStepMapper;
import com.smartfactory.mes.master.service.ProcessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工序 Service 实现
 */
@Service
public class ProcessServiceImpl extends ServiceImpl<ProcessMapper, MesProcess> implements ProcessService {

    private final RouteStepMapper routeStepMapper;

    public ProcessServiceImpl(RouteStepMapper routeStepMapper) {
        this.routeStepMapper = routeStepMapper;
    }

    @Override
    public PageResult<ProcessVO> page(ProcessQueryDTO query) {
        LambdaQueryWrapper<MesProcess> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MesProcess::getProcessCode, query.getKeyword())
                    .or().like(MesProcess::getProcessName, query.getKeyword()));
        }
        wrapper.orderByDesc(MesProcess::getId);
        Page<MesProcess> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(ProcessVO::of));
    }

    @Override
    public ProcessVO getDetail(Long id) {
        return ProcessVO.of(mustExist(id));
    }

    @Override
    @Transactional
    public Long create(ProcessSaveDTO dto) {
        checkCodeUnique(dto.getProcessCode(), null);
        MesProcess entity = new MesProcess();
        entity.setProcessCode(dto.getProcessCode());
        entity.setProcessName(dto.getProcessName());
        entity.setNeedInspection(Boolean.TRUE.equals(dto.getNeedInspection()));
        entity.setStandardMinutes(dto.getStandardMinutes());
        entity.setDescription(dto.getDescription());
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, ProcessSaveDTO dto) {
        MesProcess entity = mustExist(id);
        checkCodeUnique(dto.getProcessCode(), id);
        entity.setProcessCode(dto.getProcessCode());
        entity.setProcessName(dto.getProcessName());
        entity.setNeedInspection(Boolean.TRUE.equals(dto.getNeedInspection()));
        entity.setStandardMinutes(dto.getStandardMinutes());
        entity.setDescription(dto.getDescription());
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mustExist(id);
        boolean referenced = routeStepMapper.selectCount(
                new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getProcessId, id)) > 0;
        if (referenced) {
            throw new BusinessException("工序已被工艺路线引用，不能删除");
        }
        this.removeById(id);
    }

    private MesProcess mustExist(Long id) {
        MesProcess entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("工序不存在: id=" + id);
        }
        return entity;
    }

    private void checkCodeUnique(String processCode, Long excludeId) {
        List<MesProcess> exist = this.list(new LambdaQueryWrapper<MesProcess>()
                .eq(MesProcess::getProcessCode, processCode)
                .ne(excludeId != null, MesProcess::getId, excludeId));
        if (!exist.isEmpty()) {
            throw new BusinessException("工序编码已存在: " + processCode);
        }
    }
}
