package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.common.sequence.OrderNoGenerator;
import com.smartfactory.mes.master.entity.MesMaterial;
import com.smartfactory.mes.master.service.MaterialService;
import com.smartfactory.mes.production.dto.MaterialBatchQueryDTO;
import com.smartfactory.mes.production.dto.MaterialBatchSaveDTO;
import com.smartfactory.mes.production.dto.MaterialBatchVO;
import com.smartfactory.mes.production.entity.MesMaterialBatch;
import com.smartfactory.mes.production.mapper.MesMaterialBatchMapper;
import com.smartfactory.mes.production.service.MaterialBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * 物料批次 Service 实现（第 6 周）：创建时校验物料存在并快照编码/名称，
 * 批次号由 OrderNoGenerator 生成（MB 前缀，同事务取号）
 */
@Service
public class MaterialBatchServiceImpl extends ServiceImpl<MesMaterialBatchMapper, MesMaterialBatch>
        implements MaterialBatchService {

    private final MaterialService materialService;
    private final OrderNoGenerator orderNoGenerator;

    public MaterialBatchServiceImpl(MaterialService materialService, OrderNoGenerator orderNoGenerator) {
        this.materialService = materialService;
        this.orderNoGenerator = orderNoGenerator;
    }

    @Override
    public PageResult<MaterialBatchVO> page(MaterialBatchQueryDTO query) {
        LambdaQueryWrapper<MesMaterialBatch> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MesMaterialBatch::getBatchNo, query.getKeyword())
                    .or().like(MesMaterialBatch::getSupplier, query.getKeyword()));
        }
        wrapper.eq(query.getMaterialId() != null, MesMaterialBatch::getMaterialId, query.getMaterialId())
                .orderByDesc(MesMaterialBatch::getId);
        Page<MesMaterialBatch> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(MaterialBatchVO::of));
    }

    @Override
    @Transactional
    public Long create(MaterialBatchSaveDTO dto) {
        MesMaterial material = materialService.getById(dto.getMaterialId());
        if (material == null) {
            throw new BusinessException("物料不存在: id=" + dto.getMaterialId());
        }
        MesMaterialBatch entity = new MesMaterialBatch();
        entity.setBatchNo(orderNoGenerator.next("MB"));
        entity.setMaterialId(material.getId());
        entity.setMaterialCodeSnapshot(material.getMaterialCode());
        entity.setMaterialNameSnapshot(material.getMaterialName());
        entity.setBatchQty(dto.getBatchQty());
        entity.setUsedQty(0);
        entity.setInDate(dto.getInDate() != null ? dto.getInDate() : LocalDate.now());
        entity.setSupplier(dto.getSupplier());
        entity.setRemark(dto.getRemark());
        this.save(entity);
        return entity.getId();
    }
}
