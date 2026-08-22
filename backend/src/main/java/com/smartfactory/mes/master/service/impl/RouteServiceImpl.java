package com.smartfactory.mes.master.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.EnumUtils;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.exception.BusinessException;
import com.smartfactory.mes.master.dto.RouteQueryDTO;
import com.smartfactory.mes.master.dto.RouteSaveDTO;
import com.smartfactory.mes.master.dto.RouteStepVO;
import com.smartfactory.mes.master.dto.RouteVO;
import com.smartfactory.mes.master.entity.MesProcess;
import com.smartfactory.mes.master.entity.MesProduct;
import com.smartfactory.mes.master.entity.MesRoute;
import com.smartfactory.mes.master.entity.MesRouteStep;
import com.smartfactory.mes.master.entity.MesWorkstation;
import com.smartfactory.mes.master.enums.ProductStatus;
import com.smartfactory.mes.master.enums.RouteStatus;
import com.smartfactory.mes.master.mapper.ProcessMapper;
import com.smartfactory.mes.master.mapper.ProductMapper;
import com.smartfactory.mes.master.mapper.RouteMapper;
import com.smartfactory.mes.master.mapper.RouteStepMapper;
import com.smartfactory.mes.master.mapper.WorkstationMapper;
import com.smartfactory.mes.master.service.RouteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工艺路线 Service 实现：结构与 BOM 同构（头 + 步骤整单事务）
 */
@Service
public class RouteServiceImpl extends ServiceImpl<RouteMapper, MesRoute> implements RouteService {

    /** 工艺路线编号格式：RT + 时间戳（演示用；正式单号生成器第 2 周做） */
    private static final DateTimeFormatter ROUTE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RouteStepMapper routeStepMapper;
    private final ProductMapper productMapper;
    private final ProcessMapper processMapper;
    private final WorkstationMapper workstationMapper;

    public RouteServiceImpl(RouteStepMapper routeStepMapper, ProductMapper productMapper,
                            ProcessMapper processMapper, WorkstationMapper workstationMapper) {
        this.routeStepMapper = routeStepMapper;
        this.productMapper = productMapper;
        this.processMapper = processMapper;
        this.workstationMapper = workstationMapper;
    }

