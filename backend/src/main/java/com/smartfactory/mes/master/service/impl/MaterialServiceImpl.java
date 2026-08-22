package com.smartfactory.mes.master.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.EnumUtils;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.master.dto.MaterialQueryDTO;
import com.smartfactory.mes.master.dto.MaterialSaveDTO;
import com.smartfactory.mes.master.dto.MaterialVO;
import com.smartfactory.mes.master.entity.MesBomItem;
import com.smartfactory.mes.master.entity.MesMaterial;
import com.smartfactory.mes.master.enums.MaterialStatus;
import com.smartfactory.mes.master.mapper.BomItemMapper;
import com.smartfactory.mes.master.mapper.MaterialMapper;
import com.smartfactory.mes.master.service.MaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 物料 Service 实现
 */
@Service
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, MesMaterial> implements MaterialService {

    private final BomItemMapper bomItemMapper;

    public MaterialServiceImpl(BomItemMapper bomItemMapper) {
        this.bomItemMapper = bomItemMapper;
    }

    @Override
    public PageResult<MaterialVO> page(MaterialQueryDTO query) {
        LambdaQueryWrapper<MesMaterial> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MesMaterial::getMaterialCode, query.getKeyword())
                    .or().like(MesMaterial::getMaterialName, query.getKeyword()));
        }
        wrapper.eq(StringUtils.hasText(query.getMaterialType()), MesMaterial::getMaterialType, query.getMaterialType())
                .eq(query.getStatus() != null, MesMaterial::getStatus, query.getStatus())
                .orderByDesc(MesMaterial::getId);
        Page<MesMaterial> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(MaterialVO::of));
    }

    @Override
    public MaterialVO getDetail(Long id) {
        return MaterialVO.of(mustExist(id));
    }

    @Override
    @Transactional
    public Long create(MaterialSaveDTO dto) {
        checkCodeUnique(dto.getMaterialCode(), null);
        MesMaterial entity = new MesMaterial();
        entity.setMaterialCode(dto.getMaterialCode());
        entity.setMaterialName(dto.getMaterialName());
        entity.setMaterialType(dto.getMaterialType());
        entity.setUnit(dto.getUnit());
        entity.setTraceRequired(Boolean.TRUE.equals(dto.getTraceRequired()));
        entity.setRemark(dto.getRemark());
        entity.setStatus(MaterialStatus.ENABLED);
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, MaterialSaveDTO dto) {
        MesMaterial entity = mustExist(id);
        checkCodeUnique(dto.getMaterialCode(), id);
        entity.setMaterialCode(dto.getMaterialCode());
        entity.setMaterialName(dto.getMaterialName());
        entity.setMaterialType(dto.getMaterialType());
        entity.setUnit(dto.getUnit());
        entity.setTraceRequired(Boolean.TRUE.equals(dto.getTraceRequired()));
        entity.setRemark(dto.getRemark());
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String statusCode) {
        MesMaterial entity = mustExist(id);
        MaterialStatus target = EnumUtils.parse(MaterialStatus.values(), MaterialStatus::getCode, statusCode, "物料状态");
        entity.setStatus(target);
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mustExist(id);
        boolean referenced = bomItemMapper.selectCount(
                new LambdaQueryWrapper<MesBomItem>().eq(MesBomItem::getMaterialId, id)) > 0;
        if (referenced) {
            throw new BusinessException("物料已被 BOM 明细引用，不能删除");
        }
        this.removeById(id);
    }

    private MesMaterial mustExist(Long id) {
        MesMaterial entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("物料不存在: id=" + id);
        }
        return entity;
    }

    private void checkCodeUnique(String materialCode, Long excludeId) {
        List<MesMaterial> exist = this.list(new LambdaQueryWrapper<MesMaterial>()
                .eq(MesMaterial::getMaterialCode, materialCode)
                .ne(excludeId != null, MesMaterial::getId, excludeId));
        if (!exist.isEmpty()) {
            throw new BusinessException("物料编码已存在: " + materialCode);
        }
    }
}
