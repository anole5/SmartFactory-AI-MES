package com.smartfactory.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.production.dto.SnQueryDTO;
import com.smartfactory.mes.production.dto.SnVO;
import com.smartfactory.mes.production.entity.MesProductSn;
import com.smartfactory.mes.production.entity.MesWorkOrder;
import com.smartfactory.mes.production.entity.MesWorkReport;
import com.smartfactory.mes.production.mapper.MesProductSnMapper;
import com.smartfactory.mes.production.mapper.MesWorkOrderMapper;
import com.smartfactory.mes.production.mapper.MesWorkReportMapper;
import com.smartfactory.mes.production.service.ProductSnService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 整机 SN 服务实现（分页 + 批量回填，避免 N+1）
 */
@Service
public class ProductSnServiceImpl extends ServiceImpl<MesProductSnMapper, MesProductSn>
        implements ProductSnService {

    private final MesWorkOrderMapper workOrderMapper;
    private final MesWorkReportMapper workReportMapper;

    public ProductSnServiceImpl(MesWorkOrderMapper workOrderMapper,
                                MesWorkReportMapper workReportMapper) {
        this.workOrderMapper = workOrderMapper;
        this.workReportMapper = workReportMapper;
    }

    @Override
    public PageResult<SnVO> page(SnQueryDTO query) {
        LambdaQueryWrapper<MesProductSn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkOrderId() != null, MesProductSn::getWorkOrderId, query.getWorkOrderId())
                .like(StringUtils.hasText(query.getKeyword()), MesProductSn::getSn, query.getKeyword())
                .orderByDesc(MesProductSn::getId);
        Page<MesProductSn> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<MesProductSn> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        // 批量回填：工单号 + 出生报工单号（selectBatchIds 空集合会生成非法 SQL，必查非空）
        Set<Long> workOrderIds = records.stream().map(MesProductSn::getWorkOrderId).collect(Collectors.toSet());
        Map<Long, MesWorkOrder> workOrders = workOrderMapper.selectBatchIds(workOrderIds).stream()
                .collect(Collectors.toMap(MesWorkOrder::getId, Function.identity()));
        Set<Long> reportIds = records.stream().map(MesProductSn::getReportId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesWorkReport> reports = reportIds.isEmpty() ? Collections.emptyMap()
                : workReportMapper.selectBatchIds(reportIds).stream()
                .collect(Collectors.toMap(MesWorkReport::getId, Function.identity()));
        List<SnVO> vos = records.stream().map(r -> {
            SnVO vo = SnVO.of(r);
            MesWorkOrder wo = workOrders.get(r.getWorkOrderId());
            if (wo != null) {
                vo.setWorkOrderNo(wo.getWorkOrderNo());
            }
            MesWorkReport report = reports.get(r.getReportId());
            if (report != null) {
                vo.setReportNo(report.getReportNo());
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