    @Override
    public PageResult<RouteVO> page(RouteQueryDTO query) {
        LambdaQueryWrapper<MesRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getKeyword()), MesRoute::getRouteNo, query.getKeyword())
                .eq(query.getProductId() != null, MesRoute::getProductId, query.getProductId())
                .eq(query.getStatus() != null, MesRoute::getStatus, query.getStatus())
                .orderByDesc(MesRoute::getId);
        Page<MesRoute> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, MesProduct> products = loadProducts(page.getRecords());
        return PageResult.of(page.convert(route -> {
            RouteVO vo = RouteVO.of(route);
            fillProduct(vo, products.get(route.getProductId()));
            return vo;
        }));
    }

    @Override
    public RouteVO getDetail(Long id) {
        MesRoute route = mustExist(id);
        RouteVO vo = RouteVO.of(route);
        fillProduct(vo, productMapper.selectById(route.getProductId()));
        List<MesRouteStep> steps = routeStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getRouteId, id)
                .orderByAsc(MesRouteStep::getSequenceNo));
        // 批量查步骤引用的工位，补工位编码/名称
        Map<Long, MesWorkstation> workstations = loadWorkstations(steps);
        vo.setSteps(steps.stream().map(step -> {
            RouteStepVO stepVO = RouteStepVO.of(step);
            stepVO.fillWorkstation(workstations.get(step.getWorkstationId()));
            return stepVO;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional
    public Long create(RouteSaveDTO dto) {
        MesRoute route = new MesRoute();
        applyHeader(route, dto);
        route.setRouteNo("RT" + ROUTE_NO_FORMATTER.format(LocalDateTime.now()));
        route.setStatus(RouteStatus.DRAFT);
        this.save(route);
        saveSteps(route.getId(), dto.getSteps());
        return route.getId();
    }

    @Override
    @Transactional
    public void update(Long id, RouteSaveDTO dto) {
        MesRoute route = mustExist(id);
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new BusinessException("仅草稿状态的工艺路线可以编辑，当前状态: " + route.getStatus().getLabel());
        }
        applyHeader(route, dto);
        this.updateById(route);
        // 步骤整单覆盖：先删旧步骤，再按新数组重插，顺序号按数组顺序重新生成
        routeStepMapper.delete(new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getRouteId, id));
        saveSteps(id, dto.getSteps());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String statusCode) {
        MesRoute route = mustExist(id);
        RouteStatus target = EnumUtils.parse(RouteStatus.values(), RouteStatus::getCode, statusCode, "工艺路线状态");
        // 状态机与 BOM 一致：DRAFT -> ACTIVE -> OBSOLETE；同值幂等，回退/跳级拒绝
        boolean sameStatus = target == route.getStatus();
        boolean draftToActive = route.getStatus() == RouteStatus.DRAFT && target == RouteStatus.ACTIVE;
        boolean activeToObsolete = route.getStatus() == RouteStatus.ACTIVE && target == RouteStatus.OBSOLETE;
        if (!sameStatus && !draftToActive && !activeToObsolete) {
            throw new BusinessException("非法的状态流转: " + route.getStatus().getCode() + " -> " + target.getCode());
        }
        if (target == RouteStatus.ACTIVE) {
            MesProduct product = productMapper.selectById(route.getProductId());
            if (product == null || product.getStatus() != ProductStatus.ENABLED) {
                throw new BusinessException("产品未启用，不能激活工艺路线");
            }
            // TODO 第 2 周版本升级：激活新版本时自动作废同产品旧版本
        }
        route.setStatus(target);
        this.updateById(route);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        MesRoute route = mustExist(id);
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new BusinessException("仅草稿状态的工艺路线可以删除，当前状态: " + route.getStatus().getLabel());
        }
        this.removeById(id);
        routeStepMapper.delete(new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getRouteId, id));
    }

    private MesRoute mustExist(Long id) {
        MesRoute route = this.getById(id);
        if (route == null) {
            throw new BusinessException("工艺路线不存在: id=" + id);
        }
        return route;
    }

    /** 头字段回填 + 产品存在性/启用校验 */
    private void applyHeader(MesRoute route, RouteSaveDTO dto) {
        MesProduct product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在: id=" + dto.getProductId());
        }
        if (product.getStatus() != ProductStatus.ENABLED) {
            throw new BusinessException("产品未启用，不能维护工艺路线: " + product.getProductCode());
        }
        route.setProductId(dto.getProductId());
        route.setVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "V1");
        route.setRemark(dto.getRemark());
    }

    /** 保存步骤：工序存在性/工位存在性校验 + 快照字段回填 */
    private void saveSteps(Long routeId, List<RouteSaveDTO.RouteStepDTO> steps) {
        int sequenceNo = 1;
        for (RouteSaveDTO.RouteStepDTO step : steps) {
            MesProcess process = processMapper.selectById(step.getProcessId());
            if (process == null) {
                throw new BusinessException("工序不存在: id=" + step.getProcessId());
            }
            if (step.getWorkstationId() != null && workstationMapper.selectById(step.getWorkstationId()) == null) {
                throw new BusinessException("工位不存在: id=" + step.getWorkstationId());
            }
            MesRouteStep entity = new MesRouteStep();
            entity.setRouteId(routeId);
            // 顺序号按数组顺序生成，前端"上移/下移"即交换数组位置后重新保存
            entity.setSequenceNo(sequenceNo++);
            entity.setProcessId(step.getProcessId());
            // 快照字段服务端回填：工序主数据后续改名不影响历史工艺路线
            entity.setProcessCodeSnapshot(process.getProcessCode());
            entity.setProcessNameSnapshot(process.getProcessName());
            entity.setWorkstationId(step.getWorkstationId());
            // 未指定是否质检时继承工序主数据的设置
            entity.setNeedInspection(step.getNeedInspection() != null
                    ? step.getNeedInspection() : process.getNeedInspection());
            entity.setStandardMinutes(process.getStandardMinutes());
            entity.setRemark(step.getRemark());
            routeStepMapper.insert(entity);
        }
    }

    private Map<Long, MesProduct> loadProducts(List<MesRoute> routes) {
        Set<Long> productIds = routes.stream().map(MesRoute::getProductId).collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(MesProduct::getId, p -> p));
    }

    private Map<Long, MesWorkstation> loadWorkstations(List<MesRouteStep> steps) {
        Set<Long> workstationIds = steps.stream().map(MesRouteStep::getWorkstationId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (workstationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return workstationMapper.selectBatchIds(workstationIds).stream()
                .collect(Collectors.toMap(MesWorkstation::getId, w -> w));
    }

    private void fillProduct(RouteVO vo, MesProduct product) {
        if (product != null) {
            vo.setProductCode(product.getProductCode());
            vo.setProductName(product.getProductName());
        }
    }
}
