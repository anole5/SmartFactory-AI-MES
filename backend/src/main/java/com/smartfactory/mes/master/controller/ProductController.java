package com.smartfactory.mes.master.controller;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.PageResult;
import com.smartfactory.mes.common.api.StatusUpdateDTO;
import com.smartfactory.mes.master.dto.ProductQueryDTO;
import com.smartfactory.mes.master.dto.ProductSaveDTO;
import com.smartfactory.mes.master.dto.ProductVO;
import com.smartfactory.mes.master.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产品管理接口（Controller 只做参数接收与返回，业务规则全在 Service）
 */
@RestController
@RequestMapping("/master/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 产品分页列表 */
    @GetMapping("/page")
    public ApiResult<PageResult<ProductVO>> page(@Valid ProductQueryDTO query) {
        return ApiResult.success(productService.page(query));
    }

    /** 产品详情 */
    @GetMapping("/{id}")
    public ApiResult<ProductVO> get(@PathVariable Long id) {
        return ApiResult.success(productService.getDetail(id));
    }

    /** 创建产品 */
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody ProductSaveDTO dto) {
        return ApiResult.success(productService.create(dto));
    }

    /** 更新产品 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveDTO dto) {
        productService.update(id, dto);
        return ApiResult.success();
    }

    /** 启停用产品 */
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        productService.changeStatus(id, dto.getStatus());
        return ApiResult.success();
    }

    /** 删除产品（逻辑删除） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResult.success();
    }
}
