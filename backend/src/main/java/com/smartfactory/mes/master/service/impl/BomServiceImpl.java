package com.smartfactory.mes.master.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.EnumUtils;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.master.dto.BomItemVO;
import com.smartfactory.mes.master.dto.BomQueryDTO;
import com.smartfactory.mes.master.dto.BomSaveDTO;
import com.smartfactory.mes.master.dto.BomVO;
import com.smartfactory.mes.master.entity.MesBom;
import com.smartfactory.mes.master.entity.MesBomItem;
import com.smartfactory.mes.master.entity.MesMaterial;
import com.smartfactory.mes.master.entity.MesProduct;
import com.smartfactory.mes.master.enums.BomStatus;
import com.smartfactory.mes.master.enums.ProductStatus;
import com.smartfactory.mes.master.mapper.BomItemMapper;
import com.smartfactory.mes.master.mapper.BomMapper;
import com.smartfactory.mes.master.mapper.MaterialMapper;
import com.smartfactory.mes.master.mapper.ProductMapper;
import com.smartfactory.mes.master.service.BomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BOM Service 实现：整单事务（头 + 明细一起成功或一起回滚）是核心考点
 */
@Service
public class BomServiceImpl extends ServiceImpl<BomMapper, MesBom> implements BomService {

    /** BOM 编号格式：BOM + 时间戳（演示用；正式单号生成器第 2 周做） */
    private static final DateTimeFormatter BOM_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final BomItemMapper bomItemMapper;
    private final ProductMapper productMapper;
    private final MaterialMapper materialMapper;

    public BomServiceImpl(BomItemMapper bomItemMapper, ProductMapper productMapper, MaterialMapper materialMapper) {
        this.bomItemMapper = bomItemMapper;
        this.productMapper = productMapper;
        this.materialMapper = materialMapper;
    }

