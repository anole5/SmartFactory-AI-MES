package com.smartfactory.mes.master.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.master.dto.ProductQueryDTO;
import com.smartfactory.mes.master.dto.ProductSaveDTO;
import com.smartfactory.mes.master.dto.ProductVO;
import com.smartfactory.mes.master.entity.MesProduct;

/**
 * 产品 Service：核心业务规则放这里（编码唯一、启用才能维护 BOM/路线、引用禁删）
 */
public interface ProductService extends IService<MesProduct> {

    PageResult<ProductVO> page(ProductQueryDTO query);

    ProductVO getDetail(Long id);

    Long create(ProductSaveDTO dto);

    void update(Long id, ProductSaveDTO dto);

    void changeStatus(Long id, String statusCode);

    void delete(Long id);
}
