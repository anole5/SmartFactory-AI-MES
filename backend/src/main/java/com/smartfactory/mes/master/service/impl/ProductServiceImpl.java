package com.smartfactory.mes.master.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.EnumUtils;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.master.dto.ProductQueryDTO;
import com.smartfactory.mes.master.dto.ProductSaveDTO;
import com.smartfactory.mes.master.dto.ProductVO;
import com.smartfactory.mes.master.entity.MesBom;
import com.smartfactory.mes.master.entity.MesProduct;
import com.smartfactory.mes.master.entity.MesRoute;
import com.smartfactory.mes.master.enums.BomStatus;
import com.smartfactory.mes.master.enums.ProductStatus;
import com.smartfactory.mes.master.enums.RouteStatus;
import com.smartfactory.mes.master.mapper.BomMapper;
import com.smartfactory.mes.master.mapper.ProductMapper;
import com.smartfactory.mes.master.mapper.RouteMapper;
import com.smartfactory.mes.master.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 产品 Service 实现
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, MesProduct> implements ProductService {

    private final BomMapper bomMapper;
    private final RouteMapper routeMapper;

    public ProductServiceImpl(BomMapper bomMapper, RouteMapper routeMapper) {
        this.bomMapper = bomMapper;
        this.routeMapper = routeMapper;
    }

    @Override
    public PageResult<ProductVO> page(ProductQueryDTO query) {
        LambdaQueryWrapper<MesProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MesProduct::getProductCode, query.getKeyword())
                    .or().like(MesProduct::getProductName, query.getKeyword()));
        }
        wrapper.eq(query.getStatus() != null, MesProduct::getStatus, query.getStatus())
                .orderByDesc(MesProduct::getId);
        Page<MesProduct> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.convert(ProductVO::of));
    }

    @Override
    public ProductVO getDetail(Long id) {
        return ProductVO.of(mustExist(id));
    }

    @Override
    @Transactional
    public Long create(ProductSaveDTO dto) {
        checkCodeUnique(dto.getProductCode(), null);
        MesProduct entity = new MesProduct();
        entity.setProductCode(dto.getProductCode());
        entity.setProductName(dto.getProductName());
        entity.setProductType(dto.getProductType());
        entity.setSpecification(dto.getSpecification());
        entity.setUnit(dto.getUnit());
        // 新建产品默认停用，启用后才能维护 BOM / 工艺路线
        entity.setStatus(ProductStatus.DISABLED);
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, ProductSaveDTO dto) {
        MesProduct entity = mustExist(id);
        checkCodeUnique(dto.getProductCode(), id);
        entity.setProductCode(dto.getProductCode());
        entity.setProductName(dto.getProductName());
        entity.setProductType(dto.getProductType());
        entity.setSpecification(dto.getSpecification());
        entity.setUnit(dto.getUnit());
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String statusCode) {
        MesProduct entity = mustExist(id);
        ProductStatus target = EnumUtils.parse(ProductStatus.values(), ProductStatus::getCode, statusCode, "产品状态");
        if (target == ProductStatus.DISABLED && entity.getStatus() == ProductStatus.ENABLED) {
            // 停用前检查：存在生效中的 BOM 或工艺路线时禁止停用
            boolean hasActiveBom = bomMapper.selectCount(
                    new LambdaQueryWrapper<MesBom>()
                            .eq(MesBom::getProductId, id)
                            .eq(MesBom::getStatus, BomStatus.ACTIVE)) > 0;
            boolean hasActiveRoute = routeMapper.selectCount(
                    new LambdaQueryWrapper<MesRoute>()
                            .eq(MesRoute::getProductId, id)
                            .eq(MesRoute::getStatus, RouteStatus.ACTIVE)) > 0;
            if (hasActiveBom || hasActiveRoute) {
                throw new BusinessException("产品存在生效中的 BOM 或工艺路线，不能停用");
            }
        }
        entity.setStatus(target);
        this.updateById(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mustExist(id);
        boolean bomReferenced = bomMapper.selectCount(
                new LambdaQueryWrapper<MesBom>().eq(MesBom::getProductId, id)) > 0;
        boolean routeReferenced = routeMapper.selectCount(
                new LambdaQueryWrapper<MesRoute>().eq(MesRoute::getProductId, id)) > 0;
        if (bomReferenced || routeReferenced) {
            throw new BusinessException("产品已被 BOM 或工艺路线引用，不能删除");
        }
        // MyBatis-Plus 逻辑删除：自动把 deleted 置 1
        this.removeById(id);
    }

    private MesProduct mustExist(Long id) {
        MesProduct entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("产品不存在: id=" + id);
        }
        return entity;
    }

    private void checkCodeUnique(String productCode, Long excludeId) {
        LambdaQueryWrapper<MesProduct> wrapper = new LambdaQueryWrapper<MesProduct>()
                .eq(MesProduct::getProductCode, productCode)
                .ne(excludeId != null, MesProduct::getId, excludeId);
        List<MesProduct> exist = this.list(wrapper);
        if (!exist.isEmpty()) {
            throw new BusinessException("产品编码已存在: " + productCode);
        }
    }
}