    @Override
    public PageResult<BomVO> page(BomQueryDTO query) {
        LambdaQueryWrapper<MesBom> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getKeyword()), MesBom::getBomNo, query.getKeyword())
                .eq(query.getProductId() != null, MesBom::getProductId, query.getProductId())
                .eq(query.getStatus() != null, MesBom::getStatus, query.getStatus())
                .orderByDesc(MesBom::getId);
        Page<MesBom> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        // 列表带产品编码/名称，方便前端直接展示
        Map<Long, MesProduct> products = loadProducts(page.getRecords());
        return PageResult.of(page.convert(bom -> {
            BomVO vo = BomVO.of(bom);
            fillProduct(vo, products.get(bom.getProductId()));
            return vo;
        }));
    }

    @Override
    public BomVO getDetail(Long id) {
        MesBom bom = mustExist(id);
        BomVO vo = BomVO.of(bom);
        fillProduct(vo, productMapper.selectById(bom.getProductId()));
        List<MesBomItem> items = bomItemMapper.selectList(new LambdaQueryWrapper<MesBomItem>()
                .eq(MesBomItem::getBomId, id)
                .orderByAsc(MesBomItem::getLineNo));
        vo.setItems(items.stream().map(BomItemVO::of).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional
    public Long create(BomSaveDTO dto) {
        MesBom bom = new MesBom();
        applyHeader(bom, dto);
        bom.setBomNo("BOM" + BOM_NO_FORMATTER.format(LocalDateTime.now()));
        bom.setStatus(BomStatus.DRAFT);
        this.save(bom);
        saveItems(bom.getId(), dto.getItems());
        return bom.getId();
    }

    @Override
    @Transactional
    public void update(Long id, BomSaveDTO dto) {
        MesBom bom = mustExist(id);
        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new BusinessException("仅草稿状态的 BOM 可以编辑，当前状态: " + bom.getStatus().getLabel());
        }
        applyHeader(bom, dto);
        this.updateById(bom);
        // 明细整单覆盖：先删旧明细（逻辑删除），再按新数组重插，行号按数组顺序重新生成
        bomItemMapper.delete(new LambdaQueryWrapper<MesBomItem>().eq(MesBomItem::getBomId, id));
        saveItems(id, dto.getItems());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String statusCode) {
        MesBom bom = mustExist(id);
        BomStatus target = EnumUtils.parse(BomStatus.values(), BomStatus::getCode, statusCode, "BOM 状态");
        // 状态机：DRAFT -> ACTIVE -> OBSOLETE；同值设置幂等，回退/跳级一律拒绝
        boolean sameStatus = target == bom.getStatus();
        boolean draftToActive = bom.getStatus() == BomStatus.DRAFT && target == BomStatus.ACTIVE;
        boolean activeToObsolete = bom.getStatus() == BomStatus.ACTIVE && target == BomStatus.OBSOLETE;
        if (!sameStatus && !draftToActive && !activeToObsolete) {
            throw new BusinessException("非法的状态流转: " + bom.getStatus().getCode() + " -> " + target.getCode());
        }
        if (target == BomStatus.ACTIVE) {
            // 激活前再校验产品仍为启用（创建后产品可能被停用）
            MesProduct product = productMapper.selectById(bom.getProductId());
            if (product == null || product.getStatus() != ProductStatus.ENABLED) {
                throw new BusinessException("产品未启用，不能激活 BOM");
            }
            // TODO 第 2 周版本升级：激活新版本时自动作废同产品旧版本
        }
        bom.setStatus(target);
        this.updateById(bom);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        MesBom bom = mustExist(id);
        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new BusinessException("仅草稿状态的 BOM 可以删除，当前状态: " + bom.getStatus().getLabel());
        }
        this.removeById(id);
        // 明细随头一起逻辑删除（MP 逻辑删除不会级联）
        bomItemMapper.delete(new LambdaQueryWrapper<MesBomItem>().eq(MesBomItem::getBomId, id));
    }

    private MesBom mustExist(Long id) {
        MesBom bom = this.getById(id);
        if (bom == null) {
            throw new BusinessException("BOM 不存在: id=" + id);
        }
        return bom;
    }

    /** 头字段回填 + 产品存在性/启用校验 */
    private void applyHeader(MesBom bom, BomSaveDTO dto) {
        MesProduct product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在: id=" + dto.getProductId());
        }
        if (product.getStatus() != ProductStatus.ENABLED) {
            throw new BusinessException("产品未启用，不能维护 BOM: " + product.getProductCode());
        }
        bom.setProductId(dto.getProductId());
        bom.setVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "V1");
        bom.setEffectiveDate(dto.getEffectiveDate());
        bom.setRemark(dto.getRemark());
    }

    /** 保存明细：物料存在性/重复校验 + 快照字段回填 */
    private void saveItems(Long bomId, List<BomSaveDTO.BomItemDTO> items) {
        Set<Long> materialIds = new HashSet<>();
        int lineNo = 1;
        for (BomSaveDTO.BomItemDTO item : items) {
            if (!materialIds.add(item.getMaterialId())) {
                throw new BusinessException("BOM 明细物料重复: id=" + item.getMaterialId());
            }
            MesMaterial material = materialMapper.selectById(item.getMaterialId());
            if (material == null) {
                throw new BusinessException("物料不存在: id=" + item.getMaterialId());
            }
            MesBomItem entity = new MesBomItem();
            entity.setBomId(bomId);
            entity.setLineNo(lineNo++);
            entity.setMaterialId(item.getMaterialId());
            // 快照字段服务端回填：物料主数据后续改名不影响历史 BOM
            entity.setMaterialCodeSnapshot(material.getMaterialCode());
            entity.setMaterialNameSnapshot(material.getMaterialName());
            entity.setUnitSnapshot(material.getUnit());
            entity.setRequiredQty(item.getRequiredQty());
            entity.setLossRate(item.getLossRate() == null ? BigDecimal.ZERO : item.getLossRate());
            entity.setRemark(item.getRemark());
            bomItemMapper.insert(entity);
        }
    }

    private Map<Long, MesProduct> loadProducts(List<MesBom> boms) {
        Set<Long> productIds = boms.stream().map(MesBom::getProductId).collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(MesProduct::getId, p -> p));
    }

    private void fillProduct(BomVO vo, MesProduct product) {
        if (product != null) {
            vo.setProductCode(product.getProductCode());
            vo.setProductName(product.getProductName());
        }
    }
}
