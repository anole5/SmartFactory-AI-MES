package com.smartfactory.mes.auth.controller;

import com.smartfactory.mes.auth.CurrentUserContext;
import com.smartfactory.mes.auth.dto.LoginDTO;
import com.smartfactory.mes.auth.dto.LoginVO;
import com.smartfactory.mes.auth.dto.MenuNodeVO;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.service.AuthService;
import com.smartfactory.mes.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口（第 2 周：真实用户表 + JWT，替换第 1 周固定 token）
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录（白名单接口，不经过鉴权拦截器） */
    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResult.success(authService.login(dto));
    }

    /** 启用用户列表（派工弹窗选择操作员用） */
    @GetMapping("/users")
    public ApiResult<List<SysUser>> users() {
        return ApiResult.success(authService.listEnabledUsers());
    }

    /**
     * 当前用户菜单树（第 5 周前端动态路由数据源）。
     * 登录即可取本人菜单（经鉴权拦截器保护），接口本身不加 @RequirePermission；
     * 树内容由 service 按角色过滤，后端权限注解仍是接口级真防线。
     */
    @GetMapping("/menus")
    public ApiResult<List<MenuNodeVO>> menus() {
        return ApiResult.success(authService.listMenus(CurrentUserContext.get().getId()));
    }
}
