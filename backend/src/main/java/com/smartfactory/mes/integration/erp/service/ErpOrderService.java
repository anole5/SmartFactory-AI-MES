package com.smartfactory.mes.integration.erp.service;

import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.integration.erp.dto.ErpOrderCreateRequest;
import com.smartfactory.mes.integration.erp.dto.ErpOrderQueryDTO;
import com.smartfactory.mes.integration.erp.dto.ErpOrderVO;

/**
 * ERP 外部订单服务（模拟外部 ERP 系统对接）
 */
public interface ErpOrderService {

    /** 分页查询 */
    PageResult<ErpOrderVO> page(ErpOrderQueryDTO query);

    /** 详情 */
    ErpOrderVO getDetail(Long id);

    /** 模拟下单（外部 ERP 推单）：PENDING */
    Long create(ErpOrderCreateRequest request);

    /** 一键转工单：PENDING → SYNCED（CAS 防重复转单，工单创建同事务） */
    void toWorkOrder(Long id);

    /**
     * 工单完工回传：SYNCED → DONE（按外部订单号 CAS，幂等静默）
     * <p>由报工完工钩子调用，异常由调用方兜底。</p>
     */
    void markDoneByExternalOrderNo(String externalOrderNo);
}
